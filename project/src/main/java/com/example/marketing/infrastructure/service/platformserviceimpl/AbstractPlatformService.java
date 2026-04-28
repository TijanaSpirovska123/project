package com.example.marketing.infrastructure.service.platformserviceimpl;

import com.example.marketing.exception.BusinessException;
import com.example.marketing.exception.StatusErrorResponse;
import com.example.marketing.infrastructure.cache.PlatformRawDataCache;
import com.example.marketing.infrastructure.entity.BasePlatformEntity;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.oauth.service.TokenService;
import com.example.marketing.infrastructure.strategy.PlatformClientRegistry;
import com.example.marketing.infrastructure.service.PlatformService;
import com.example.marketing.infrastructure.strategy.PlatformStrategy;
import com.example.marketing.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public abstract class AbstractPlatformService<
        E extends BasePlatformEntity,
        D,
        S extends PlatformStrategy<D>
        > implements PlatformService<E,D> {

    protected final JpaRepository<E, Long> repository;
    protected final Function<E, D> toDto;
    protected final PlatformClientRegistry clients;
    protected final TokenService tokens;
    protected final Function<Provider, S> strategies;
    protected final PlatformRawDataCache rawDataCache;

    // ===== required hooks for sync =====
    protected abstract E newEntity();
    protected abstract String dtoExternalId(D dto);
    protected abstract void applyDtoToNewEntity(D dto, E entity, UserEntity user, Provider provider, String adAccountId);
    protected abstract List<E> findExisting(UserEntity user, String platform, String adAccountId, Collection<String> externalIds);

    // ===== required hooks for delete (implemented by each concrete service) =====
    protected abstract void deleteAllByUserAndPlatformAndAdAccount(Long userId, String platform, String adAccountId);
    protected abstract void deleteAllByUserAndPlatform(Long userId, String platform);

    @Transactional
    public void wipeAdAccount(UserEntity user, Provider platform, String adAccountId) {
        deleteAllByUserAndPlatformAndAdAccount(user.getId(), platform.name(), adAccountId);
    }

    @Transactional
    public void wipePlatform(UserEntity user, Provider platform) {
        deleteAllByUserAndPlatform(user.getId(), platform.name());
    }

    // ===== optional hooks (defaults: no-op) =====
    protected void applyDtoToExistingEntity(D dto, E existing) { /* no-op */ }
    protected Map<String, Object> dtoRawData(D dto) { return null; }

    protected void applyRawDataToEntity(E entity, Map<String, Object> raw) { }
    protected abstract void cacheRawData(String platform, String adAccountId, String externalId, Map<String, Object> raw);

    private Provider providerOf(E entity) {
        return Provider.from(entity.getPlatform());
    }

    @Override
    public void createOnPlatform(E entity) {
        Provider provider = providerOf(entity);
        UserEntity user = entity.getUser();
        String token = tokens.getAccessToken(user, provider);

        var client = clients.of(provider);
        var strategy = strategies.apply(provider);
        D dto = toDto.apply(entity);

        String path = strategy.createPath(entity.getAdAccountId());
        MultiValueMap<String, String> form = strategy.toCreateForm(dto);

        ResponseEntity<Map> response = client.postForm(path, form, token);
        Map body = response.getBody();

        if (body == null) {
            throw BusinessException.of(StatusErrorResponse.FACEBOOK_API_ERROR,
                    "Platform returned no response when creating " + entity.getClass().getSimpleName());
        }
        if (body.get("id") == null) {
            throw BusinessException.of(StatusErrorResponse.FACEBOOK_API_ERROR,
                    "Platform did not return an id for the created " + entity.getClass().getSimpleName());
        }
        entity.setExternalId(body.get("id").toString());
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Override
    public void updateOnPlatform(E entity) {
        if (entity.getExternalId() == null) return;

        Provider provider = providerOf(entity);
        String token = tokens.getAccessToken(entity.getUser(), provider);

        var client = clients.of(provider);
        var strategy = strategies.apply(provider);
        D dto = toDto.apply(entity);

        String path = strategy.updatePath(entity.getExternalId());
        MultiValueMap<String, String> form = strategy.toUpdateForm(dto, false);

        client.postForm(path, form, token);
    }

    @Override
    public void deleteOnPlatform(E entity) {
        if (entity.getExternalId() == null) return;

        Provider provider = providerOf(entity);
        String token = tokens.getAccessToken(entity.getUser(), provider);

        var client = clients.of(provider);
        var strategy = strategies.apply(provider);
        D dto = toDto.apply(entity);

        String path = strategy.updatePath(entity.getExternalId());
        MultiValueMap<String, String> form = strategy.toUpdateForm(dto, true);

        Map<String, String> payload = new HashMap<>();
        form.forEach((k, v) -> { if (!v.isEmpty()) payload.put(k, v.get(0)); });

        client.delete(path, payload, token);
    }

    @Override
    public List<D> listFromPlatform(UserEntity user, Provider platform, String adAccountId) {
        String token = tokens.getAccessToken(user, platform);
        var client = clients.of(platform);
        var strategy = strategies.apply(platform);

        String after = null;
        Map<String, String> baseQ = strategy.baseListQuery();
        List<D> out = new ArrayList<>();

        do {
            Map<String, String> q = new HashMap<>(baseQ);
            if (after != null) q.put("after", after);

            ResponseEntity<Map> resp = client.get(strategy.listPath(adAccountId), q, token);
            Map body = resp.getBody();
            if (body == null) break;

            @SuppressWarnings("unchecked")
            var data = (List<Map<String, Object>>) body.get("data");
            if (data != null) {
                for (var row : data) {
                    out.add(strategy.mapGetRow(row, user.getId(), adAccountId));
                }
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> paging = (Map<String, Object>) body.get("paging");
            @SuppressWarnings("unchecked")
            Map<String, Object> cursors = paging != null ? (Map<String, Object>) paging.get("cursors") : null;
            after = cursors != null ? (String) cursors.get("after") : null;

        } while (after != null);

        return out;
    }

    @Transactional
    @Override
    public SyncResult syncFromPlatform(UserEntity user, Provider platform, String adAccountId) {
        List<D> dtos = listFromPlatform(user, platform, adAccountId);

        List<String> externalIds = dtos.stream()
                .map(this::dtoExternalId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        if (externalIds.isEmpty()) {
            return new SyncResult(dtos.size(), 0, dtos.size(), 0);
        }

        String platformStr = platform.name();

        List<E> existing = findExisting(user, platformStr, adAccountId, externalIds);
        Map<String, E> existingById = existing.stream()
                .filter(e -> e.getExternalId() != null)
                .collect(Collectors.toMap(BasePlatformEntity::getExternalId, Function.identity(), (a, b) -> a));

        int skipped = 0;
        int inserted = 0;
        int updated = 0;

        List<E> toInsert = new ArrayList<>();
        List<E> toUpdate = new ArrayList<>();

        for (D dto : dtos) {
            String ext = dtoExternalId(dto);
            if (ext == null || ext.isBlank()) { skipped++; continue; }
            ext = ext.trim();

            Map<String, Object> raw = dtoRawData(dto);
            if (raw != null && !raw.isEmpty()) {
                cacheRawData(platformStr, adAccountId, ext, raw);
            }

            E already = existingById.get(ext);
            if (already != null) {
                applyDtoToExistingEntity(dto, already);
                if (raw != null && !raw.isEmpty()) applyRawDataToEntity(already, raw);
                already.setUpdatedAt(LocalDateTime.now());
                toUpdate.add(already);
                updated++;
                continue;
            }

            E e = newEntity();
            applyDtoToNewEntity(dto, e, user, platform, adAccountId);

            e.setUser(user);
            e.setPlatform(platformStr);
            e.setAdAccountId(adAccountId);
            e.setExternalId(ext);
            if (raw != null && !raw.isEmpty()) applyRawDataToEntity(e, raw);

            if (e.getCreatedAt() == null) e.setCreatedAt(LocalDateTime.now());
            e.setUpdatedAt(LocalDateTime.now());

            toInsert.add(e);
            inserted++;
        }

        if (!toInsert.isEmpty()) repository.saveAll(toInsert);
        if (!toUpdate.isEmpty()) repository.saveAll(toUpdate);

        return new SyncResult(dtos.size(), inserted, skipped, updated);
    }

    public record SyncResult(int fetched, int inserted, int skipped, int updated) {}
}

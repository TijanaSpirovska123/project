package com.example.marketing.adcreative.service;

import com.example.marketing.adcreative.dto.AdAssetDto;
import com.example.marketing.adcreative.dto.CreativeDto;
import com.example.marketing.adcreative.entity.AdAssetEntity;
import com.example.marketing.adcreative.entity.CreativeAssetEntity;
import com.example.marketing.adcreative.entity.CreativeEntity;
import com.example.marketing.adcreative.mapper.CreativeMapper;
import com.example.marketing.adcreative.repository.AdAssetRepository;
import com.example.marketing.adcreative.repository.CreativeAssetRepository;
import com.example.marketing.adcreative.repository.CreativeRepository;
import com.example.marketing.adcreative.strategy.CreativeStrategyRegistry;
import com.example.marketing.adset.dto.AdSetDto;
import com.example.marketing.asset.entity.StoredAssetEntity;
import com.example.marketing.asset.entity.StoredAssetVariantEntity;
import com.example.marketing.asset.repository.StoredAssetRepository;
import com.example.marketing.asset.repository.StoredAssetVariantRepository;
import com.example.marketing.asset.service.MetaVideoUploadService;
import com.example.marketing.asset.storage.ObjectStorageClient;
import com.example.marketing.exception.BusinessException;
import com.example.marketing.exception.StatusErrorResponse;
import com.example.marketing.infrastructure.service.platformserviceimpl.AbstractPlatformService;
import com.example.marketing.infrastructure.strategy.PlatformClientRegistry;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.oauth.service.TokenService;
import com.example.marketing.page.entity.PageEntity;
import com.example.marketing.page.entity.PagePostEntity;
import com.example.marketing.page.repository.PagePostRepository;
import com.example.marketing.page.repository.PageRepository;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdCreativeService {

    private final CreativeRepository creativeRepository;
    private final CreativeMapper creativeMapper;

    private final AdAssetRepository adAssetRepository;
    private final CreativeAssetRepository creativeAssetRepository;

    private final UserRepository userRepository;
    private final PageRepository pageRepository;
    private final PagePostRepository pagePostRepository;

    private final PlatformClientRegistry clients;
    private final TokenService tokens;
    private final CreativeStrategyRegistry strategyRegistry;

    private final StoredAssetRepository storedAssetRepository;
    private final StoredAssetVariantRepository storedAssetVariantRepository;
    private final ObjectStorageClient storage;
    private final MetaVideoUploadService metaVideoUploadService;

    @Value("${facebook.login.marketing.ad-account-id:}")
    private String defaultAdAccountId;

    public AdCreativeService(
            CreativeRepository creativeRepository,
            CreativeMapper creativeMapper,
            AdAssetRepository adAssetRepository,
            CreativeAssetRepository creativeAssetRepository,
            UserRepository userRepository,
            PageRepository pageRepository,
            PagePostRepository pagePostRepository,
            PlatformClientRegistry clients,
            TokenService tokens,
            CreativeStrategyRegistry strategyRegistry,
            StoredAssetRepository storedAssetRepository,
            StoredAssetVariantRepository storedAssetVariantRepository,
            ObjectStorageClient storage,
            MetaVideoUploadService metaVideoUploadService
    ) {
        this.creativeRepository = creativeRepository;
        this.creativeMapper = creativeMapper;
        this.adAssetRepository = adAssetRepository;
        this.creativeAssetRepository = creativeAssetRepository;
        this.userRepository = userRepository;
        this.pageRepository = pageRepository;
        this.pagePostRepository = pagePostRepository;
        this.clients = clients;
        this.tokens = tokens;
        this.strategyRegistry = strategyRegistry;
        this.storedAssetRepository = storedAssetRepository;
        this.storedAssetVariantRepository = storedAssetVariantRepository;
        this.storage = storage;
        this.metaVideoUploadService = metaVideoUploadService;
    }

    /* =========================
       MODE 1: CREATE CREATIVE FROM POST (platform creative)
       ========================= */
    @Transactional
    public CreativeDto createCreative(
            String postId,
            String name,
            Long userId,
            String pageName,
            String linkUrl,
            Provider platform
    ) {
        if (platform == null) throw new IllegalArgumentException("platform is required");
        if (postId == null || postId.isBlank()) throw new IllegalArgumentException("postId is required");
        if (pageName == null || pageName.isBlank()) throw new IllegalArgumentException("pageName is required");

        if (platform == Provider.META && !postId.contains("_")) {
            throw new IllegalArgumentException("For META, postId must be in format {pageId}_{postId}");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        PagePostEntity pagePost = pagePostRepository
                .findByPostIdAndPageNameAndUserId(postId, pageName, userId)
                .orElseThrow(() -> BusinessException.notFound(
                        "Page post not found with id '" + postId + "' for page '" + pageName + "'"));

        creativeRepository.findByPagePost(pagePost).ifPresent(x -> {
            throw BusinessException.duplicate("A creative already exists for post " + postId);
        });

        String safeName = (name != null && !name.isBlank())
                ? name
                : "Creative_" + (postId.contains("_") ? postId.substring(postId.indexOf('_') + 1) : postId);

        CreativeDto dto = new CreativeDto();
        dto.setName(safeName);
        dto.setObjectStoryId(postId);
        dto.setObjectUrl(linkUrl);
        dto.setUserId(userId);
        dto.setAdAccountId(resolveAdAccountId(null));
        dto.setPlatform(platform.name());

        String creativePlatformId = createCreativeOnPlatform(user, dto, platform);

        CreativeEntity e = new CreativeEntity();
        e.setName(safeName);
        e.setPlatform(platform.name());
        e.setUser(user);
        e.setAdAccountId(dto.getAdAccountId());
        e.setExternalId(creativePlatformId);
        e.setPagePost(pagePost);
        e.setLinkUrl(linkUrl);

        e = creativeRepository.save(e);
        return creativeMapper.convertToBaseDto(e);
    }

    @Transactional
    public CreativeDto updateCreative(String externalId, String name, String linkUrl) {
        if (externalId == null || externalId.isBlank()) throw BusinessException.badRequest("creativeId is required");

        CreativeEntity e = creativeRepository.findByExternalId(externalId)
                .orElseThrow(() -> BusinessException.notFound("Creative not found with externalId: " + externalId));

        boolean changed = false;
        if (name != null && !name.isBlank() && !Objects.equals(name, e.getName())) { e.setName(name); changed = true; }
        if (linkUrl != null && !Objects.equals(linkUrl, e.getLinkUrl())) { e.setLinkUrl(linkUrl); changed = true; }

        if (!changed) return creativeMapper.convertToBaseDto(e);

        e = creativeRepository.save(e);
        return creativeMapper.convertToBaseDto(e);
    }

    /* =========================
       MODE 2: CREATE PLATFORM CREATIVE FROM META ASSET HASH
       (this is your controller /creative/from-asset)
       ========================= */
    @Transactional
    public CreativeDto createCreativeFromAsset(Long userId, CreativeDto in, String adAccountId, Provider platform) {
        if (platform == null) throw new IllegalArgumentException("platform is required");
        if (in == null) throw new IllegalArgumentException("body is required");


        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        String scopedAdAccountId = resolveAdAccountId(adAccountId);

        // Ensure the hash exists in our DB for this user/provider/account (prevents random hash injection)
        String hashLookupAccountId = scopedAdAccountId.startsWith("act_")
                ? scopedAdAccountId.substring(4)
                : scopedAdAccountId;
        adAssetRepository.findByUserAndProviderAndAdAccountIdAndHash(user, platform, hashLookupAccountId, in.getImageHash())
                .orElseThrow(() -> BusinessException.notFound("Ad asset not found for hash '" + in.getImageHash()
                        + "' in account " + scopedAdAccountId + ". Please upload the image first."));

        String safeName = (in.getName() != null && !in.getName().isBlank())
                ? in.getName()
                : "Creative_" + UUID.randomUUID();

        CreativeDto dto = new CreativeDto();
        dto.setName(safeName);
        dto.setPageId(in.getPageId());
        dto.setObjectUrl(in.getObjectUrl());
        dto.setImageHash(in.getImageHash());
        dto.setMessage(in.getMessage());
        dto.setHeadline(in.getHeadline());
        dto.setUrlTags(in.getUrlTags());
        dto.setCallToAction(in.getCallToAction() != null && !in.getCallToAction().isBlank()
                ? in.getCallToAction() : "LEARN_MORE");
        dto.setUserId(userId);
        dto.setAdAccountId(scopedAdAccountId);
        dto.setPlatform(platform.name());

        String platformCreativeId = createCreativeOnPlatform(user, dto, platform);

        CreativeEntity e = new CreativeEntity();
        e.setName(safeName);
        e.setPlatform(platform.name());
        e.setUser(user);
        e.setAdAccountId(scopedAdAccountId);
        e.setExternalId(platformCreativeId);
        e.setPageId(dto.getPageId());
        e.setLinkUrl(dto.getObjectUrl());

        e = creativeRepository.save(e);
        return creativeMapper.convertToBaseDto(e);
    }

    /* =========================
       MODE 2b: CREATE INTERNAL CREATIVE FROM STORED ASSET
       (your controller /creative/from-stored-asset)
       ========================= */
    @Transactional
    public CreativeDto createCreativeFromStoredAsset(
            Long userId,
            CreativeDto in,
            String adAccountId,
            Provider platform,
            Long storedAssetId,
            String variantKey
    ) {
        if (platform == null) throw new IllegalArgumentException("platform is required");
        if (in == null) throw new IllegalArgumentException("body is required");
        if (storedAssetId == null) throw new IllegalArgumentException("storedAssetId is required");
        if (variantKey == null || variantKey.isBlank()) throw new IllegalArgumentException("variantKey is required");

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        StoredAssetEntity asset = storedAssetRepository.findById(storedAssetId)
                .orElseThrow(() -> BusinessException.notFound("Stored asset not found with id: " + storedAssetId));

        if (!Objects.equals(asset.getUser().getId(), userId))
            throw BusinessException.forbidden("You do not own stored asset with id " + storedAssetId);

        boolean isVideo = "VIDEO".equalsIgnoreCase(asset.getAssetType());
        boolean isImage = "IMAGE".equalsIgnoreCase(asset.getAssetType());

        if (!isImage && !isVideo)
            throw BusinessException.badRequest("Only IMAGE or VIDEO assets are supported. Asset " + storedAssetId
                    + " is of type " + asset.getAssetType());

        if ("ARCHIVED".equalsIgnoreCase(asset.getStatus()))
            throw BusinessException.badRequest("Asset " + storedAssetId + " is archived and cannot be used");

        String vKey = variantKey.trim().toUpperCase();
        StoredAssetVariantEntity variant = storedAssetVariantRepository
                .findByAssetIdAndVariantKey(storedAssetId, vKey)
                .orElseThrow(() -> BusinessException.notFound(
                        "Variant '" + vKey + "' not found for asset " + storedAssetId));

        String safeName = (in.getName() != null && !in.getName().isBlank())
                ? in.getName()
                : "Creative_" + UUID.randomUUID();

        String scopedAdAccountId = resolveAdAccountId(adAccountId);

        CreativeEntity e = new CreativeEntity();
        e.setName(safeName);
        e.setPlatform(platform.name());
        e.setUser(user);
        e.setAdAccountId(scopedAdAccountId);
        e.setLinkUrl(in.getObjectUrl());
        e.setPageId(in.getPageId());
        e = creativeRepository.save(e);

        CreativeAssetEntity ca = CreativeAssetEntity.builder()
                .creative(e)
                .asset(asset)
                .variant(variant)
                .role(isVideo ? CreativeAssetEntity.Role.VIDEO : CreativeAssetEntity.Role.PRIMARY_IMAGE)
                .sortOrder(0)
                .build();
        creativeAssetRepository.save(ca);

        // For VIDEO assets, upload to Meta and create video creative on platform
        if (platform == Provider.META && isVideo) {
            String metaVideoId;
            if (variant.getMetaVideoId() != null && !variant.getMetaVideoId().isBlank()) {
                // Already uploaded to Meta — reuse existing video_id
                metaVideoId = variant.getMetaVideoId();
                log.info("Reusing existing Meta video_id {} for asset {} variant {}",
                        metaVideoId, storedAssetId, vKey);
            } else {
                log.info("Uploading video asset {} variant {} to Meta for account {}",
                        storedAssetId, vKey, scopedAdAccountId);
                metaVideoId = metaVideoUploadService.uploadVideoToMeta(variant, userId, scopedAdAccountId);
            }

            CreativeDto videoCreativeDto = new CreativeDto();
            videoCreativeDto.setName(safeName);
            videoCreativeDto.setPageId(in.getPageId());
            videoCreativeDto.setVideoId(metaVideoId);
            videoCreativeDto.setObjectUrl(in.getObjectUrl());
            videoCreativeDto.setUserId(userId);
            videoCreativeDto.setAdAccountId(scopedAdAccountId);
            videoCreativeDto.setPlatform(platform.name());

            String platformCreativeId = createCreativeOnPlatform(user, videoCreativeDto, platform);
            e.setExternalId(platformCreativeId);
            e = creativeRepository.save(e);
        }

        return creativeMapper.convertToBaseDto(e);
    }
    
    @Transactional
    public AdAssetDto uploadAdImageFromStoredAsset(
            Long userId,
            String adAccountId,
            Long storedAssetId,
            String variantKey,
            String pageName
    ) {
        if (storedAssetId == null) throw new IllegalArgumentException("storedAssetId is required");
        if (variantKey == null || variantKey.isBlank()) throw new IllegalArgumentException("variantKey is required");

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        StoredAssetEntity storedAsset = storedAssetRepository.findById(storedAssetId)
                .orElseThrow(() -> BusinessException.notFound("Stored asset not found with id: " + storedAssetId));

        if (!Objects.equals(storedAsset.getUser().getId(), userId))
            throw BusinessException.forbidden("You do not own stored asset with id " + storedAssetId);

        if (!"IMAGE".equalsIgnoreCase(storedAsset.getAssetType()))
            throw BusinessException.badRequest("Only IMAGE assets can be uploaded to Meta. Asset " + storedAssetId
                    + " is of type " + storedAsset.getAssetType());

        String vKey = variantKey.trim().toUpperCase();
        StoredAssetVariantEntity variant = storedAssetVariantRepository
                .findByAssetIdAndVariantKey(storedAssetId, vKey)
                .orElseThrow(() -> BusinessException.notFound(
                        "Variant '" + vKey + "' not found for asset " + storedAssetId));

        String scopedAdAccountId = resolveAdAccountId(adAccountId);

        // ✅ fastest dedup: if we already uploaded this exact stored variant to this account, return it
        Optional<AdAssetEntity> existing = adAssetRepository
                .findByUserAndProviderAndAdAccountIdAndStoredVariant(user, Provider.META, scopedAdAccountId, variant);

        if (existing.isPresent()) return toAdAssetDto(existing.get());

        PageEntity page = (pageName != null && !pageName.isBlank())
                ? pageRepository.findByNameAndUser(sanitizeInput(pageName), user)
                .orElseThrow(() -> BusinessException.notFound("Page '" + pageName + "' not found for current user"))
                : null;

        byte[] bytes = storage.getBytes(variant.getBucket(), variant.getObjectKey());
        if (bytes == null || bytes.length < 100) throw new IllegalArgumentException("Variant bytes invalid/empty: " + vKey);

        MetaUploadResult meta = uploadBytesToMeta(user, scopedAdAccountId, bytes, storedAsset.getOriginalFilename());

        AdAssetEntity saved = adAssetRepository
                .findByUserAndProviderAndAdAccountIdAndHash(user, Provider.META, scopedAdAccountId, meta.hash)
                .orElseGet(() -> AdAssetEntity.builder()
                        .user(user)
                        .provider(Provider.META)
                        .adAccountId(scopedAdAccountId)
                        .assetType(AdAssetEntity.AssetType.IMAGE)
                        .hash(meta.hash)
                        .url(meta.url != null ? meta.url : "")
                        .mimeType(storedAsset.getMimeType())
                        .sizeBytes(storedAsset.getSizeBytes())
                        .originalFilename(storedAsset.getOriginalFilename())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());

        saved.setStoredAsset(storedAsset);
        saved.setStoredVariant(variant);
        saved.setPage(page);
        if (meta.url != null) saved.setUrl(meta.url);
        saved.setUpdatedAt(LocalDateTime.now());
        saved = adAssetRepository.save(saved);

        return toAdAssetDto(saved);
    }

    /* =========================
       LIST + DETAILS (META)
       ========================= */

    public List<String> getAllAdCreativeIds(Long userId, String adAccountId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        String act = normalizeAct(resolveAdAccountId(adAccountId));

        var token = tokens.getAccessToken(user, Provider.META);
        var client = clients.of(Provider.META);

        String path = act + "/adcreatives";

        List<String> all = new ArrayList<>();
        String after = null;

        do {
            Map<String, String> q = new HashMap<>();
            q.put("fields", "id");
            if (after != null) q.put("after", after);

            ResponseEntity<Map> resp = client.get(path, q, token);

            Map body = resp.getBody();
            if (body == null || !resp.getStatusCode().is2xxSuccessful()) break;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");

            if (data != null) {
                for (Map<String, Object> m : data) {
                    Object id = m.get("id");
                    if (id != null) all.add(id.toString());
                }
            }

            Map paging = (Map) body.get("paging");
            Map cursors = paging == null ? null : (Map) paging.get("cursors");
            after = (cursors == null) ? null : (String) cursors.get("after");

        } while (after != null);

        return all;
    }

    public Map<String, Object> getAdCreativeDetails(Long userId, String adAccountId, String creativeId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        var token = tokens.getAccessToken(user, Provider.META);
        var client = clients.of(Provider.META);

        String fields = "id,name,object_story_id,object_story_spec,object_type,image_hash,image_url,video_id,body,title,status,thumbnail_url,url_tags";
        ResponseEntity<Map> resp = client.get(creativeId, Map.of("fields", fields), token);

        Map body = resp.getBody();
        if (body == null || !resp.getStatusCode().is2xxSuccessful()) {
            throw BusinessException.of(StatusErrorResponse.FACEBOOK_API_ERROR,
                    "Failed to fetch details for creative " + creativeId + " from Meta");
        }
        return body;
    }

    public List<Map<String, Object>> getAllAdCreativesWithDetails(Long userId, String adAccountId) {
        List<String> ids = getAllAdCreativeIds(userId, adAccountId);
        List<Map<String, Object>> out = new ArrayList<>(ids.size());

        for (String id : ids) {
            try {
                out.add(getAdCreativeDetails(userId, adAccountId, id));
            } catch (Exception e) {
                out.add(Map.of("id", id, "error", e.getMessage()));
            }
        }
        return out;
    }

    public List<AdAssetDto> listAdAssets(Long userId, String adAccountId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        List<AdAssetEntity> list = adAssetRepository.findAllByUserOrderByCreatedAtDesc(user);

        if (adAccountId != null && !adAccountId.isBlank()) {
            String id = adAccountId;
            list = list.stream().filter(x -> id.equals(x.getAdAccountId())).collect(Collectors.toList());
        }

        return list.stream().map(e -> {
            AdAssetDto d = new AdAssetDto();
            d.setId(e.getId());
            d.setImageHash(e.getHash());
            d.setUrl(e.getUrl());
            return d;
        }).collect(Collectors.toList());
    }

    /* =========================
       INTERNAL HELPERS
       ========================= */

    private String createCreativeOnPlatform(UserEntity user, CreativeDto dto, Provider provider) {
        var token = tokens.getAccessToken(user, provider);
        var client = clients.of(provider);
        var strategy = strategyRegistry.of(provider);

        MultiValueMap<String, String> form = strategy.toCreateForm(dto);
        ResponseEntity<Map> resp = client.postForm(strategy.createPath(dto.getAdAccountId()), form, token);

        Map body = resp.getBody();
        if (body == null || !resp.getStatusCode().is2xxSuccessful()) {
            throw BusinessException.of(StatusErrorResponse.FACEBOOK_API_ERROR,
                    provider + " creative creation failed: platform returned no valid response");
        }

        Object id = body.get("id");
        if (id == null) throw BusinessException.of(StatusErrorResponse.FACEBOOK_API_ERROR,
                provider + " creative creation failed: platform did not return a creative id");
        return id.toString();
    }

    @Transactional
    public AbstractPlatformService.SyncResult syncAdCreatives(Long userId, Provider platform, String accountId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        // Step 1: Fetch all creative IDs from platform
        String act = normalizeAct(resolveAdAccountId(accountId));
        String token = tokens.getAccessToken(user, platform);
        var client = clients.of(platform);

        List<String> ids = new ArrayList<>();
        String after = null;
        do {
            Map<String, String> q = new HashMap<>();
            q.put("fields", "id");
            if (after != null) q.put("after", after);

            ResponseEntity<Map> resp = client.get(act + "/adcreatives", q, token);
            Map body = resp.getBody();
            if (body == null) break;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
            if (data != null) {
                data.stream()
                        .map(row -> (String) row.get("id"))
                        .filter(id -> id != null && !id.isBlank())
                        .forEach(ids::add);
            }

            Map<?, ?> paging = (Map<?, ?>) body.get("paging");
            Map<?, ?> cursors = paging != null ? (Map<?, ?>) paging.get("cursors") : null;
            after = cursors != null ? (String) cursors.get("after") : null;
        } while (after != null);

        if (ids.isEmpty()) {
            return new AbstractPlatformService.SyncResult(0, 0, 0, 0);
        }

        // Step 2: Fetch details for each ID
        String fields = "id,name,object_story_id,object_story_spec,object_type,image_hash,image_url,body,title,status,thumbnail_url,url_tags";
        List<Map<String, Object>> rawCreatives = new ArrayList<>();
        for (String id : ids) {
            try {
                ResponseEntity<Map> resp = client.get(id, Map.of("fields", fields), token);
                Map<?, ?> body = resp.getBody();
                if (body != null && resp.getStatusCode().is2xxSuccessful()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> creative = (Map<String, Object>) body;
                    rawCreatives.add(creative);
                }
            } catch (Exception ex) {
                log.warn("Skipping creative {} due to error: {}", id, ex.getMessage());
            }
        }

        // Step 3: Load existing from DB
        List<String> externalIds = rawCreatives.stream()
                .map(row -> (String) row.get("id"))
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        List<CreativeEntity> existingList = creativeRepository
                .findByUserAndPlatformAndAdAccountIdAndExternalIdIn(user, platform.name(), accountId, externalIds);
        Map<String, CreativeEntity> existingById = existingList.stream()
                .collect(Collectors.toMap(CreativeEntity::getExternalId, e -> e, (a, b) -> a));

        // Step 4: Upsert
        List<CreativeEntity> toInsert = new ArrayList<>();
        List<CreativeEntity> toUpdate = new ArrayList<>();
        int inserted = 0, updated = 0, skipped = 0;

        for (Map<String, Object> row : rawCreatives) {
            String extId = (String) row.get("id");
            if (extId == null || extId.isBlank()) { skipped++; continue; }

            String name = (String) row.getOrDefault("name", null);

            CreativeEntity existing = existingById.get(extId);
            if (existing != null) {
                if (name != null) existing.setName(name);
                existing.setUpdatedAt(LocalDateTime.now());
                toUpdate.add(existing);
                updated++;
            } else {
                CreativeEntity e = new CreativeEntity();
                e.setExternalId(extId);
                e.setName(name);
                e.setUser(user);
                e.setPlatform(platform.name());
                e.setAdAccountId(accountId);
                e.setCreatedAt(LocalDateTime.now());
                e.setUpdatedAt(LocalDateTime.now());
                toInsert.add(e);
                inserted++;
            }
        }

        if (!toInsert.isEmpty()) creativeRepository.saveAll(toInsert);
        if (!toUpdate.isEmpty()) creativeRepository.saveAll(toUpdate);

        return new AbstractPlatformService.SyncResult(rawCreatives.size(), inserted, skipped, updated);
    }

    public List<CreativeDto> getAllAdCreatives(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        return creativeRepository.findByUser(user).stream()
                .map(creativeMapper::convertToBaseDto)
                .collect(Collectors.toList());
    }


    private record MetaUploadResult(String hash, String url) {}

    private MetaUploadResult uploadBytesToMeta(UserEntity user, String adAccountId, byte[] bytes, String filename) {
        var token = tokens.getAccessToken(user, Provider.META);
        var client = clients.of(Provider.META);

        String act = normalizeAct(adAccountId);
        String path = act + "/adimages";

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();

        String accessToken = (token == null) ? "" : token.trim();
        if (accessToken.regionMatches(true, 0, "bearer ", 0, 7)) {
            accessToken = accessToken.substring(7).trim();
        }
        form.add("access_token", accessToken);

        String safe = safeFilename(filename);
        ByteArrayResource fileResource = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return (safe != null && !safe.isBlank()) ? safe : "upload.jpg"; }
        };
        // keep your known working key
        form.add("filename", fileResource);

        ResponseEntity<Map> resp;
        try {
            resp = client.postMultipart(path, form, token);
        } catch (HttpStatusCodeException ex) {
            String metaBody = ex.getResponseBodyAsString();
            log.error("Meta upload failed HTTP {}: {}", ex.getStatusCode(), metaBody);
            throw BusinessException.of(StatusErrorResponse.FACEBOOK_API_ERROR,
                    "Failed to upload image to Meta (HTTP " + ex.getStatusCode() + "): " + metaBody);
        }

        Map body = resp.getBody();
        if (body == null || !resp.getStatusCode().is2xxSuccessful()) {
            throw BusinessException.of(StatusErrorResponse.FACEBOOK_API_ERROR,
                    "Meta image upload returned an invalid response");
        }

        Object imagesObj = body.get("images");
        if (!(imagesObj instanceof Map)) throw BusinessException.of(StatusErrorResponse.FACEBOOK_API_ERROR,
                "Meta did not return image data after upload");

        @SuppressWarnings("unchecked")
        Map<String, Object> images = (Map<String, Object>) imagesObj;
        if (images.isEmpty()) throw BusinessException.of(StatusErrorResponse.FACEBOOK_API_ERROR,
                "Meta returned an empty images object after upload");

        Object firstValue = images.values().iterator().next();
        if (!(firstValue instanceof Map)) throw BusinessException.of(StatusErrorResponse.FACEBOOK_API_ERROR,
                "Meta returned an unexpected image data structure after upload");

        @SuppressWarnings("unchecked")
        Map<String, Object> imageData = (Map<String, Object>) firstValue;

        String hash = imageData.get("hash") != null ? imageData.get("hash").toString() : null;
        String url = imageData.get("url") != null ? imageData.get("url").toString() : null;

        if (hash == null || hash.isBlank()) throw BusinessException.of(StatusErrorResponse.FACEBOOK_API_ERROR,
                "Meta did not return an image hash after upload");
        return new MetaUploadResult(hash, url);
    }

    private AdAssetDto toAdAssetDto(AdAssetEntity e) {
        AdAssetDto out = new AdAssetDto();
        out.setId(e.getId());
        out.setImageHash(e.getHash());
        out.setUrl(e.getUrl());
        return out;
    }

    private String resolveAdAccountId(String adAccountId) {
        String id = (adAccountId != null && !adAccountId.isBlank()) ? adAccountId : defaultAdAccountId;
        if (id == null || id.isBlank()) throw new IllegalStateException("No adAccountId provided and no default configured");
        return id;
    }

    private String normalizeAct(String adAccountId) {
        return adAccountId.startsWith("act_") ? adAccountId : "act_" + adAccountId;
    }

    private static void requireNonEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("No file uploaded");
    }

    private String sanitizeInput(String input) {
        if (input == null) return null;
        return input.replaceAll("[^a-zA-Z0-9_\\-.\\s]", "");
    }

    private static String safeFilename(String original) {
        if (original == null || original.isBlank()) return "upload";
        return original.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
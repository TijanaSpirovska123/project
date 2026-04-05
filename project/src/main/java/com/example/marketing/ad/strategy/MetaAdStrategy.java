package com.example.marketing.ad.strategy;

import com.example.marketing.ad.dto.AdDto;
import com.example.marketing.infrastructure.util.FormBuilder;
import com.example.marketing.infrastructure.util.Provider;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.Map;

import static com.example.marketing.infrastructure.util.MetaForm.normalizeAct;
import static com.example.marketing.infrastructure.util.StrategyMappers.s;

@Component
public class MetaAdStrategy implements AdStrategy {

    private static final String FIELDS = "id,name,status,adset_id,creative";

    @Override public Provider platform() { return Provider.META; }

    @Override public String createPath(String accountId) { return normalizeAct(accountId) + "/ads"; }
    @Override public String listPath(String accountId) { return normalizeAct(accountId) + "/ads"; }
    @Override public String updatePath(String platformId) { return platformId; }

    @Override
    public Map<String, String> baseListQuery() {
        return Map.of("fields", FIELDS);
    }

    @Override
    public MultiValueMap<String, String> toCreateForm(AdDto dto) {
        if (dto.getAdSetExternalId() == null || dto.getAdSetExternalId().isBlank()) {
            throw new IllegalArgumentException("adSetExternalId is required for Meta ad create");
        }
        if (dto.getCreativeId() == null || dto.getCreativeId().isBlank()) {
            throw new IllegalArgumentException("creativeId is required for Meta ad create");
        }
        return FormBuilder.create()
                .add("name", dto.getName())
                .add("status", dto.getStatus() != null ? dto.getStatus() : "PAUSED")
                .add("adset_id", dto.getAdSetExternalId())
                .add("creative", "{\"creative_id\":\"" + dto.getCreativeId() + "\"}")
                .build();
    }

    @Override
    public MultiValueMap<String, String> toUpdateForm(AdDto dto, boolean isDelete) {
        if (isDelete) return FormBuilder.create().add("status", "DELETED").build();
        return FormBuilder.create()
                .add("name", dto.getName())
                .add("status", dto.getStatus())
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public AdDto mapGetRow(Map<String, Object> row, Long userId, String adAccountId) {
        AdDto dto = new AdDto();

        dto.setExternalId(s(row, "id"));
        dto.setName(s(row, "name"));
        dto.setStatus(s(row, "status"));
        dto.setAdSetExternalId(s(row, "adset_id"));

        // Meta returns creative as nested object: {id: "123456789"}
        Object creativeObj = row.get("creative");
        if (creativeObj instanceof Map) {
            dto.setCreativeId(s((Map<String, Object>) creativeObj, "id"));
        }

        dto.setAdAccountId(adAccountId);
        dto.setUserId(userId);
        dto.setPlatform(Provider.META.name());
        dto.setRawData(new HashMap<>(row));
        return dto;
    }
}

package com.example.marketing.adset.strategy;

import com.example.marketing.adset.dto.AdSetDto;
import com.example.marketing.infrastructure.util.FormBuilder;
import com.example.marketing.infrastructure.util.Provider;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.Map;

import static com.example.marketing.infrastructure.util.MetaForm.normalizeAct;
import static com.example.marketing.infrastructure.util.StrategyMappers.s;

@Component
public class MetaAdSetStrategy implements AdSetStrategy {

    private static final String FIELDS = "id,name,status,campaign_id";

    @Override public Provider platform() { return Provider.META; }

    @Override public String createPath(String accountId) { return normalizeAct(accountId) + "/adsets"; }
    @Override public String listPath(String accountId) { return normalizeAct(accountId) + "/adsets"; }
    @Override public String updatePath(String platformId) { return platformId; }

    @Override public Map<String, String> baseListQuery() { return Map.of("fields", FIELDS); }

    @Override
    public MultiValueMap<String, String> toCreateForm(AdSetDto dto) {
        return FormBuilder.create()
                .add("name", dto.getName())
                .add("status", dto.getStatus())
                .add("campaign_id", dto.getCampaignExternalId())
                .build();
    }

    @Override
    public MultiValueMap<String, String> toUpdateForm(AdSetDto dto, boolean isDelete) {
        if (isDelete) {
            return FormBuilder.create().add("status", "DELETED").build();
        }
        return FormBuilder.create()
                .add("name", dto.getName())
                .add("status", dto.getStatus())
                .build();
    }

    @Override
    public AdSetDto mapGetRow(Map<String, Object> row, Long userId, String adAccountId) {
        AdSetDto dto = new AdSetDto();

        dto.setExternalId(s(row, "id"));
        dto.setName(s(row, "name"));
        dto.setStatus(s(row, "status"));
        dto.setCampaignExternalId(s(row, "campaign_id"));
        dto.setAdAccountId(adAccountId);
        dto.setUserId(userId);
        dto.setPlatform("META");
        dto.setRawData(new HashMap<>(row));
        return dto;
    }
}

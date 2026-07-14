package com.example.marketing.campaign.strategy;

import com.example.marketing.campaign.dto.CampaignDto;
import com.example.marketing.infrastructure.util.FormBuilder;
import com.example.marketing.infrastructure.util.Provider;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.Map;

import static com.example.marketing.infrastructure.util.MetaForm.normalizeAct;
import static com.example.marketing.infrastructure.util.StrategyMappers.s;

@Component
public class MetaCampaignStrategy implements CampaignStrategy {

    private static final String FIELDS = "id,name,status";

    @Override public Provider platform() { return Provider.META; }

    @Override public String createPath(String accountId) { return normalizeAct(accountId) + "/campaigns"; }
    @Override public String listPath(String accountId) { return normalizeAct(accountId) + "/campaigns"; }
    @Override public String updatePath(String platformId) { return platformId; }

    @Override public Map<String, String> baseListQuery() { return Map.of("fields", FIELDS); }

    @Override
    public MultiValueMap<String, String> toCreateForm(CampaignDto dto) {
        return FormBuilder.create()
                .add("name", dto.getName())
                .add("status", dto.getStatus())
                .build();
    }

    @Override
    public MultiValueMap<String, String> toUpdateForm(CampaignDto dto, boolean isDelete) {
        if (isDelete) {
            return FormBuilder.create().add("status", "DELETED").build();
        }
        return FormBuilder.create()
                .add("name", dto.getName())
                .add("status", dto.getStatus())
                .build();
    }

    @Override
    public CampaignDto mapGetRow(Map<String, Object> row, Long userId, String adAccountId) {
        CampaignDto dto = new CampaignDto();

        dto.setExternalId(s(row, "id"));
        dto.setName(s(row, "name"));
        dto.setStatus(s(row, "status"));
        dto.setAdAccountId(adAccountId);
        dto.setUserId(userId);
        dto.setPlatform("META");
        dto.setRawData(new HashMap<>(row));
        return dto;
    }
}

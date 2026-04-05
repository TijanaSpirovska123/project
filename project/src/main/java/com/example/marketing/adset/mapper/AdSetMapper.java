package com.example.marketing.adset.mapper;

import com.example.marketing.adset.dto.AdSetDto;
import com.example.marketing.adset.entity.AdSetEntity;
import com.example.marketing.infrastructure.mapper.BaseMapper;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(uses = BaseMapper.class)
public interface AdSetMapper extends BaseMapper<AdSetDto, AdSetEntity> {

    @AfterMapping
    default void fillDerivedFields(AdSetEntity e, @MappingTarget AdSetDto dto) {
        if (e.getCampaign() != null) {
            dto.setCampaignId(e.getCampaign().getId());
            dto.setCampaignExternalId(e.getCampaign().getExternalId());
        } else if (e.getCampaignExternalId() != null) {
            dto.setCampaignExternalId(e.getCampaignExternalId());
        }

        if (e.getUser() != null) {
            dto.setUserId(e.getUser().getId());
        }
    }
}

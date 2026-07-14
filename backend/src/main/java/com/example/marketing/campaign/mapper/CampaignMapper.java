package com.example.marketing.campaign.mapper;

import com.example.marketing.campaign.dto.CampaignDto;
import com.example.marketing.campaign.entity.CampaignEntity;
import com.example.marketing.infrastructure.mapper.BaseMapper;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(uses = BaseMapper.class)
public interface CampaignMapper extends BaseMapper<CampaignDto, CampaignEntity> {

    @AfterMapping
    default void fillDerivedFields(CampaignEntity e, @MappingTarget CampaignDto dto) {
        if (e.getUser() != null) {
            dto.setUserId(e.getUser().getId());
        }
    }
}

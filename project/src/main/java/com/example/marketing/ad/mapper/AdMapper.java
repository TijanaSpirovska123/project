package com.example.marketing.ad.mapper;

import com.example.marketing.ad.dto.AdDto;
import com.example.marketing.ad.entity.AdEntity;
import com.example.marketing.infrastructure.mapper.BaseMapper;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(uses = BaseMapper.class)
public interface AdMapper extends BaseMapper<AdDto, AdEntity> {

    @AfterMapping
    default void fillDerivedFields(AdEntity entity, @MappingTarget AdDto dto) {
        if (entity.getAdSet() != null) {
            dto.setAdSetId(entity.getAdSet().getId());
            dto.setAdSetExternalId(entity.getAdSet().getExternalId());
        } else if (entity.getAdSetExternalId() != null) {
            dto.setAdSetExternalId(entity.getAdSetExternalId());
        }

        if (entity.getUser() != null) {
            dto.setUserId(entity.getUser().getId());
        }
    }
}

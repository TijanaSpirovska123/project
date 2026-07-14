package com.example.marketing.adcreative.mapper;

import com.example.marketing.adcreative.dto.CreativeDto;
import com.example.marketing.adcreative.entity.CreativeEntity;
import com.example.marketing.infrastructure.mapper.BaseMapper;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
@Mapper(uses = BaseMapper.class)
public interface CreativeMapper extends BaseMapper<CreativeDto, CreativeEntity> {

    @AfterMapping
    default void fillDerivedFields(CreativeEntity e, @MappingTarget CreativeDto dto) {
        if (e == null) return;

        // linkUrl -> objectUrl
        dto.setObjectUrl(e.getLinkUrl());

        // MODE 1: pagePost -> objectStoryId
        dto.setObjectStoryId(e.getPagePost() != null ? e.getPagePost().getPostId() : null);

        // MODE 2: pageId
        dto.setPageId(e.getPageId());

        // userId (BasePlatformEntity has user)
        dto.setUserId(e.getUser() != null ? e.getUser().getId() : null);

        dto.setImageHash(e.getImageHash());

        // DO NOT set dto.imageHash here. It's not stored on CreativeEntity.
    }
}
package com.example.marketing.adcreative.mapper;

import com.example.marketing.infrastructure.mapper.BaseMapper;
import com.example.marketing.adcreative.dto.AdAssetDto;
import com.example.marketing.adcreative.entity.AdAssetEntity;
import org.mapstruct.Mapper;

@Mapper(uses= BaseMapper.class)
public interface AdAssetMapper extends BaseMapper<AdAssetDto, AdAssetEntity> {
}

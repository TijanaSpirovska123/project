package com.example.marketing.page.mapper;

import com.example.marketing.page.dto.PageDto;
import com.example.marketing.page.entity.PageEntity;
import com.example.marketing.infrastructure.mapper.BaseMapper;
import org.mapstruct.Mapper;

@Mapper(uses= BaseMapper.class)
public interface PageMapper extends BaseMapper<PageDto, PageEntity> {
}
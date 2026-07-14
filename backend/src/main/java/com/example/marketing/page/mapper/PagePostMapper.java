package com.example.marketing.page.mapper;

import com.example.marketing.page.dto.PagePostDto;
import com.example.marketing.page.entity.PagePostEntity;
import com.example.marketing.infrastructure.mapper.BaseMapper;
import org.mapstruct.Mapper;

@Mapper(uses= BaseMapper.class)
public interface PagePostMapper extends BaseMapper<PagePostDto, PagePostEntity> {
}
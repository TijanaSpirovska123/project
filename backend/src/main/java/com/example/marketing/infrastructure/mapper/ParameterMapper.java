package com.example.marketing.infrastructure.mapper;

import org.springframework.util.MultiValueMap;

@FunctionalInterface
public interface ParameterMapper<T> {
    void map(T entity, MultiValueMap<String, String> params, boolean isDelete);
}
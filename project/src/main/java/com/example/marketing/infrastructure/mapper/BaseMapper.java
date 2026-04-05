package com.example.marketing.infrastructure.mapper;



import java.util.List;

public interface BaseMapper<D , S >{
    D convertToBaseDto(S input);

    S convertToBaseEntity(D input);

    List<D> convertToBaseDto(List<S> input);

    List<S> convertToBaseEntity(List<D> input);
}

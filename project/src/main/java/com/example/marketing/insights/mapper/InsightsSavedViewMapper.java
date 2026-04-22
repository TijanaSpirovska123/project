package com.example.marketing.insights.mapper;

import com.example.marketing.insights.dto.InsightsSavedViewDto;
import com.example.marketing.insights.entity.InsightsSavedViewEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface InsightsSavedViewMapper {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mapping(target = "provider", expression = "java(entity.getProvider() != null ? entity.getProvider().name() : null)")
    @Mapping(target = "viewConfig", qualifiedByName = "stringToJsonNode")
    InsightsSavedViewDto toDto(InsightsSavedViewEntity entity);

    @Mapping(target = "user", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "provider", ignore = true)
    @Mapping(target = "viewConfig", qualifiedByName = "jsonNodeToString")
    InsightsSavedViewEntity toEntity(InsightsSavedViewDto dto);

    @Named("stringToJsonNode")
    default JsonNode stringToJsonNode(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    @Named("jsonNodeToString")
    default String jsonNodeToString(JsonNode node) {
        if (node == null) return null;
        return node.toString();
    }
}

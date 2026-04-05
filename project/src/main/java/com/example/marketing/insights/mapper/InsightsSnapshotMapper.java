package com.example.marketing.insights.mapper;

import com.example.marketing.infrastructure.mapper.BaseMapper;
import com.example.marketing.insights.dto.InsightSnapshotDto;
import com.example.marketing.insights.dto.InsightMetricDto;
import com.example.marketing.insights.entity.InsightMetricEntity;
import com.example.marketing.insights.entity.InsightSnapshotEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = BaseMapper.class)
public interface InsightsSnapshotMapper extends BaseMapper<InsightSnapshotDto, InsightSnapshotEntity> {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    @Mapping(target = "rawData", ignore = true)
    InsightSnapshotDto convertToBaseDto(InsightSnapshotEntity entity);

    @Override
    @Mapping(target = "rawJson", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "breakdownsJson", ignore = true)
    InsightSnapshotEntity convertToBaseEntity(InsightSnapshotDto dto);

   @AfterMapping
    default void fillDerivedFields(InsightSnapshotEntity entity, @MappingTarget InsightSnapshotDto dto) {
        if (entity.getRawJson() == null || entity.getRawJson().isBlank()) {
            return;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(entity.getRawJson());

            // Keep full raw payload on DTO
            dto.setRawData(root);

            // Extract metrics from raw JSON
            dto.setMetrics(extractMetrics(root));
        } catch (Exception e) {
            // Fallback: keep raw string if JSON parsing fails
            dto.setRawData(entity.getRawJson());
            dto.setMetrics(Collections.emptyList());
        }
    }

    default List<InsightMetricDto> extractMetrics(JsonNode root) {
    List<InsightMetricDto> result = new ArrayList<>();

    // FIX: Meta returns flat fields — skip known non-metric structural keys
    Set<String> skip = Set.of("date_start", "date_stop", "data", "paging", "summary");

    root.fields().forEachRemaining(entry -> {
        if (skip.contains(entry.getKey())) return;

        JsonNode valueNode = entry.getValue();

        // Expandable array fields (actions, video_play_actions, etc.)
        if (valueNode.isArray()) {
            for (JsonNode item : valueNode) {
                String actionType = item.path("action_type").asText(null);
                String value      = item.path("value").asText(null);
                if (actionType != null && value != null) {
                    InsightMetricDto dto = new InsightMetricDto();
                    dto.setName(entry.getKey() + "." + actionType);
                    dto.setValueNumber(parseDecimal(value));
                    result.add(dto);
                }
            }
            return;
        }

        // Flat numeric/text fields
        if (valueNode.isNumber() || valueNode.isTextual()) {
            InsightMetricDto dto = new InsightMetricDto();
            dto.setName(entry.getKey());
            if (valueNode.isNumber()) {
                dto.setValueNumber(valueNode.decimalValue());
            } else {
                BigDecimal parsed = parseDecimal(valueNode.asText());
                if (parsed != null) dto.setValueNumber(parsed);
                else dto.setValueText(valueNode.asText());
            }
            result.add(dto);
        }
    });

    return result;
}

default BigDecimal parseDecimal(String value) {
    if (value == null || value.isBlank()) return null;
    try { return new BigDecimal(value.trim()); }
    catch (NumberFormatException e) { return null; }
}

    default InsightMetricDto toMetricDto(InsightMetricEntity entity) {
        InsightMetricDto dto = new InsightMetricDto();
        dto.setName(entity.getName());
        dto.setValueNumber(entity.getValueNumber());
        dto.setValueText(entity.getValueText());
        return dto;
    }
}
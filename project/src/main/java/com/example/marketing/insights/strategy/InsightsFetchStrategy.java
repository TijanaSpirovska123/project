package com.example.marketing.insights.strategy;

import com.example.marketing.insights.dto.InsightSyncRequestDto;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.user.entity.UserEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface InsightsFetchStrategy {
    
    Map<String, Object> fetchForObject(UserEntity user, String objectId, Map<String, String> queryParams);
    
    Map<String, Map<String, Object>> fetchForObjects(UserEntity user, Iterable<String> objectIds, Map<String, String> queryParams);
    
    Map<String, Object> fetchForAccount(UserEntity user, String adAccountId, Map<String, String> queryParams);
    
    List<String> defaultFieldsFor(InsightObjectType objectType, boolean batch);
    
    /**
     * Build query parameters for this provider
     */
    Map<String, String> buildQueryParams(InsightSyncRequestDto request, List<String> fields, int timeIncrement);
    
    /**
     * Determines if a field should be expanded into sub-metrics
     */
    default boolean shouldExpandField(String fieldName) {
        return false;
    }
    
    /**
     * Extracts sub-metrics from a list/array field
     */
    default Map<String, BigDecimal> expandField(String fieldName, List<?> data) {
        return Map.of();
    }
    
    /**
     * Helper method to parse string to BigDecimal
     */
    default BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
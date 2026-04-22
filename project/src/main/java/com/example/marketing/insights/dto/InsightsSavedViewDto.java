package com.example.marketing.insights.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InsightsSavedViewDto {
    private Long id;
    private String name;
    private String description;
    private String provider;
    private JsonNode viewConfig;
    private boolean pinned;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

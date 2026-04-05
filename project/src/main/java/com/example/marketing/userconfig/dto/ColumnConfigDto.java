package com.example.marketing.userconfig.dto;

import lombok.Data;

import java.util.List;

@Data
public class ColumnConfigDto {
    /** Entity type: CAMPAIGN | AD_SET | AD */
    private String entityType;
    /** Ordered list of column keys */
    private List<String> columns;
}

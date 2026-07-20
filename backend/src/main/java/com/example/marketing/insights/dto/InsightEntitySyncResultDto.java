package com.example.marketing.insights.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Per-entity outcome of a batch sync — distinguishes a successful-with-data result from a
 * successful-but-no-delivery result from an outright failure, rather than conflating them. */
@Data
public class InsightEntitySyncResultDto {
    private String objectExternalId;

    /** One of: SYNCED, NO_ACTIVITY, FAILED, SKIPPED. */
    private String status;

    private List<InsightWarningDto> warnings = new ArrayList<>();
}

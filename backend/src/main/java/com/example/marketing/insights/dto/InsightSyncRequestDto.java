package com.example.marketing.insights.dto;

import com.example.marketing.insights.util.FetchMode;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.infrastructure.util.Provider;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class InsightSyncRequestDto {
    private Provider provider;
    private String adAccountId;
    private InsightObjectType objectType;
    private List<String> objectExternalIds;

    // Date range — use either datePreset OR dateStart+dateStop.
    // dateStop is inclusive (matches Meta's own time_range.until semantics): a range of
    // dateStart=2026-06-16, dateStop=2026-07-16 covers data through and including July 16th.
    private LocalDate dateStart;
    private LocalDate dateStop;
    private String datePreset;              // e.g. "last_month", "this_year", "maximum", "last_quarter"

    private FetchMode fetchMode = FetchMode.PER_OBJECT;
    private Integer limit;
    private Integer timeIncrement;          // 1, 7, 30 or use "all_days" via timeIncrementAllDays
    private Boolean timeIncrementAllDays;   // if true -> time_increment=all_days
    private List<String> fields;
    private Map<String, Object> breakdowns; // e.g. {"breakdowns": "age,gender"}
    private String actionBreakdowns;        // e.g. "action_type"
    private String actionReportTime;        // e.g. "conversion"
}

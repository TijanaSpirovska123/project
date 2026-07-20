package com.example.marketing.insights.analytics.dto;

import com.example.marketing.insights.analytics.enums.FindingSeverity;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * A single deterministic, evidence-based finding. Never produced by an LLM — always a fixed
 * rule evaluated against canonical metrics. Must not contain unsupported causal claims (see
 * FindingEngine's restrictions).
 */
@Value
@Builder
public class DeterministicFindingDto {
    String code;
    FindingSeverity severity;
    ScopeRefDto scope;
    String title;
    String message;
    List<FindingEvidenceDto> evidence;
    /** HIGH / MEDIUM / LOW — a simple, deterministic confidence label, not a probability. */
    String confidence;
}

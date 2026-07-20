package com.example.marketing.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsightWarningDto {
    private String code;
    private String message;

    public static InsightWarningDto of(String code, String message) {
        return new InsightWarningDto(code, message);
    }
}

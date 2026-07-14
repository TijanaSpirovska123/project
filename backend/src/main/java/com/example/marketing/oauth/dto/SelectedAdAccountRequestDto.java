package com.example.marketing.oauth.dto;

import lombok.Data;
import java.util.List;
@Data
public class SelectedAdAccountRequestDto {
    private List<String> adAccountIds;
}

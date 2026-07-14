package com.example.marketing.userconfig.api;

import com.example.marketing.infrastructure.api.BaseController;
import com.example.marketing.userconfig.dto.ColumnConfigDto;
import com.example.marketing.userconfig.dto.InsightsConfigDto;
import com.example.marketing.userconfig.service.UserConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user-config")
@RequiredArgsConstructor
public class UserConfigController extends BaseController {

    private final UserConfigService userConfigService;

    // ── Insights metric cards ─────────────────────────────────────────────────

    @GetMapping("/insights")
    public BaseResponse<InsightsConfigDto> getInsightsConfig(Authentication auth) {
        return ok(userConfigService.getInsightsConfig(extractUserId(auth)));
    }

    @PutMapping("/insights")
    public BaseResponse<InsightsConfigDto> saveInsightsConfig(
            Authentication auth,
            @RequestBody InsightsConfigDto dto) {
        return ok(userConfigService.saveInsightsConfig(extractUserId(auth), dto));
    }

    // ── Column visibility / order ─────────────────────────────────────────────

    @GetMapping("/columns/{entityType}")
    public BaseResponse<ColumnConfigDto> getColumnConfig(
            Authentication auth,
            @PathVariable String entityType) {
        return ok(userConfigService.getColumnConfig(extractUserId(auth), entityType));
    }

    @PutMapping("/columns")
    public BaseResponse<ColumnConfigDto> saveColumnConfig(
            Authentication auth,
            @RequestBody ColumnConfigDto dto) {
        return ok(userConfigService.saveColumnConfig(extractUserId(auth), dto));
    }
}

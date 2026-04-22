package com.example.marketing.insights.api;

import com.example.marketing.infrastructure.api.BaseController;
import com.example.marketing.insights.dto.InsightsSavedViewDto;
import com.example.marketing.insights.service.InsightsSavedViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/insights/saved-views")
public class InsightsSavedViewController extends BaseController {

    private final InsightsSavedViewService service;

    @PostMapping
    public BaseResponse<InsightsSavedViewDto> create(
            Authentication auth,
            @RequestBody InsightsSavedViewDto dto) {
        return ok(service.create(extractUserId(auth), dto));
    }

    @GetMapping
    public BaseResponse<List<InsightsSavedViewDto>> list(
            Authentication auth,
            @RequestParam String provider) {
        return ok(service.listForProvider(extractUserId(auth), provider));
    }

    @GetMapping("/{id}")
    public BaseResponse<InsightsSavedViewDto> getById(
            Authentication auth,
            @PathVariable Long id) {
        return ok(service.getById(extractUserId(auth), id));
    }

    @PutMapping("/{id}")
    public BaseResponse<InsightsSavedViewDto> update(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody InsightsSavedViewDto dto) {
        return ok(service.update(extractUserId(auth), id, dto));
    }

    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(
            Authentication auth,
            @PathVariable Long id) {
        service.delete(extractUserId(auth), id);
        return ok(null);
    }

    @PostMapping("/{id}/toggle-pin")
    public BaseResponse<InsightsSavedViewDto> togglePin(
            Authentication auth,
            @PathVariable Long id) {
        return ok(service.togglePin(extractUserId(auth), id));
    }
}

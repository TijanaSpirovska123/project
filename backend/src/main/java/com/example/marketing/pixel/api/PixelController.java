package com.example.marketing.pixel.api;

import com.example.marketing.infrastructure.Endpoints;
import com.example.marketing.infrastructure.api.BaseController;
import com.example.marketing.pixel.dto.PixelDto;
import com.example.marketing.pixel.service.PixelService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(Endpoints.BASE_PIXEL)
public class PixelController extends BaseController {

    private final PixelService pixelService;

    @GetMapping("/meta/{adAccountId}")
    public BaseResponse<List<PixelDto>> getAllPixelsFromMeta(Authentication auth, @PathVariable String adAccountId) {
        Long userId = extractUserId(auth);
        return ok(pixelService.getAllPixelsFromMeta(userId, adAccountId));
    }
}

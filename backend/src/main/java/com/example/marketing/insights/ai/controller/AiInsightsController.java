package com.example.marketing.insights.ai.controller;

import com.example.marketing.exception.BusinessException;
import com.example.marketing.infrastructure.api.BaseController;
import com.example.marketing.insights.ai.dto.AiInsightResponseDto;
import com.example.marketing.insights.ai.dto.GenerateAiInsightRequestDto;
import com.example.marketing.insights.ai.service.AiInsightGenerationService;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights/ai")
@RequiredArgsConstructor
public class AiInsightsController extends BaseController {

    private final AiInsightGenerationService generationService;
    private final UserRepository userRepository;

    @PostMapping("/analyze")
    public BaseResponse<AiInsightResponseDto> analyze(
            Authentication auth,
            @Valid @RequestBody GenerateAiInsightRequestDto request
    ) {
        UserEntity user = currentUser(auth);

        return ok(
                generationService.generate(
                        user,
                        request
                )
        );
    }

    private UserEntity currentUser(Authentication auth) {
        Long userId = extractUserId(auth);
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));
    }
}
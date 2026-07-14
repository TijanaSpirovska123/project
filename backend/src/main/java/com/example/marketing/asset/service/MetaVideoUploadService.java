package com.example.marketing.asset.service;

import com.example.marketing.asset.entity.StoredAssetVariantEntity;
import com.example.marketing.asset.repository.StoredAssetVariantRepository;
import com.example.marketing.asset.storage.ObjectStorageClient;
import com.example.marketing.exception.BusinessException;
import com.example.marketing.exception.StatusErrorResponse;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.oauth.service.TokenService;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MetaVideoUploadService {

    private static final Logger log = LoggerFactory.getLogger(MetaVideoUploadService.class);

    private final RestTemplate restTemplate;
    private final ObjectStorageClient storage;
    private final StoredAssetVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;

    @Value("${meta.graph-api-version:v23.0}")
    private String apiVersion;

    public String uploadVideoToMeta(StoredAssetVariantEntity variant, Long userId, String adAccountId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        String rawToken = tokenService.getAccessToken(user, Provider.META);
        // Strip "Bearer " prefix if present (same as AdCreativeService pattern)
        String accessToken = (rawToken != null) ? rawToken.trim() : "";
        if (accessToken.toLowerCase().startsWith("bearer ")) {
            accessToken = accessToken.substring(7).trim();
        }

        String accountId = adAccountId.startsWith("act_") ? adAccountId : "act_" + adAccountId;

        byte[] videoBytes = storage.getBytes(variant.getBucket(), variant.getObjectKey());

        String url = String.format("https://graph-video.facebook.com/%s/%s/advideos", apiVersion, accountId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        final String token = accessToken;
        body.add("source", new ByteArrayResource(videoBytes) {
            @Override
            public String getFilename() { return "video.mp4"; }
        });
        body.add("access_token", token);
        body.add("title", "AdFlow Video");

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response;
        try {
            response = restTemplate.postForEntity(url, request, Map.class);
        } catch (HttpStatusCodeException ex) {
            String metaBody = ex.getResponseBodyAsString();
            log.error("Meta video upload failed HTTP {}: {}", ex.getStatusCode(), metaBody);
            throw BusinessException.of(StatusErrorResponse.FACEBOOK_API_ERROR,
                    "Failed to upload video to Meta (HTTP " + ex.getStatusCode() + "): " + metaBody);
        }

        if (response.getBody() == null || !response.getBody().containsKey("id")) {
            throw BusinessException.of(StatusErrorResponse.FACEBOOK_API_ERROR,
                    "Meta video upload failed — no video ID returned");
        }

        String videoId = response.getBody().get("id").toString();
        log.info("Uploaded video to Meta, video_id: {}", videoId);

        waitForVideoReady(videoId, accessToken);

        variant.setMetaVideoId(videoId);
        variant.setUpdatedAt(LocalDateTime.now());
        variantRepository.save(variant);

        return videoId;
    }

    private void waitForVideoReady(String videoId, String accessToken) {
        String url = String.format("https://graph.facebook.com/%s/%s?fields=status&access_token=%s",
                apiVersion, videoId, accessToken);
        int maxAttempts = 20;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                Thread.sleep(3000);
                ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
                if (response.getBody() != null) {
                    Object statusObj = response.getBody().get("status");
                    if (statusObj instanceof Map<?, ?> status) {
                        Object videoStatusObj = status.get("video_status");
                        if (videoStatusObj != null) {
                            String videoStatus = videoStatusObj.toString();
                            log.info("Video {} status: {}", videoId, videoStatus);
                            if ("ready".equals(videoStatus)) return;
                            if ("error".equals(videoStatus)) {
                                throw BusinessException.of(StatusErrorResponse.FACEBOOK_API_ERROR,
                                        "Meta video processing failed for: " + videoId);
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for video to be ready", e);
            }
        }
        log.warn("Video {} not confirmed ready after timeout — proceeding", videoId);
    }
}

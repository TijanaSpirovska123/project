package com.example.marketing.asset.api;

import com.example.marketing.asset.dto.CropVariantsRequestDto;
import com.example.marketing.asset.dto.MetaImageSyncResultDto;
import com.example.marketing.asset.dto.SetTagsRequestDto;
import com.example.marketing.asset.dto.StoredAssetDto;
import com.example.marketing.asset.service.MetaAssetSyncService;
import com.example.marketing.asset.service.StoredAssetService;
import com.example.marketing.infrastructure.api.BaseController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assets")
public class StoredAssetController extends BaseController {

    private final StoredAssetService service;
    private final MetaAssetSyncService metaAssetSyncService;

    @PostMapping(value = "/upload", consumes = MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<StoredAssetDto> upload(Authentication auth,
                                               @RequestParam("file") MultipartFile file) {
        Long userId = extractUserId(auth);
        return ok(service.uploadOriginal(userId, file));
    }

    @PostMapping(value = "/upload/video", consumes = MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<StoredAssetDto> uploadVideo(Authentication auth,
                                                    @RequestParam("file") MultipartFile file) {
        Long userId = extractUserId(auth);
        String contentType = file.getContentType();
        if (contentType == null || !isValidVideoType(contentType)) {
            throw new IllegalArgumentException("Invalid video type. Supported: MP4, MOV, AVI");
        }
        if (file.getSize() > 4L * 1024 * 1024 * 1024) {
            throw new IllegalArgumentException("Video file too large. Maximum size is 4GB");
        }
        return ok(service.uploadVideo(userId, file));
    }

    private static boolean isValidVideoType(String contentType) {
        return contentType.equals("video/mp4")
                || contentType.equals("video/quicktime")
                || contentType.equals("video/avi")
                || contentType.equals("video/x-msvideo")
                || contentType.equals("video/mov");
    }

    @GetMapping
    public BaseResponse<List<StoredAssetDto>> list(Authentication auth,
                                                    @RequestParam(required = false) List<String> tags) {
        Long userId = extractUserId(auth);
        return ok(service.listMyAssets(userId, tags));
    }

    @GetMapping("/{assetId}")
    public BaseResponse<StoredAssetDto> get(Authentication auth, @PathVariable Long assetId) {
        Long userId = extractUserId(auth);
        return ok(service.getMyAsset(userId, assetId));
    }

    @GetMapping("/{assetId}/status")
    public BaseResponse<StoredAssetDto> getStatus(Authentication auth, @PathVariable Long assetId) {
        Long userId = extractUserId(auth);
        return ok(service.getAssetStatus(userId, assetId));
    }

    @PostMapping("/{assetId}/tags")
    public BaseResponse<StoredAssetDto> setTags(Authentication auth,
                                                 @PathVariable Long assetId,
                                                 @RequestBody SetTagsRequestDto request) {
        Long userId = extractUserId(auth);
        return ok(service.setTags(userId, assetId, request));
    }

    // Redirect to presigned URL
    @GetMapping("/{assetId}/variants/{variantKey}/download")
    public ResponseEntity<Void> download(Authentication auth,
                                         @PathVariable Long assetId,
                                         @PathVariable String variantKey) {

        // basic sanity — accept lowercase too; service normalises to uppercase
        if (!variantKey.matches("^[A-Za-z0-9_\\-]{1,64}$")) {
            return ResponseEntity.badRequest().build();
        }

        Long userId = extractUserId(auth);
        String url = service.getDownloadUrl(userId, assetId, variantKey);

        return ResponseEntity.status(HttpStatus.SEE_OTHER) // 303
                .location(URI.create(url))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @PostMapping("/sync-meta-hashes")
    public BaseResponse<MetaImageSyncResultDto> syncMetaHashes(
            Authentication auth,
            @RequestParam String adAccountId) {
        Long userId = extractUserId(auth);
        MetaImageSyncResultDto result = metaAssetSyncService.syncImageHashes(userId, adAccountId);
        return ok(result);
    }

    @PostMapping("/{assetId}/variants/crop")
    public BaseResponse<StoredAssetDto> cropVariants(
            Authentication auth,
            @PathVariable Long assetId,
            @RequestBody CropVariantsRequestDto request
    ) {
        Long userId = extractUserId(auth);
        return ok(service.cropAndGenerateVariants(userId, assetId, request));
    }

}

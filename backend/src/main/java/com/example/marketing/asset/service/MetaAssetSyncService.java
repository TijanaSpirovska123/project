package com.example.marketing.asset.service;

import com.example.marketing.adcreative.entity.AdAssetEntity;
import com.example.marketing.adcreative.repository.AdAssetRepository;
import com.example.marketing.asset.dto.MetaImageSyncResultDto;
import com.example.marketing.asset.entity.StoredAssetVariantEntity;
import com.example.marketing.asset.repository.StoredAssetVariantRepository;
import com.example.marketing.asset.storage.ObjectStorageClient;
import com.example.marketing.exception.BusinessException;
import com.example.marketing.infrastructure.strategy.PlatformClientRegistry;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.oauth.service.TokenService;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetaAssetSyncService {

    private final PlatformClientRegistry clients;
    private final TokenService tokens;
    private final StoredAssetVariantRepository variantRepository;
    private final AdAssetRepository adAssetRepository;
    private final ObjectStorageClient storage;
    private final UserRepository userRepository;

    @Transactional
    public MetaImageSyncResultDto syncImageHashes(Long userId, String adAccountId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        int newlyMatched = 0;
        int removedStale = 0;

        // ── Step A — Fast path: AdAssetEntity already has hash + storedVariant FK ──
        // Covers every variant previously pushed through this app.
        // No MinIO download or Meta API call needed.
        List<AdAssetEntity> unhashedAdAssets = adAssetRepository.findImageAssetsWithUnhashedVariant(userId);
        for (AdAssetEntity aa : unhashedAdAssets) {
            StoredAssetVariantEntity variant = aa.getStoredVariant();
            if (variant != null && aa.getHash() != null && !aa.getHash().isBlank()) {
                variant.setMetaImageHash(aa.getHash());
                variantRepository.save(variant);
                newlyMatched++;
            }
        }

        // ── Steps B & C — Meta API paths (optional; isolated so failures don't ────
        //                  roll back the fast-path saves above)
        if (adAccountId != null && !adAccountId.isBlank()) {
            try {
                String act = adAccountId.startsWith("act_") ? adAccountId : "act_" + adAccountId;
                Map<String, Map<String, Object>> metaImages = fetchAllMetaImages(user, act);

                // Step B — verify hashes on variants that already have one
                List<AdAssetEntity> hashedAdAssets = adAssetRepository.findImageAssetsWithHashedVariant(userId);
                for (AdAssetEntity aa : hashedAdAssets) {
                    StoredAssetVariantEntity variant = aa.getStoredVariant();
                    if (variant != null && variant.getMetaImageHash() != null
                            && !metaImages.containsKey(variant.getMetaImageHash())) {
                        variant.setMetaImageHash(null);
                        variantRepository.save(variant);
                        removedStale++;
                    }
                }

                // Step C — slow path: variants with no AdAssetEntity at all — MD5 match
                List<StoredAssetVariantEntity> stillUnmatched =
                        variantRepository.findByAsset_User_IdAndMetaImageHashIsNull(userId);
                for (StoredAssetVariantEntity variant : stillUnmatched) {
                    try {
                        byte[] imageBytes = storage.getBytes(variant.getBucket(), variant.getObjectKey());
                        if (imageBytes == null || imageBytes.length == 0) continue;

                        MessageDigest md = MessageDigest.getInstance("MD5");
                        String localMd5 = HexFormat.of().formatHex(md.digest(imageBytes));

                        if (metaImages.containsKey(localMd5)) {
                            variant.setMetaImageHash(localMd5);
                            variantRepository.save(variant);
                            newlyMatched++;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to compute hash for variant {}: {}", variant.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Meta API sync skipped (fast-path results still saved): {}", e.getMessage());
            }
        }

        int totalWithHash = variantRepository.findByAsset_User_IdAndMetaImageHashIsNotNull(userId).size();
        int totalWithout  = variantRepository.findByAsset_User_IdAndMetaImageHashIsNull(userId).size();

        return MetaImageSyncResultDto.builder()
                .totalMetaImages(0)
                .totalLocalVariants(totalWithHash + totalWithout)
                .newlyMatched(newlyMatched)
                .alreadyMatched(totalWithHash - newlyMatched)
                .removedStale(removedStale)
                .unmatched(totalWithout)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> fetchAllMetaImages(UserEntity user, String act) {
        var token = tokens.getAccessToken(user, Provider.META);
        var client = clients.of(Provider.META);

        String path = act + "/adimages";
        Map<String, Map<String, Object>> result = new HashMap<>();
        String after = null;

        do {
            Map<String, String> q = new HashMap<>();
            q.put("fields", "hash,name,url_128,width,height,status");
            q.put("limit", "500");
            if (after != null) q.put("after", after);

            ResponseEntity<Map> resp = client.get(path, q, token);
            Map body = resp.getBody();
            if (body == null || !resp.getStatusCode().is2xxSuccessful()) break;

            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
            if (data != null) {
                for (Map<String, Object> image : data) {
                    Object hash = image.get("hash");
                    if (hash != null && !hash.toString().isBlank()) {
                        result.put(hash.toString(), image);
                    }
                }
            }

            Map paging = (Map) body.get("paging");
            Map cursors = paging == null ? null : (Map) paging.get("cursors");
            after = cursors == null ? null : (String) cursors.get("after");

        } while (after != null);

        return result;
    }
}

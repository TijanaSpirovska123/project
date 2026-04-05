package com.example.marketing.asset.service;

import com.example.marketing.adcreative.entity.AdAssetEntity;
import com.example.marketing.adcreative.repository.AdAssetRepository;
import com.example.marketing.asset.dto.*;
import com.example.marketing.asset.dto.SetTagsRequestDto;
import com.example.marketing.exception.BusinessException;
import com.example.marketing.asset.entity.StoredAssetEntity;
import com.example.marketing.asset.entity.StoredAssetVariantEntity;
import com.example.marketing.asset.repository.StoredAssetRepository;
import com.example.marketing.asset.repository.StoredAssetVariantRepository;
import com.example.marketing.asset.storage.ObjectStorageClient;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StoredAssetService {

    private static final Logger log = LoggerFactory.getLogger(StoredAssetService.class);

    private final StoredAssetRepository assetRepository;
    private final StoredAssetVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final ObjectStorageClient storage;
    private final AdAssetRepository adAssetRepository;

    @Value("${storage.bucket.assets:marketing-assets}")
    private String assetsBucket;

    /**
     * Meta Ad Manager canonical formats.
     * Each entry defines the target aspect ratio (used for center-crop), the
     * preferred output resolution, and the minimum post-crop dimensions below
     * which the variant is skipped rather than upscaled (Meta rejects tiny ads).
     *
     * Keys follow the pattern META_<LABEL>_<WIDTH> so the frontend can identify
     * them easily (e.g. META_SQUARE_1080, META_VERTICAL_1080 …).
     */
    private static final List<MetaVariantSpec> META_AD_SPECS = List.of(
            // 1:1 – Feed Square
            new MetaVariantSpec("META_SQUARE_1080", 1080, 1080, 600, 600),
            // 4:5 – Feed Vertical
            new MetaVariantSpec("META_VERTICAL_1080", 1080, 1350, 600, 750),
            // 9:16 – Stories / Reels
            new MetaVariantSpec("META_STORIES_1080", 1080, 1920, 600, 1067),
            // 1.91:1 – Landscape / Link-click
            new MetaVariantSpec("META_LANDSCAPE_1200", 1200, 628, 600, 314));

    @Transactional
    public StoredAssetDto uploadOriginal(Long userId, MultipartFile file) {
        requireNonEmpty(file);

        String contentType = requireSupportedContentType(file);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        String originalName = safeFilename(file.getOriginalFilename());
        String extension = fileExtension(originalName, contentType);

        // Read bytes first so we can use actual length for size validation
        // (file.getSize() may return -1 for streaming uploads)
        byte[] bytes = readBytes(file);
        long size = requireMaxSize(bytes, 25 * 1024 * 1024); // 25MB

        // ✅ compute hash for dedup
        String hash = sha256Hex(bytes);

        // ✅ dedup: if exists and not FAILED, return existing + variants, no re-upload
        Optional<StoredAssetEntity> existing = assetRepository.findByUserAndHash(user, hash);
        if (existing.isPresent()) {
            StoredAssetEntity a = existing.get();
            if (!"FAILED".equalsIgnoreCase(a.getStatus())) {
                List<StoredAssetVariantEntity> variants = variantRepository
                        .findAllByAssetIdOrderByCreatedAtDesc(a.getId());
                return toDto(a, variants);
            }
            // FAILED asset with same hash: fall through and re-upload
        }

        // Detect dims
        Integer originalW = null;
        Integer originalH = null;
        if (!isVideo(contentType)) {
            ImageSize s = readImageSize(bytes);
            originalW = s.width;
            originalH = s.height;
        }

        // 1) Create asset row (PROCESSING)
        StoredAssetEntity asset = new StoredAssetEntity();
        asset.setUser(user);
        asset.setAssetType(isVideo(contentType) ? "VIDEO" : "IMAGE");
        asset.setOriginalFilename(originalName);
        asset.setMimeType(contentType);
        asset.setSizeBytes(size);
        asset.setHash(hash);
        asset.setStatus("PROCESSING");
        asset.setCreatedAt(LocalDateTime.now());
        asset.setUpdatedAt(LocalDateTime.now());
        asset = assetRepository.save(asset);

        // 2) Upload ORIGINAL variant
        final String variantKey = "ORIGINAL";
        final String objectKey = buildObjectKey(userId, asset.getId(), variantKey, extension);

        try {
            storage.put(
                    assetsBucket,
                    objectKey,
                    bytes,
                    contentType,
                    Map.of(
                            "userId", String.valueOf(userId),
                            "assetId", String.valueOf(asset.getId()),
                            "variantKey", variantKey,
                            "filename", originalName,
                            "hash", hash));
        } catch (Exception e) {
            asset.setStatus("FAILED");
            asset.setUpdatedAt(LocalDateTime.now());
            assetRepository.save(asset);
            throw new RuntimeException("Failed to upload to storage", e);
        }

        // 3) Save ORIGINAL variant row
        StoredAssetVariantEntity originalVariant = new StoredAssetVariantEntity();
        originalVariant.setAsset(asset);
        originalVariant.setVariantKey(variantKey);
        originalVariant.setBucket(assetsBucket);
        originalVariant.setObjectKey(objectKey);
        originalVariant.setWidth(originalW);
        originalVariant.setHeight(originalH);
        originalVariant.setCreatedAt(LocalDateTime.now());
        originalVariant.setUpdatedAt(LocalDateTime.now());

        try {
            variantRepository.save(originalVariant);
        } catch (Exception e) {
            // cleanup object
            try {
                storage.delete(assetsBucket, objectKey);
            } catch (Exception ignored) {
            }
            asset.setStatus("FAILED");
            asset.setUpdatedAt(LocalDateTime.now());
            assetRepository.save(asset);
            throw new RuntimeException("Failed to save ORIGINAL variant in DB", e);
        }

        // 4) default variants (non-critical)
        if (!isVideo(contentType)) {
            try {
                generateDefaultImageVariants(userId, asset, bytes, contentType, extension);
            } catch (Exception e) {
                log.warn("Default variant generation failed for asset {}, continuing", asset.getId(), e);
            }
        }

        // 5) mark READY
        asset.setStatus("READY");
        asset.setUpdatedAt(LocalDateTime.now());
        assetRepository.save(asset);

        List<StoredAssetVariantEntity> variants = variantRepository.findAllByAssetIdOrderByCreatedAtDesc(asset.getId());

        return toDto(asset, variants);
    }

    public List<StoredAssetDto> listMyAssets(Long userId) {
        return listMyAssets(userId, null);
    }

    public StoredAssetDto getMyAsset(Long userId, Long assetId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        StoredAssetEntity a = assetRepository.findByIdAndUser(assetId, user)
                .orElseThrow(() -> BusinessException.notFound(
                        "Asset not found with id " + assetId + " for current user"));

        List<StoredAssetVariantEntity> variants = variantRepository.findAllByAssetIdOrderByCreatedAtDesc(assetId);

        return toDto(a, variants);
    }

    public List<StoredAssetDto> listMyAssets(Long userId, List<String> tags) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        // Build map of originalFilename -> hash from ad_assets (first/most-recent match per filename)
        Map<String, String> adHashByFilename = new java.util.LinkedHashMap<>();
        for (AdAssetEntity aa : adAssetRepository.findAllByUserOrderByCreatedAtDesc(user)) {
            adHashByFilename.putIfAbsent(aa.getOriginalFilename(), aa.getHash());
        }

        List<StoredAssetEntity> assets = assetRepository.findAllByUserOrderByCreatedAtDesc(user);

        if (tags != null && !tags.isEmpty()) {
            List<String> normalizedTags = tags.stream().map(String::toLowerCase).toList();
            assets = assets.stream()
                    .filter(a -> {
                        List<String> assetTags = parseTags(a.getTags());
                        return assetTags.containsAll(normalizedTags);
                    })
                    .toList();
        }

        List<StoredAssetDto> out = new ArrayList<>(assets.size());
        for (StoredAssetEntity a : assets) {
            List<StoredAssetVariantEntity> variants = variantRepository.findAllByAssetIdOrderByCreatedAtDesc(a.getId());
            StoredAssetDto dto = toDto(a, variants);
            // Use hash from ad_assets when this filename was uploaded to a platform
            String adHash = adHashByFilename.get(a.getOriginalFilename());
            if (adHash != null) {
                dto.setHash(adHash);
            }
            out.add(dto);
        }
        return out;
    }

    @Transactional
    public StoredAssetDto setTags(Long userId, Long assetId, SetTagsRequestDto request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        StoredAssetEntity a = assetRepository.findByIdAndUser(assetId, user)
                .orElseThrow(() -> BusinessException.notFound(
                        "Asset not found with id " + assetId + " for current user"));

        List<String> sanitized = request.getTags() == null ? List.of() :
                request.getTags().stream()
                        .filter(t -> t != null && !t.isBlank())
                        .map(t -> t.trim().toLowerCase())
                        .distinct()
                        .toList();

        a.setTags(String.join(",", sanitized));
        a.setUpdatedAt(LocalDateTime.now());
        assetRepository.save(a);

        List<StoredAssetVariantEntity> variants = variantRepository.findAllByAssetIdOrderByCreatedAtDesc(assetId);
        return toDto(a, variants);
    }

    public StoredAssetDto getAssetStatus(Long userId, Long assetId) {
        return getMyAsset(userId, assetId);
    }

    public String getDownloadUrl(Long userId, Long assetId, String variantKey) {
        getMyAsset(userId, assetId);

        String normalizedKey = normalizeVariantKey(variantKey);

        StoredAssetVariantEntity v = variantRepository.findByAssetIdAndVariantKey(assetId, normalizedKey)
                .orElseThrow(() -> BusinessException.notFound(
                        "Variant '" + normalizedKey + "' not found for asset " + assetId));

        return storage.presignGet(v.getBucket(), v.getObjectKey(), Duration.ofMinutes(10));
    }

    /*
     * =========================
     * VARIANT GENERATION (unchanged)
     * =========================
     */

    private void generateDefaultImageVariants(Long userId,
            StoredAssetEntity asset,
            byte[] originalBytes,
            String contentType,
            String extension) {

        // ── Meta Ad Manager formats (center-crop → scale, never upscale) ────────
        for (MetaVariantSpec spec : META_AD_SPECS) {
            try {
                upsertCenterCropVariant(userId, asset, originalBytes, contentType, extension, spec);
            } catch (Exception e) {
                log.warn("Failed to generate Meta variant {} for asset {}, skipping",
                        spec.key, asset.getId(), e);
            }
        }
    }

    /**
     * Generates one Meta Ad variant using center-crop + proportional scale-down.
     *
     * <p>
     * Algorithm:
     * <ol>
     * <li>Compute the largest rectangle in the source image that matches the
     * target aspect ratio, anchored at the center.</li>
     * <li>If that rectangle is smaller than Meta's minimum dimensions, skip the
     * variant — we must not upscale below-minimum content.</li>
     * <li>Scale the cropped region down to the target output size. If the
     * source is already smaller than the target in either dimension, use the
     * source size (no upscaling).</li>
     * </ol>
     */
    private void upsertCenterCropVariant(Long userId,
            StoredAssetEntity asset,
            byte[] originalBytes,
            String contentType,
            String extension,
            MetaVariantSpec spec) {

        ImageSize src = readImageSize(originalBytes);
        int[] rect = centerCropRect(src.width, src.height, spec.targetW, spec.targetH);
        int cropX = rect[0], cropY = rect[1], cropW = rect[2], cropH = rect[3];

        // Skip if the original is too small to satisfy Meta's minimum after crop
        if (cropW < spec.minW || cropH < spec.minH) {
            log.warn("Asset {} skipped variant {} – crop {}×{} is below Meta minimum {}×{}",
                    asset.getId(), spec.key, cropW, cropH, spec.minW, spec.minH);
            return;
        }

        // Never upscale: output at most the target, but no larger than the crop itself
        int outW = Math.min(spec.targetW, cropW);
        int outH = Math.min(spec.targetH, cropH);

        String outputFormat = outputFormatFromContentType(contentType);
        byte[] outBytes;
        try (var in = new ByteArrayInputStream(originalBytes);
                var out = new ByteArrayOutputStream()) {
            Thumbnails.of(in)
                    .sourceRegion(cropX, cropY, cropW, cropH)
                    .size(outW, outH)
                    .keepAspectRatio(false) // ratio is already exact from centerCropRect
                    .outputFormat(outputFormat)
                    .outputQuality(0.92) // high quality — Meta penalises compression artefacts
                    .toOutputStream(out);
            outBytes = out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Meta variant " + spec.key, e);
        }

        ImageSize outSize = readImageSize(outBytes);
        String vKey = spec.key;
        String newObjKey = buildObjectKey(userId, asset.getId(), vKey, extension);

        StoredAssetVariantEntity existing = variantRepository
                .findByAssetIdAndVariantKey(asset.getId(), vKey)
                .orElse(null);

        String oldBucket = null, oldKey = null;
        if (existing != null) {
            oldBucket = existing.getBucket();
            oldKey = existing.getObjectKey();
        }

        storage.put(assetsBucket, newObjKey, outBytes, contentType,
                Map.of(
                        "userId", String.valueOf(userId),
                        "assetId", String.valueOf(asset.getId()),
                        "variantKey", vKey,
                        "sourceVariant", "ORIGINAL"));

        StoredAssetVariantEntity toSave = (existing != null) ? existing : new StoredAssetVariantEntity();
        toSave.setAsset(asset);
        toSave.setVariantKey(vKey);
        toSave.setBucket(assetsBucket);
        toSave.setObjectKey(newObjKey);
        toSave.setWidth(outSize.width);
        toSave.setHeight(outSize.height);
        toSave.setUpdatedAt(LocalDateTime.now());
        if (toSave.getCreatedAt() == null)
            toSave.setCreatedAt(LocalDateTime.now());

        try {
            variantRepository.save(toSave);
        } catch (Exception e) {
            try {
                storage.delete(assetsBucket, newObjKey);
            } catch (Exception ignored) {
            }
            throw e;
        }

        if (oldBucket != null && oldKey != null && !oldKey.equals(newObjKey)) {
            try {
                storage.delete(oldBucket, oldKey);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Returns the largest center-anchored crop rectangle of (srcW × srcH) that
     * exactly matches the aspect ratio targetW:targetH.
     *
     * @return [x, y, w, h] in source-image pixels
     */
    private static int[] centerCropRect(int srcW, int srcH, int targetW, int targetH) {
        double tgtRatio = (double) targetW / targetH;
        double srcRatio = (double) srcW / srcH;

        int cropW, cropH;
        if (srcRatio > tgtRatio) {
            // Source is wider than target → constrain by height, crop width
            cropH = srcH;
            cropW = (int) Math.round((double) srcH * targetW / targetH);
        } else {
            // Source is taller than target → constrain by width, crop height
            cropW = srcW;
            cropH = (int) Math.round((double) srcW * targetH / targetW);
        }
        // Clamp to source bounds (rounding can push 1 px over)
        cropW = Math.min(cropW, srcW);
        cropH = Math.min(cropH, srcH);

        int cropX = (srcW - cropW) / 2;
        int cropY = (srcH - cropH) / 2;
        return new int[] { cropX, cropY, cropW, cropH };
    }

    @Transactional
    public StoredAssetDto cropAndGenerateVariants(Long userId, Long assetId, CropVariantsRequestDto request) {
        if (request == null || request.getVariants() == null || request.getVariants().isEmpty()) {
            throw new IllegalArgumentException("variants list is required");
        }

        // ownership check
        getMyAsset(userId, assetId);

        StoredAssetEntity asset = assetRepository.findById(assetId)
                .orElseThrow(() -> BusinessException.notFound("Asset not found with id: " + assetId));

        if (!"IMAGE".equalsIgnoreCase(asset.getAssetType())) {
            throw BusinessException.badRequest(
                    "Crop and variant generation is only supported for IMAGE assets, but asset " + assetId
                            + " is of type " + asset.getAssetType());
        }

        StoredAssetVariantEntity originalVariant = variantRepository
                .findByAssetIdAndVariantKey(assetId, "ORIGINAL")
                .orElseThrow(() -> BusinessException.notFound(
                        "ORIGINAL variant not found for asset " + assetId + ". The asset may not have been fully uploaded."));

        byte[] originalBytes = storage.getBytes(originalVariant.getBucket(), originalVariant.getObjectKey());

        String contentType = asset.getMimeType();
        String outputFormat = outputFormatFromContentType(contentType);
        String extension = outputFormat;

        ImageSize originalSize = readImageSize(originalBytes);
        int ow = originalSize.width;
        int oh = originalSize.height;

        // Track newly uploaded S3 keys for compensation on failure
        List<String> uploadedKeys = new ArrayList<>();
        try {
            for (CropVariantRequestDto vr : request.getVariants()) {
                String vKey = normalizeVariantKey(vr.getVariantKey());
                validateVariantKey(vKey);

                if (vr.getTargetWidth() <= 0 || vr.getTargetHeight() <= 0) {
                    throw new IllegalArgumentException("Invalid target size for " + vKey);
                }

                CropRectDto c = vr.getCrop();
                if (c == null)
                    throw new IllegalArgumentException("crop is required for " + vKey);

                validateCropRect(c, ow, oh);

                byte[] outBytes = cropAndResize(originalBytes, c, vr.getTargetWidth(), vr.getTargetHeight(),
                        outputFormat);

                String newObjectKey = buildObjectKey(userId, assetId, vKey, extension);

                StoredAssetVariantEntity existingVariant = variantRepository
                        .findByAssetIdAndVariantKey(assetId, vKey)
                        .orElse(null);

                String oldBucket = null;
                String oldKey = null;
                if (existingVariant != null) {
                    oldBucket = existingVariant.getBucket();
                    oldKey = existingVariant.getObjectKey();
                }

                storage.put(
                        assetsBucket,
                        newObjectKey,
                        outBytes,
                        contentType,
                        Map.of(
                                "userId", String.valueOf(userId),
                                "assetId", String.valueOf(assetId),
                                "variantKey", vKey,
                                "sourceVariant", "ORIGINAL"));
                uploadedKeys.add(newObjectKey);

                ImageSize outSize = readImageSize(outBytes);

                StoredAssetVariantEntity toSave = (existingVariant != null) ? existingVariant
                        : new StoredAssetVariantEntity();
                toSave.setAsset(asset);
                toSave.setVariantKey(vKey);
                toSave.setBucket(assetsBucket);
                toSave.setObjectKey(newObjectKey);
                toSave.setWidth(outSize.width);
                toSave.setHeight(outSize.height);
                toSave.setUpdatedAt(LocalDateTime.now());
                if (toSave.getCreatedAt() == null)
                    toSave.setCreatedAt(LocalDateTime.now());

                try {
                    variantRepository.save(toSave);
                } catch (Exception e) {
                    try {
                        storage.delete(assetsBucket, newObjectKey);
                    } catch (Exception ignored) {
                    }
                    uploadedKeys.remove(newObjectKey);
                    throw e;
                }

                if (oldBucket != null && oldKey != null && !oldKey.equals(newObjectKey)) {
                    try {
                        storage.delete(oldBucket, oldKey);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            // Compensate: delete all S3 objects uploaded in this request before the failure
            for (String key : uploadedKeys) {
                try {
                    storage.delete(assetsBucket, key);
                } catch (Exception ignored) {
                }
            }
            throw e;
        }

        StoredAssetEntity refreshedAsset = assetRepository.findById(assetId)
                .orElseThrow(() -> BusinessException.notFound("Asset not found with id: " + assetId));
        List<StoredAssetVariantEntity> variants = variantRepository.findAllByAssetIdOrderByCreatedAtDesc(assetId);
        return toDto(refreshedAsset, variants);
    }

    /*
     * =========================
     * CROPPING (keep your existing method as-is)
     * =========================
     */
    // Keep your cropAndGenerateVariants(...) unchanged from your current file

    /*
     * =========================
     * HELPERS
     * =========================
     */

    private static void validateVariantKey(String key) {
        if (key == null || key.isBlank())
            throw new IllegalArgumentException("variantKey is required");
        if (!key.matches("^[A-Z0-9_\\-]{1,64}$"))
            throw new IllegalArgumentException("Invalid variantKey: " + key);
        if ("ORIGINAL".equals(key))
            throw new IllegalArgumentException("Use ORIGINAL only for upload, not crop output");
    }

    private static void validateCropRect(CropRectDto c, int originalW, int originalH) {
        if (c.getW() <= 0 || c.getH() <= 0)
            throw new IllegalArgumentException("Crop w/h must be > 0");
        if (c.getX() < 0 || c.getY() < 0)
            throw new IllegalArgumentException("Crop x/y must be >= 0");
        if (c.getX() + c.getW() > originalW)
            throw new IllegalArgumentException("Crop exceeds original width");
        if (c.getY() + c.getH() > originalH)
            throw new IllegalArgumentException("Crop exceeds original height");
    }

    private static byte[] cropAndResize(byte[] original,
            CropRectDto crop,
            int targetW,
            int targetH,
            String outputFormat) {

        try (var in = new ByteArrayInputStream(original);
                var out = new ByteArrayOutputStream()) {

            Thumbnails.of(in)
                    .sourceRegion(crop.getX(), crop.getY(), crop.getW(), crop.getH())
                    .size(targetW, targetH)
                    .keepAspectRatio(false)
                    .outputFormat(outputFormat)
                    .toOutputStream(out);

            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to crop/resize image", e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256", e);
        }
    }

    private static byte[] resizeToFit(byte[] input, int width, int height, String contentType) throws Exception {
        try (var in = new ByteArrayInputStream(input);
                var out = new ByteArrayOutputStream()) {

            Thumbnails.of(in)
                    .size(width, height)
                    .outputFormat(outputFormatFromContentType(contentType))
                    .toOutputStream(out);

            return out.toByteArray();
        }
    }

    private static String outputFormatFromContentType(String contentType) {
        if (contentType == null)
            return "jpg";
        if (contentType.equals(MediaType.IMAGE_PNG_VALUE))
            return "png";
        if (contentType.equals(MediaType.IMAGE_JPEG_VALUE))
            return "jpg";
        if ("image/webp".equals(contentType))
            return "webp";
        return "jpg";
    }

    private static ImageSize readImageSize(byte[] bytes) {
        try (var in = new ByteArrayInputStream(bytes)) {
            var img = ImageIO.read(in);
            if (img == null)
                throw new IllegalArgumentException("Unsupported image format or corrupt image");
            return new ImageSize(img.getWidth(), img.getHeight());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read image size", e);
        }
    }

    private static class ImageSize {
        final int width;
        final int height;

        ImageSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static String normalizeVariantKey(String variantKey) {
        if (variantKey == null || variantKey.isBlank())
            throw new IllegalArgumentException("variantKey is required");
        return variantKey.trim().toUpperCase();
    }

    private static void requireNonEmpty(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("No file uploaded");
    }

    private static String requireSupportedContentType(MultipartFile file) {
        String ct = file.getContentType();
        if (ct == null)
            throw new IllegalArgumentException("Missing content type");

        boolean ok = ct.equals(MediaType.IMAGE_JPEG_VALUE) ||
                ct.equals(MediaType.IMAGE_PNG_VALUE) ||
                ct.equals("image/webp") ||
                ct.equals("video/mp4");

        if (!ok)
            throw new IllegalArgumentException("Unsupported content type: " + ct);
        return ct;
    }

    private static long requireMaxSize(byte[] bytes, long maxBytes) {
        long size = bytes.length;
        if (size == 0)
            throw new IllegalArgumentException("Empty upload");
        if (size > maxBytes)
            throw new IllegalArgumentException("File too large. Max bytes: " + maxBytes);
        return size;
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }
    }

    private static String safeFilename(String original) {
        if (original == null || original.isBlank())
            return "upload";
        return original.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static boolean isVideo(String contentType) {
        return contentType != null && contentType.startsWith("video/");
    }

    private static String fileExtension(String filename, String contentType) {
        int dot = filename.lastIndexOf('.');
        if (dot > 0 && dot < filename.length() - 1)
            return filename.substring(dot + 1).toLowerCase();
        if (MediaType.IMAGE_JPEG_VALUE.equals(contentType))
            return "jpg";
        if (MediaType.IMAGE_PNG_VALUE.equals(contentType))
            return "png";
        if ("image/webp".equals(contentType))
            return "webp";
        if ("video/mp4".equals(contentType))
            return "mp4";
        return "bin";
    }

    private static String buildObjectKey(Long userId, Long assetId, String variantKey, String ext) {
        // variantKey is already normalised to uppercase; keep that in the path for
        // consistency
        return "u/" + userId + "/assets/" + assetId + "/" + variantKey.toUpperCase() + "." + ext;
    }

    private static List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toList();
    }

    private StoredAssetDto toDto(StoredAssetEntity asset, List<StoredAssetVariantEntity> variants) {
        StoredAssetDto dto = new StoredAssetDto();
        dto.setId(asset.getId());
        dto.setUserId(asset.getUser().getId());
        dto.setAssetType(asset.getAssetType());
        dto.setOriginalFilename(asset.getOriginalFilename());
        dto.setMimeType(asset.getMimeType());
        dto.setSizeBytes(asset.getSizeBytes());
        dto.setHash(asset.getHash());
        dto.setStatus(asset.getStatus());
        dto.setTags(parseTags(asset.getTags()));
        dto.setCreatedAt(asset.getCreatedAt());
        dto.setUpdatedAt(asset.getUpdatedAt());

        List<StoredAssetVariantDto> out = new ArrayList<>();
        for (StoredAssetVariantEntity ve : variants) {
            StoredAssetVariantDto vd = new StoredAssetVariantDto();
            vd.setId(ve.getId());
            vd.setVariantKey(ve.getVariantKey());
            vd.setBucket(ve.getBucket());
            vd.setObjectKey(ve.getObjectKey());
            vd.setWidth(ve.getWidth());
            vd.setHeight(ve.getHeight());
            vd.setCreatedAt(ve.getCreatedAt());
            vd.setUpdatedAt(ve.getUpdatedAt());
            out.add(vd);
        }
        dto.setVariants(out);
        return dto;
    }

    private static class VariantSpec {
        final String key;
        final int width;
        final int height;

        VariantSpec(String key, int width, int height) {
            this.key = key;
            this.width = width;
            this.height = height;
        }
    }

    /** Describes one Meta Ad Manager output format. */
    private static class MetaVariantSpec {
        final String key;
        /** Preferred output width (px). */
        final int targetW;
        /** Preferred output height (px). */
        final int targetH;
        /** Meta minimum width after crop – skip if not met. */
        final int minW;
        /** Meta minimum height after crop – skip if not met. */
        final int minH;

        MetaVariantSpec(String key, int targetW, int targetH, int minW, int minH) {
            this.key = key;
            this.targetW = targetW;
            this.targetH = targetH;
            this.minW = minW;
            this.minH = minH;
        }
    }
}
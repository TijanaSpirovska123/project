package com.example.marketing.asset.service;

import com.example.marketing.asset.entity.StoredAssetEntity;
import com.example.marketing.asset.entity.StoredAssetVariantEntity;
import com.example.marketing.asset.entity.VideoVariantType;
import com.example.marketing.asset.repository.StoredAssetRepository;
import com.example.marketing.asset.repository.StoredAssetVariantRepository;
import com.example.marketing.asset.storage.ObjectStorageClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VideoVariantService {

    private static final Logger log = LoggerFactory.getLogger(VideoVariantService.class);

    private final ObjectStorageClient storage;
    private final StoredAssetVariantRepository variantRepository;
    private final StoredAssetRepository assetRepository;

    @Value("${storage.bucket.assets:marketing-assets}")
    private String assetsBucket;

    @Value("${app.ffmpeg.path:/usr/bin/ffmpeg}")
    private String ffmpegPath;

    @Value("${app.ffprobe.path:/usr/bin/ffprobe}")
    private String ffprobePath;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Async
    public void generateVariants(StoredAssetEntity asset) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("video-variants-");
            Path originalFile = downloadFromStorage(asset, tempDir);

            VideoMetadata metadata = getVideoMetadata(originalFile);
            asset.setDurationSeconds((int) metadata.durationSeconds());

            Path thumbnail = generateThumbnail(originalFile, tempDir);
            String thumbKey = uploadThumbnail(thumbnail, asset);
            asset.setThumbnailMinioKey(thumbKey);

            for (VideoVariantType variantType : VideoVariantType.values()) {
                if (variantType == VideoVariantType.ORIGINAL) continue;
                try {
                    generateAndSaveVariant(asset, originalFile, tempDir, variantType, metadata);
                } catch (Exception e) {
                    log.warn("Failed to generate video variant {} for asset {}: {}",
                            variantType, asset.getId(), e.getMessage());
                }
            }

            asset.setStatus("READY");
            asset.setUpdatedAt(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Failed to generate video variants for asset {}: {}", asset.getId(), e.getMessage(), e);
            asset.setStatus("FAILED");
            asset.setUpdatedAt(LocalDateTime.now());
        } finally {
            if (tempDir != null) cleanupTempDir(tempDir);
        }
        assetRepository.save(asset);
    }

    private void generateAndSaveVariant(StoredAssetEntity asset, Path originalFile, Path tempDir,
            VideoVariantType variantType, VideoMetadata originalMeta) throws Exception {

        int targetWidth = variantType.getWidth();
        int targetHeight = variantType.getHeight();
        Path outputFile = tempDir.resolve(variantType.name().toLowerCase() + ".mp4");

        String cropFilter = buildCropFilter(originalMeta.width(), originalMeta.height(), targetWidth, targetHeight);

        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath, "-i", originalFile.toString(),
                "-vf", cropFilter,
                "-c:v", "libx264", "-crf", "23", "-preset", "fast",
                "-c:a", "aac", "-b:a", "128k",
                "-movflags", "+faststart",
                "-y", outputFile.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg failed with exit code " + exitCode + " for variant " + variantType);
        }

        String variantKey = variantType.name();
        String objectKey = buildObjectKey(asset.getUser().getId(), asset.getId(), variantKey, "mp4");
        byte[] bytes = Files.readAllBytes(outputFile);

        storage.put(assetsBucket, objectKey, bytes, "video/mp4", Map.of(
                "userId", String.valueOf(asset.getUser().getId()),
                "assetId", String.valueOf(asset.getId()),
                "variantKey", variantKey
        ));

        StoredAssetVariantEntity existing = variantRepository
                .findByAssetIdAndVariantKey(asset.getId(), variantKey)
                .orElse(null);

        StoredAssetVariantEntity toSave = (existing != null) ? existing : new StoredAssetVariantEntity();
        toSave.setAsset(asset);
        toSave.setVariantKey(variantKey);
        toSave.setBucket(assetsBucket);
        toSave.setObjectKey(objectKey);
        toSave.setWidth(targetWidth);
        toSave.setHeight(targetHeight);
        toSave.setUpdatedAt(LocalDateTime.now());
        if (toSave.getCreatedAt() == null) toSave.setCreatedAt(LocalDateTime.now());

        variantRepository.save(toSave);
        log.info("Saved video variant {} for asset {}", variantKey, asset.getId());
    }

    private String buildCropFilter(int srcW, int srcH, int tgtW, int tgtH) {
        double srcRatio = (double) srcW / srcH;
        double tgtRatio = (double) tgtW / tgtH;
        if (srcRatio > tgtRatio) {
            int scaledW = (int) Math.round(srcW * ((double) tgtH / srcH));
            int cropX = (scaledW - tgtW) / 2;
            return String.format("scale=%d:%d,crop=%d:%d:%d:0", scaledW, tgtH, tgtW, tgtH, cropX);
        } else {
            int scaledH = (int) Math.round(srcH * ((double) tgtW / srcW));
            int cropY = (scaledH - tgtH) / 2;
            return String.format("scale=%d:%d,crop=%d:%d:0:%d", tgtW, scaledH, tgtW, tgtH, cropY);
        }
    }

    private Path generateThumbnail(Path videoFile, Path tempDir) throws Exception {
        Path thumbnail = tempDir.resolve("thumbnail.jpg");
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath, "-i", videoFile.toString(),
                "-ss", "00:00:01", "-vframes", "1", "-q:v", "2",
                "-y", thumbnail.toString()
        );
        pb.redirectErrorStream(true);
        pb.start().waitFor();
        return thumbnail;
    }

    private VideoMetadata getVideoMetadata(Path videoFile) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                ffprobePath,
                "-v", "quiet", "-print_format", "json", "-show_streams",
                videoFile.toString()
        );
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();

        JsonNode root = MAPPER.readTree(output);
        JsonNode streams = root.get("streams");

        int width = 1920, height = 1080;
        double duration = 0;

        if (streams != null) {
            for (JsonNode stream : streams) {
                if ("video".equals(stream.path("codec_type").asText())) {
                    width = stream.path("width").asInt(1920);
                    height = stream.path("height").asInt(1080);
                    duration = stream.path("duration").asDouble(0);
                    break;
                }
            }
        }
        return new VideoMetadata(width, height, duration);
    }

    private Path downloadFromStorage(StoredAssetEntity asset, Path tempDir) throws Exception {
        StoredAssetVariantEntity originalVariant = variantRepository
                .findByAssetIdAndVariantKey(asset.getId(), "ORIGINAL")
                .orElseThrow(() -> new RuntimeException(
                        "No ORIGINAL variant found for asset " + asset.getId()));

        byte[] bytes = storage.getBytes(originalVariant.getBucket(), originalVariant.getObjectKey());
        String extension = fileExtension(asset.getOriginalFilename(), asset.getMimeType());
        Path file = tempDir.resolve("original." + extension);
        Files.write(file, bytes);
        return file;
    }

    private String uploadThumbnail(Path thumbnail, StoredAssetEntity asset) throws Exception {
        if (!Files.exists(thumbnail)) {
            log.warn("Thumbnail not generated for asset {}, skipping", asset.getId());
            return null;
        }
        String key = buildObjectKey(asset.getUser().getId(), asset.getId(), "THUMBNAIL", "jpg");
        byte[] bytes = Files.readAllBytes(thumbnail);
        storage.put(assetsBucket, key, bytes, "image/jpeg", Map.of(
                "userId", String.valueOf(asset.getUser().getId()),
                "assetId", String.valueOf(asset.getId()),
                "variantKey", "THUMBNAIL"
        ));
        return key;
    }

    private static String buildObjectKey(Long userId, Long assetId, String variantKey, String ext) {
        return "u/" + userId + "/assets/" + assetId + "/" + variantKey.toUpperCase() + "." + ext;
    }

    private static String fileExtension(String filename, String mimeType) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot > 0 && dot < filename.length() - 1) {
                return filename.substring(dot + 1).toLowerCase();
            }
        }
        if ("video/mp4".equals(mimeType)) return "mp4";
        if ("video/quicktime".equals(mimeType)) return "mov";
        return "mp4";
    }

    private void cleanupTempDir(Path tempDir) {
        try {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });
        } catch (Exception e) {
            log.warn("Failed to cleanup temp dir: {}", tempDir, e);
        }
    }

    public record VideoMetadata(int width, int height, double durationSeconds) {}
}

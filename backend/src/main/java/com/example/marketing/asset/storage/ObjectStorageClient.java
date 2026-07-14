package com.example.marketing.asset.storage;

import java.time.Duration;
import java.util.Map;

public interface ObjectStorageClient {

    /**
     * Store bytes under (bucket, key).
     * metadata can include contentType, originalFilename, etc.
     */
    void put(String bucket, String key, byte[] bytes, String contentType, Map<String, String> metadata);

    /**
     * Create a short-lived download URL for frontend or server-to-server reads.
     */
    String presignGet(String bucket, String key, Duration ttl);

    /**
     * Optional for cleanup.
     */
    void delete(String bucket, String key);

    byte[] getBytes(String bucket, String key);
}

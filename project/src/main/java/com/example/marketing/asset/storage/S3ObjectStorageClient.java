package com.example.marketing.asset.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

@Component
public class S3ObjectStorageClient implements ObjectStorageClient {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String internalEndpoint;
    private final String publicEndpoint;

    public S3ObjectStorageClient(
            @Value("${s3.endpoint}") String endpoint,
            @Value("${s3.public-endpoint:}") String publicEndpoint,
            @Value("${s3.region}") String region,
            @Value("${s3.accessKey}") String accessKey,
            @Value("${s3.secretKey}") String secretKey
    ) {
        this.internalEndpoint = endpoint;
        this.publicEndpoint = publicEndpoint.isBlank() ? null : publicEndpoint.stripTrailing();
        var creds = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));

        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        this.s3 = S3Client.builder()
                .credentialsProvider(creds)
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(s3Config)
                .build();

        this.presigner = S3Presigner.builder()
                .credentialsProvider(creds)
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(s3Config)
                .build();
    }

    @Override
    public void put(String bucket, String key, byte[] bytes, String contentType, Map<String, String> metadata) {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("bucket is required");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key is required");
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("bytes empty");

        PutObjectRequest.Builder req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType);

        if (metadata != null && !metadata.isEmpty()) req.metadata(metadata);

        s3.putObject(req.build(), RequestBody.fromBytes(bytes));
    }

    @Override
    public String presignGet(String bucket, String key, Duration ttl) {
        if (ttl == null) ttl = Duration.ofMinutes(10);

        GetObjectRequest getReq = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getReq)
                .build();

        String url = presigner.presignGetObject(presignReq).url().toString();

        // Rewrite internal endpoint to public-facing URL when configured
        if (publicEndpoint != null) {
            url = url.replace(internalEndpoint, publicEndpoint);
        }
        return url;
    }

    @Override
    public void delete(String bucket, String key) {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("bucket is required");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key is required");
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public byte[] getBytes(String bucket, String key) {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("bucket is required");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key is required");

        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try (var res = s3.getObject(req)) {
            return res.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read object " + bucket + "/" + key, e);
        }
    }

}

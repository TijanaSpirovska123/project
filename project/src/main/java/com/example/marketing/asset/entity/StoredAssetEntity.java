package com.example.marketing.asset.entity;

import com.example.marketing.user.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Table(
        name = "stored_assets",
        indexes = {
                @Index(name = "idx_stored_assets_user_created", columnList = "user_id, created_at")
        }
)
public class StoredAssetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "IMAGE" | "VIDEO" (keep as String for now)
    @Column(name = "asset_type", nullable = false, length = 16)
    private String assetType;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 64)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "hash", nullable = false, length = 255)
    private String hash;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PROCESSING, READY, FAILED, ARCHIVED

    // Comma-separated tags, e.g. "summer,product,landscape"
    @Column(name = "tags", nullable = false)
    @Builder.Default
    private String tags = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

package com.example.marketing.asset.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Table(
        name = "stored_asset_variants",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_asset_variant_key",
                        columnNames = {"asset_id", "variant_key"}
                )
        },
        indexes = {
                @Index(name = "idx_asset_variants_asset", columnList = "asset_id")
        }
)
public class StoredAssetVariantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private StoredAssetEntity asset;

    @Column(name = "variant_key", nullable = false, length = 64)
    private String variantKey; // ORIGINAL, META_FEED_1080, etc.

    @Column(name = "bucket", nullable = false, length = 128)
    private String bucket;

    @Column(name = "object_key", nullable = false, columnDefinition = "TEXT")
    private String objectKey;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "meta_video_id")
    private String metaVideoId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

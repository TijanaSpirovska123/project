package com.example.marketing.adcreative.entity;

import com.example.marketing.asset.entity.StoredAssetEntity;
import com.example.marketing.asset.entity.StoredAssetVariantEntity;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.page.entity.PageEntity;
import com.example.marketing.user.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Entity
@Table(
        name = "ad_assets",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ad_assets_user_provider_account_hash",
                        columnNames = {"user_id", "provider", "ad_account_id", "hash"}
                )
        },
        indexes = {
                @Index(name = "idx_ad_assets_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_ad_assets_user_page", columnList = "user_id, page_id"),
                @Index(name = "idx_ad_assets_user_provider_account", columnList = "user_id, provider, ad_account_id")
        }
)
public class AdAssetEntity {

    public enum AssetType { IMAGE, VIDEO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hash", nullable = false, length = 255)
    private String hash;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private Provider provider;

    @Column(name = "ad_account_id", nullable = false, length = 50)
    private String adAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 16)
    private AssetType assetType;

    @Column(name = "mime_type", nullable = false, length = 64)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id")
    private PageEntity page;

    // Link back to internal library
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stored_asset_id", nullable = false)
    private StoredAssetEntity storedAsset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stored_variant_id", nullable = false)
    private StoredAssetVariantEntity storedVariant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

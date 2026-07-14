package com.example.marketing.adcreative.entity;

import com.example.marketing.asset.entity.StoredAssetEntity;
import com.example.marketing.asset.entity.StoredAssetVariantEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Table(
        name = "creative_assets",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_creative_assets_creative_variant_role",
                        columnNames = {"creative_id", "variant_id", "role"}
                )
        },
        indexes = {
                @Index(name = "idx_creative_assets_creative", columnList = "creative_id"),
                @Index(name = "idx_creative_assets_asset", columnList = "asset_id"),
                @Index(name = "idx_creative_assets_variant", columnList = "variant_id")
        }
)
public class CreativeAssetEntity {

    public enum Role {
        PRIMARY_IMAGE,
        VIDEO,
        THUMBNAIL,
        BACKGROUND,
        LOGO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Owning creative
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creative_id", nullable = false)
    private CreativeEntity creative;

    // The original asset (optional but useful for queries)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private StoredAssetEntity asset;

    // The concrete file used (variant)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private StoredAssetVariantEntity variant;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Role role;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
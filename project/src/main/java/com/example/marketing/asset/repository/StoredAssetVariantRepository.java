package com.example.marketing.asset.repository;

import com.example.marketing.asset.entity.StoredAssetVariantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoredAssetVariantRepository extends JpaRepository<StoredAssetVariantEntity, Long> {

    // Load variants for asset
    List<StoredAssetVariantEntity> findAllByAssetIdOrderByCreatedAtDesc(Long assetId);

    // Pick a specific variant (optional, but you will need it)
    Optional<StoredAssetVariantEntity> findByAssetIdAndVariantKey(Long assetId, String variantKey);

    // Useful guard if you allow "create variant" later
    boolean existsByAssetIdAndVariantKey(Long assetId, String variantKey);


}

package com.example.marketing.adcreative.repository;

import com.example.marketing.adcreative.entity.AdAssetEntity;
import com.example.marketing.asset.entity.StoredAssetVariantEntity;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.page.entity.PageEntity;
import com.example.marketing.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdAssetRepository extends JpaRepository<AdAssetEntity, Long> {

    // ✅ Correct dedup: same stored variant uploaded to same provider+adAccountId
    Optional<AdAssetEntity> findByUserAndProviderAndAdAccountIdAndStoredVariant(
            UserEntity user,
            Provider provider,
            String adAccountId,
            StoredAssetVariantEntity storedVariant
    );

    // fallback dedup by hash within ad account
    Optional<AdAssetEntity> findByUserAndProviderAndAdAccountIdAndHash(
            UserEntity user,
            Provider provider,
            String adAccountId,
            String hash
    );

    List<AdAssetEntity> findAllByUserOrderByCreatedAtDesc(UserEntity user);

    List<AdAssetEntity> findAllByUserAndPageOrderByCreatedAtDesc(UserEntity user, PageEntity page);

    // Used by MetaAssetSyncService fast-path: IMAGE ad-assets whose linked variant has no hash yet
    @Query("SELECT a FROM AdAssetEntity a WHERE a.user.id = :userId AND a.assetType = 'IMAGE' AND a.storedVariant.metaImageHash IS NULL")
    List<AdAssetEntity> findImageAssetsWithUnhashedVariant(@Param("userId") Long userId);

    // IMAGE ad-assets whose linked variant already has a hash — used to verify stale hashes
    @Query("SELECT a FROM AdAssetEntity a WHERE a.user.id = :userId AND a.assetType = 'IMAGE' AND a.storedVariant.metaImageHash IS NOT NULL")
    List<AdAssetEntity> findImageAssetsWithHashedVariant(@Param("userId") Long userId);
}
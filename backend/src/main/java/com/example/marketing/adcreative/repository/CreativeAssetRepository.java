package com.example.marketing.adcreative.repository;

import com.example.marketing.adcreative.entity.CreativeAssetEntity;
import com.example.marketing.adcreative.entity.CreativeEntity;
import com.example.marketing.asset.entity.StoredAssetEntity;
import com.example.marketing.asset.entity.StoredAssetVariantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreativeAssetRepository extends JpaRepository<CreativeAssetEntity, Long> {

    List<CreativeAssetEntity> findAllByCreativeId(Long creativeId);

    Optional<CreativeAssetEntity> findFirstByCreativeIdAndRole(Long creativeId, CreativeAssetEntity.Role role);

    List<CreativeAssetEntity> findAllByAsset(StoredAssetEntity asset);

    List<CreativeAssetEntity> findAllByVariant(StoredAssetVariantEntity variant);
}
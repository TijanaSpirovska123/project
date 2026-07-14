package com.example.marketing.asset.repository;

import com.example.marketing.asset.entity.StoredAssetEntity;
import com.example.marketing.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoredAssetRepository extends JpaRepository<StoredAssetEntity, Long> {

    // Most common list call (UI + API)
    List<StoredAssetEntity> findAllByUserOrderByCreatedAtDesc(UserEntity user);

    // Security: never allow reading someone else's asset by id
    Optional<StoredAssetEntity> findByIdAndUser(Long id, UserEntity user);

    Optional<StoredAssetEntity> findByUserAndHash(UserEntity user, String hash);

}

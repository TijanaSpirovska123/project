package com.example.marketing.ad.repository;

import com.example.marketing.ad.entity.AdEntity;
import com.example.marketing.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AdRepository extends JpaRepository<AdEntity,Long> {
    List<AdEntity> findByAdSetId(Long adSetId);
    List<AdEntity> findByUser(UserEntity user);
    List<AdEntity> findByUserAndPlatform(UserEntity user, String platform);
    List<AdEntity> findByUserAndPlatformAndAdAccountIdAndExternalIdIn(
            UserEntity user, String platform, String adAccountId, Collection<String> externalIds
    );

    java.util.Optional<AdEntity> findByUserAndPlatformAndExternalId(
            UserEntity user, String platform, String externalId
    );
}
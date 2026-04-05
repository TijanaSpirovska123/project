package com.example.marketing.adset.repository;

import com.example.marketing.adset.entity.AdSetEntity;
import com.example.marketing.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AdSetRepository extends JpaRepository<AdSetEntity,Long> {
    List<AdSetEntity> findByCampaignId(Long campaignId);
    List<AdSetEntity> findByUser(UserEntity user);
    List<AdSetEntity> findByUserAndPlatform(UserEntity user, String platform);
    List<AdSetEntity> findByUserAndPlatformAndAdAccountIdAndExternalIdIn(
            UserEntity user, String platform, String adAccountId, Collection<String> externalIds
    );
    Optional<AdSetEntity> findByUserAndPlatformAndAdAccountIdAndExternalId(
            UserEntity user, String platform, String adAccountId, String externalId
    );

    Optional<AdSetEntity> findByUserAndPlatformAndExternalId(
            UserEntity user, String platform, String externalId
    );
}
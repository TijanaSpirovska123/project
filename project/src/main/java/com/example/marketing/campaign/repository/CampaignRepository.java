package com.example.marketing.campaign.repository;

import com.example.marketing.campaign.entity.CampaignEntity;
import com.example.marketing.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<CampaignEntity, Long> {

    List<CampaignEntity> findByUser(UserEntity user);

    List<CampaignEntity> findByUserAndPlatform(UserEntity user, String platform);

    Optional<CampaignEntity> findByUserAndPlatformAndAdAccountIdAndExternalId(
            UserEntity user, String platform, String adAccountId, String externalId
    );

    List<CampaignEntity> findByUserAndPlatformAndAdAccountIdAndExternalIdIn(
            UserEntity user, String platform, String adAccountId, Collection<String> externalIds
    );

    Optional<CampaignEntity> findByUserAndPlatformAndExternalId(
            UserEntity user, String platform, String externalId
    );
}

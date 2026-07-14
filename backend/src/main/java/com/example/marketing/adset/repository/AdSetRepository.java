package com.example.marketing.adset.repository;

import com.example.marketing.adset.entity.AdSetEntity;
import com.example.marketing.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    @Modifying
    @Transactional
    @Query("DELETE FROM AdSetEntity a WHERE a.user.id = :userId AND a.platform = :platform AND a.adAccountId = :adAccountId")
    void deleteAllByUserIdAndPlatformAndAdAccountId(
            @Param("userId") Long userId,
            @Param("platform") String platform,
            @Param("adAccountId") String adAccountId);

    @Modifying
    @Transactional
    @Query("DELETE FROM AdSetEntity a WHERE a.user.id = :userId AND a.platform = :platform")
    void deleteAllByUserIdAndPlatform(
            @Param("userId") Long userId,
            @Param("platform") String platform);
}
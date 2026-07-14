package com.example.marketing.ad.repository;

import com.example.marketing.ad.entity.AdEntity;
import com.example.marketing.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    @Modifying
    @Transactional
    @Query("DELETE FROM AdEntity a WHERE a.user.id = :userId AND a.platform = :platform AND a.adAccountId = :adAccountId")
    void deleteAllByUserIdAndPlatformAndAdAccountId(
            @Param("userId") Long userId,
            @Param("platform") String platform,
            @Param("adAccountId") String adAccountId);

    @Modifying
    @Transactional
    @Query("DELETE FROM AdEntity a WHERE a.user.id = :userId AND a.platform = :platform")
    void deleteAllByUserIdAndPlatform(
            @Param("userId") Long userId,
            @Param("platform") String platform);
}
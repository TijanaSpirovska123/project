package com.example.marketing.adcreative.repository;

import com.example.marketing.adcreative.entity.CreativeEntity;
import com.example.marketing.page.entity.PagePostEntity;
import com.example.marketing.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CreativeRepository extends JpaRepository<CreativeEntity, Long> {
    Optional<CreativeEntity> findByPagePost(PagePostEntity pagePost);
    Optional<CreativeEntity> findByExternalId(String externalId);
    Optional<CreativeEntity> findByUserAndPlatformAndAdAccountIdAndExternalId(
            UserEntity user, String platform, String adAccountId, String externalId
    );
    List<CreativeEntity> findByUserAndPlatformAndAdAccountIdAndExternalIdIn(
            UserEntity user, String platform, String adAccountId, Collection<String> externalIds);
    List<CreativeEntity> findByUser(UserEntity user);


}

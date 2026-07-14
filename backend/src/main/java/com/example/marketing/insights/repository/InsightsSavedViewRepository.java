package com.example.marketing.insights.repository;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.insights.entity.InsightsSavedViewEntity;
import com.example.marketing.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InsightsSavedViewRepository extends JpaRepository<InsightsSavedViewEntity, Long> {

    List<InsightsSavedViewEntity> findByUserAndProviderOrderByPinnedDescUpdatedAtDesc(
            UserEntity user, Provider provider);

    Optional<InsightsSavedViewEntity> findByIdAndUser(Long id, UserEntity user);

    boolean existsByUserAndName(UserEntity user, String name);

    boolean existsByUserAndNameAndIdNot(UserEntity user, String name, Long id);
}

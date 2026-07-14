package com.example.marketing.oauth.repository;

import com.example.marketing.oauth.entity.OAuthAccountEntity;
import com.example.marketing.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OAuthAccountRepository extends JpaRepository<OAuthAccountEntity, Long> {

    Optional<OAuthAccountEntity> findByUserAndProvider(UserEntity user, String provider);

    Optional<OAuthAccountEntity> findByUserIdAndProvider(Long userId, String provider);

    boolean existsByUserIdAndProvider(Long userId, String provider);

    List<OAuthAccountEntity> findAllByUserId(Long userId);
}


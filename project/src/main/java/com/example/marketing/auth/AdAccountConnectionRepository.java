package com.example.marketing.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdAccountConnectionRepository
        extends JpaRepository<AdAccountConnectionEntity, Long> {

    List<AdAccountConnectionEntity> findAllByUserIdAndProvider(Long userId, String provider);

    List<AdAccountConnectionEntity> findAllByUserId(Long userId);

    List<AdAccountConnectionEntity> findAllByUserIdAndActive(Long userId, boolean active);

    List<AdAccountConnectionEntity> findAllByUserIdAndProviderAndActiveTrue(Long userId, String provider);

    Optional<AdAccountConnectionEntity> findByUserIdAndProviderAndAdAccountId(
            Long userId, String provider, String adAccountId
    );

    boolean existsByUserIdAndProviderAndAdAccountId(Long userId, String provider, String adAccountId);

    void deleteAllByUserIdAndProvider(Long userId, String provider);
}

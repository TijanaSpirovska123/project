package com.example.marketing.auth;

import com.example.marketing.oauth.entity.OAuthConnectRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OAuthConnectRequestRepository
        extends JpaRepository<OAuthConnectRequestEntity, String> {

    Optional<OAuthConnectRequestEntity> findByStateAndProvider(String state, String provider);

    List<OAuthConnectRequestEntity> findAllByExpiresAtBefore(LocalDateTime time);

    void deleteAllByExpiresAtBefore(LocalDateTime time);

    default Optional<OAuthConnectRequestEntity> findByState(String state) {
        return findById(state);
    }

    @Query("SELECT r FROM OAuthConnectRequestEntity r WHERE r.userId = :userId AND r.consumed = false AND r.expiresAt > :now")
    Optional<OAuthConnectRequestEntity> findPendingByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM OAuthConnectRequestEntity r WHERE r.expiresAt < :now")
    void deleteExpired(@Param("now") LocalDateTime now);
}

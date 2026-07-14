package com.example.marketing.user.repository;


import com.example.marketing.user.entity.PasswordResetToken;
import com.example.marketing.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Query("SELECT t FROM PasswordResetToken t WHERE t.user = :user AND t.token = :token")
    Optional<PasswordResetToken> findByUserAndToken(@Param("user") UserEntity user, @Param("token") String token);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.user = :user")
    void deleteByUser(@Param("user") UserEntity user);
}



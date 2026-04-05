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
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByUsername(String username);

    @Query("SELECT t FROM PasswordResetToken t WHERE t.user.email = :email AND t.token = :token")
    PasswordResetToken findByEmailAndToken(@Param("email") String email, @Param("token") String token);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.user.email = :email")
    void deleteByEmail(@Param("email") String email);
}

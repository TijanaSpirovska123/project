package com.example.marketing.userconfig.repository;

import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.userconfig.entity.UserConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserConfigRepository extends JpaRepository<UserConfigEntity, Long> {

    Optional<UserConfigEntity> findByUserAndConfigType(UserEntity user, String configType);
}

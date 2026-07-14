package com.example.marketing.page.repository;

import com.example.marketing.page.entity.PageEntity;
import com.example.marketing.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Long> {
    Optional<PageEntity> findByPageIdAndUser(String pageId, UserEntity user);
    List<PageEntity> findByUser(UserEntity user);
    Optional<PageEntity> findByNameAndUser(String name, UserEntity user);
}

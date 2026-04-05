package com.example.marketing.page.repository;

import com.example.marketing.page.entity.PageEntity;
import com.example.marketing.page.entity.PagePostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PagePostRepository extends JpaRepository<PagePostEntity, Long> {
    Optional<PagePostEntity> findByPostIdAndPage(String postId, PageEntity page);
    List<PagePostEntity> findByPage(PageEntity page);
    Optional<PagePostEntity> findByPostId(String postId);

    @Query("""
    SELECT pp FROM PagePostEntity pp
    JOIN FETCH pp.page p
    JOIN FETCH p.user u
    WHERE pp.postId = :postId
      AND p.name = :pageName
      AND u.id = :userId
""")
    Optional<PagePostEntity> findByPostIdAndPageNameAndUserId(
            @Param("postId") String postId,
            @Param("pageName") String pageName,
            @Param("userId") Long userId
    );

}
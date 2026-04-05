package com.example.marketing.insights.repository;

import com.example.marketing.insights.entity.InsightSnapshotEntity;
import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InsightSnapshotRepository extends JpaRepository<InsightSnapshotEntity, Long> {

        Optional<InsightSnapshotEntity> findByUserAndProviderAndAdAccountIdAndObjectTypeAndObjectExternalIdAndDateStartAndDateStopAndTimeIncrement(
                        UserEntity user,
                        Provider provider,
                        String adAccountId,
                        InsightObjectType objectType,
                        String objectExternalId,
                        LocalDate dateStart,
                        LocalDate dateStop,
                        Integer timeIncrement);

        List<InsightSnapshotEntity> findAllByUserAndProviderAndAdAccountIdAndObjectTypeAndObjectExternalIdAndDateStartGreaterThanEqualAndDateStopLessThanEqualOrderByDateStartAsc(
                        UserEntity user,
                        Provider provider,
                        String adAccountId,
                        InsightObjectType objectType,
                        String objectExternalId,
                        LocalDate dateStart,
                        LocalDate dateStop);

        // Add to InsightSnapshotRepository
        List<InsightSnapshotEntity> findAllByUserAndProviderAndAdAccountIdAndObjectTypeAndDateStartGreaterThanEqualAndDateStopLessThanEqualOrderByDateStartAsc(
                        UserEntity user, Provider provider, String adAccountId, InsightObjectType objectType,
                        LocalDate dateStart, LocalDate dateStop);

        @Query("SELECT s FROM InsightSnapshotEntity s WHERE " +
                        "s.user = :user AND " +
                        "s.provider = :provider AND " +
                        "s.adAccountId = :adAccountId AND " +
                        "s.objectType = :objectType AND " +
                        "s.objectExternalId = :objectExternalId AND " +
                        "s.dateStart <= :dateStop AND s.dateStop >= :dateStart")
        List<InsightSnapshotEntity> findByUserAndProviderAndAdAccountIdAndObjectTypeAndObjectExternalIdAndDateRange(
                        @Param("user") UserEntity user,
                        @Param("provider") Provider provider,
                        @Param("adAccountId") String adAccountId,
                        @Param("objectType") InsightObjectType objectType,
                        @Param("objectExternalId") String objectExternalId,
                        @Param("dateStart") LocalDate dateStart,
                        @Param("dateStop") LocalDate dateStop);

        // Query all objects of a type with date range overlap
        @Query("SELECT s FROM InsightSnapshotEntity s WHERE " +
                        "s.user = :user AND " +
                        "s.provider = :provider AND " +
                        "s.adAccountId = :adAccountId AND " +
                        "s.objectType = :objectType AND " +
                        "s.dateStart <= :dateStop AND s.dateStop >= :dateStart")
        List<InsightSnapshotEntity> findByUserAndProviderAndAdAccountIdAndObjectTypeAndDateRange(
                        @Param("user") UserEntity user,
                        @Param("provider") Provider provider,
                        @Param("adAccountId") String adAccountId,
                        @Param("objectType") InsightObjectType objectType,
                        @Param("dateStart") LocalDate dateStart,
                        @Param("dateStop") LocalDate dateStop);

        // Find by ID and user (for security)
        Optional<InsightSnapshotEntity> findByIdAndUser(Long id, UserEntity user);

        // ...existing code...

Optional<InsightSnapshotEntity> findByUserAndProviderAndAdAccountIdAndObjectTypeAndObjectExternalIdAndDateStartAndDateStop(
        UserEntity user, Provider provider, String adAccountId, InsightObjectType objectType,
        String objectExternalId, LocalDate dateStart, LocalDate dateStop);

}
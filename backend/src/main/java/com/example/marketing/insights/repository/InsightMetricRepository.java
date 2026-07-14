package com.example.marketing.insights.repository;

import com.example.marketing.insights.entity.InsightMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InsightMetricRepository extends JpaRepository<InsightMetricEntity, Long> {

    List<InsightMetricEntity> findAllBySnapshotId(Long snapshotId);

    @Query("select distinct m.name from InsightMetricEntity m order by m.name asc")
    List<String> findAllDistinctNames();

    @Query("SELECT DISTINCT m.name FROM InsightMetricEntity m ORDER BY m.name")
    List<String> findDistinctMetricNames();
}
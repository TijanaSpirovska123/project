package com.example.marketing.insights.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Table(
        name = "insight_metric",
        indexes = {
                @Index(name="idx_ins_metric_snapshot", columnList="snapshot_id"),
                @Index(name="idx_ins_metric_name", columnList="name")
        }
)
public class InsightMetricEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="snapshot_id", nullable = false)
    private InsightSnapshotEntity snapshot;

    @Column(name="name", nullable = false, length = 128)
    private String name;

    @Column(name="value_number", precision = 28, scale = 6)
    private BigDecimal valueNumber;

    @Column(name="value_text", columnDefinition = "TEXT")
    private String valueText;

    @Column(name="created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
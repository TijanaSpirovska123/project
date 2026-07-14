package com.example.marketing.insights.entity;

import com.example.marketing.insights.util.InsightObjectType;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.user.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Table(
        name = "insight_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ins_snapshot_unique",
                        columnNames = {
                                "user_id","provider","ad_account_id","object_type","object_external_id","date_start","date_stop","time_increment"
                        }
                )
        },
        indexes = {
                @Index(name = "idx_ins_snapshot_user_obj_range",
                        columnList = "user_id,provider,ad_account_id,object_type,object_external_id,date_start,date_stop")
        }
)
public class InsightSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name="provider", nullable = false, length = 32)
    private Provider provider;

    @Column(name="ad_account_id", nullable = false, length = 64)
    private String adAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name="object_type", nullable = false, length = 16)
    private InsightObjectType objectType;

    @Column(name="object_external_id", nullable = false, length = 64)
    private String objectExternalId;

    @Column(name="date_start", nullable = false)
    private LocalDate dateStart;

    @Column(name="date_stop", nullable = false)
    private LocalDate dateStop;

    @Column(name="time_increment", nullable = false)
    private Integer timeIncrement;

    // store JSONB without forcing you into a specific model
    @Column(name="breakdowns_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String breakdownsJson;

    @Column(name="raw_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawJson;

    @Column(name="created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name="updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
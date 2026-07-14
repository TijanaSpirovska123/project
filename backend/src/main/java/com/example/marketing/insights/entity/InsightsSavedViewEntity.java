package com.example.marketing.insights.entity;

import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.user.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Table(
    name = "insights_saved_view",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_insights_saved_view_user_name",
        columnNames = {"user_id", "name"}
    ),
    indexes = @Index(name = "idx_insights_saved_view_user", columnList = "user_id")
)
public class InsightsSavedViewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private Provider provider;

    @Column(name = "view_config", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String viewConfig;

    @Column(name = "pinned", nullable = false)
    private boolean pinned = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

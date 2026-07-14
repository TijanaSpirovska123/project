package com.example.marketing.auth;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ad_account_connection",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_provider_adaccount",
                columnNames = {"user_id","provider","ad_account_id"}
        )
)
@Data
public class AdAccountConnectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String provider; // "META"

    @Column(name="ad_account_id", nullable = false, length = 64)
    private String adAccountId; // "act_123..."

    // ✅ useful Meta fields
    @Column(name="ad_account_name", length = 255)
    private String adAccountName;

    @Column(name="account_id", length = 64)
    private String accountId;

    @Column(name="account_status")
    private Integer accountStatus;

    @Column(length = 10)
    private String currency;

    @Column(name="timezone_name", length = 64)
    private String timezoneName;

    @Column(nullable = true, length = 64)
    private String businessId;

    @Column(nullable = false)
    private boolean active = false;

    @Column(name = "last_synced")
    private LocalDateTime lastSynced;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

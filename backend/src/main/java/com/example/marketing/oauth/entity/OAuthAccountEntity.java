package com.example.marketing.oauth.entity;

import com.example.marketing.user.entity.UserEntity;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "oauth_accounts",
        uniqueConstraints = @UniqueConstraint(name="uq_user_provider", columnNames={"user_id","provider"})
)
@Data
public class OAuthAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false, length = 2048)
    private String accessToken;

    @Column(nullable = true)
    private LocalDateTime tokenExpiry;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TokenStatus tokenStatus = TokenStatus.VALID;

    @Column(nullable = false, length = 100)
    private String externalUserId;

    @Column(nullable = true, length = 2048)
    private String grantedScopes;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}


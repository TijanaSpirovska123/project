package com.example.marketing.oauth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "oauth_connect_requests")
@Data
public class OAuthConnectRequestEntity {

    @Id
    @Column(length = 128, nullable = false)
    private String state; // random UUID

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String provider; // META

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean consumed;

    @Column(nullable = false)
    private boolean completed;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

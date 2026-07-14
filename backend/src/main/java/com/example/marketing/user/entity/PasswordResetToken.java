package com.example.marketing.user.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "token")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    private String token;
    private LocalDateTime expiryDate;


     @ManyToOne(fetch = FetchType.LAZY)
     @JoinColumn(name = "user_id", nullable = false)
     private UserEntity user;


}


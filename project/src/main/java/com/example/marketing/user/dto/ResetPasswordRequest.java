package com.example.marketing.user.dto;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String email;
    private String token;
    private String newPassword;
}
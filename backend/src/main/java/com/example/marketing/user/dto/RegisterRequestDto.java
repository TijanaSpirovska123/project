package com.example.marketing.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequestDto {

    @NotBlank
    @Size(min = 1, max = 255)
    private String fullName;

    @NotBlank
    @Size(min = 1, max = 255)
    private String username;

    @NotBlank
    @Email
    @Size(min = 1, max = 255)
    private String email;

    @NotBlank
    @Size(min = 6, max = 255)
    private String password;
}

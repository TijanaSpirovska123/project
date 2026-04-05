package com.example.marketing.user.dto;

import com.example.marketing.user.util.Role;
import lombok.Data;

@Data
public class UserResponseDto {
    private Long id;
    private String fullName;
    private String username;
    private String email;
    private Role role;
}

package com.example.marketing.user.dto;

import lombok.Data;

@Data
public class LoginRequestDto {
  private String email;
  private String password;
  private String username;
}

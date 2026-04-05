package com.example.marketing.user.service;

import com.example.marketing.user.dto.RegisterRequestDto;
import com.example.marketing.user.dto.ResetPasswordRequest;
import com.example.marketing.user.dto.UserDto;
import com.example.marketing.user.dto.UserResponseDto;


import java.util.List;

public interface UserService {
  List<UserResponseDto> getAllUsers();

  String updatePassword(String email, String oldPassword, String newPassword);
  public void sendResetToken(String email);
  UserResponseDto register(RegisterRequestDto dto);
  public void resetPassword(ResetPasswordRequest resetPasswordRequest);
  public boolean verifyToken(ResetPasswordRequest resetPasswordRequest);
}

package com.example.marketing.user.service.impl;

import com.example.marketing.email.service.EmailService;
import com.example.marketing.exception.StatusErrorResponse;
import com.example.marketing.user.dto.RegisterRequestDto;
import com.example.marketing.user.dto.ResetPasswordRequest;
import com.example.marketing.user.dto.UserDto;
import com.example.marketing.user.dto.UserResponseDto;
import com.example.marketing.user.entity.PasswordResetToken;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.mapper.UserMapper;
import com.example.marketing.user.repository.PasswordResetTokenRepository;
import com.example.marketing.user.repository.UserRepository;
import com.example.marketing.user.service.UserService;
import com.example.marketing.user.util.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.marketing.exception.BusinessException.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final UserMapper userMapper;
  private final EmailService emailService;
  private final   PasswordEncoder passwordEncoder;

  public UserServiceImpl(UserRepository userRepository,
                         PasswordResetTokenRepository passwordResetTokenRepository,
                         UserMapper userMapper,
                         PasswordEncoder passwordEncoder,
                         EmailService emailService) {
    this.userRepository = userRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.emailService = emailService;
  }

  // ---- USER CRUD ----
  @Override
  @Transactional(readOnly = true)
  public List<UserResponseDto> getAllUsers() {
    return userMapper.convertToBaseDto(userRepository.findAll());
  }


  // ---- PASSWORD RESET LOGIC ----

  @Override
  @Transactional
  public String updatePassword(String email, String oldPassword, String newPassword) {
    UserEntity user = userRepository.findByEmail(email)
            .orElseThrow(() -> of(StatusErrorResponse.ENTITY_NOT_FOUND, "User not found."));
    validateUserAccount(user);

    if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
      throw of(StatusErrorResponse.INVALID_CREDENTIALS, "Old password is incorrect.");
    }

    user.setPassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);
    return "Successfully updated password";
  }


  @Override
  @Transactional
  public void sendResetToken(String email) {
    UserEntity user = userRepository.findByEmail(email)
            .orElseThrow(() -> of(StatusErrorResponse.ENTITY_NOT_FOUND, "No user found with the given email."));

    String token = UUID.randomUUID().toString();
    LocalDateTime expiry = LocalDateTime.now().plusMinutes(15);

    passwordResetTokenRepository.deleteByUser(user);

    PasswordResetToken resetToken = new PasswordResetToken();
    resetToken.setUser(user);
    resetToken.setToken(token);
    resetToken.setExpiryDate(expiry);
    passwordResetTokenRepository.save(resetToken);

    emailService.sendPasswordResetEmail(email, token);
  }

  @Override
  public boolean verifyToken(ResetPasswordRequest resetPasswordRequest) {
    UserEntity user = userRepository.findByEmail(resetPasswordRequest.getEmail())
            .orElseThrow(() -> of(StatusErrorResponse.ENTITY_NOT_FOUND, "No user found with the given email."));

    return passwordResetTokenRepository
            .findByUserAndToken(user, resetPasswordRequest.getToken())
            .filter(t -> t.getExpiryDate().isAfter(LocalDateTime.now()))
            .isPresent();
  }

  @Override
  @Transactional
  public void resetPassword(ResetPasswordRequest resetPasswordRequest) {
    UserEntity user = userRepository.findByEmail(resetPasswordRequest.getEmail())
            .orElseThrow(() ->of(StatusErrorResponse.ENTITY_NOT_FOUND, "No user found with the given email."));

    PasswordResetToken resetToken = passwordResetTokenRepository
            .findByUserAndToken(user, resetPasswordRequest.getToken())
            .filter(t -> t.getExpiryDate().isAfter(LocalDateTime.now()))
            .orElseThrow(() -> of(StatusErrorResponse.INVALID_CREDENTIALS, "Invalid or expired token"));

    user.setPassword(passwordEncoder.encode(resetPasswordRequest.getNewPassword()));
    userRepository.save(user);
    passwordResetTokenRepository.deleteByUser(user);
  }

  private void validateUserAccount(UserEntity user) {
    if (!user.isAccountNonExpired()) {
      throw of(StatusErrorResponse.ACCOUNT_EXPIRED, "User account has expired.");
    }
    if (!user.isCredentialsNonExpired()) {
      throw of(StatusErrorResponse.CREDENTIALS_EXPIRED, "User credentials have expired.");
    }
  }

  @Override
  @Transactional
  public UserResponseDto register(RegisterRequestDto dto) {

    if (userRepository.findByUsername(dto.getUsername()).isPresent()) {
      throw duplicate("Username already exists.");
    }

    if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
      throw duplicate("Email already exists.");
    }

    UserEntity user = new UserEntity();
    user.setFullName(dto.getFullName());
    user.setUsername(dto.getUsername());
    user.setEmail(dto.getEmail());
    user.setPassword(passwordEncoder.encode(dto.getPassword()));

    // IMPORTANT: never allow role from client
    user.setRole(Role.USER);

    UserEntity saved = userRepository.save(user);

    UserResponseDto response = new UserResponseDto();
    response.setId(saved.getId());
    response.setFullName(saved.getFullName());
    response.setUsername(saved.getUsername());
    response.setEmail(saved.getEmail());
    response.setRole(saved.getRole());

    return response;
  }
}

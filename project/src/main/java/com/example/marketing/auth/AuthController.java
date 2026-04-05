package com.example.marketing.auth;

import com.example.marketing.user.dto.RegisterRequestDto;
import com.example.marketing.user.dto.LoginRequestDto;
import com.example.marketing.user.dto.LoginResponseDto;
import com.example.marketing.user.dto.UserResponseDto;
import com.example.marketing.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationService authenticationService;

    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(@Valid @RequestBody RegisterRequestDto dto) {
        return ResponseEntity.ok(userService.register(dto));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }
}

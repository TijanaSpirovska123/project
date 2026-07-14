package com.example.marketing.auth;

import com.example.marketing.exception.StatusErrorResponse;
import com.example.marketing.user.dto.LoginRequestDto;
import com.example.marketing.user.dto.LoginResponseDto;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static com.example.marketing.exception.BusinessException.*;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdAccountConnectionRepository adAccountConnectionRepository;

    public LoginResponseDto login(LoginRequestDto request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> of(StatusErrorResponse.INVALID_CREDENTIALS, "Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw of(StatusErrorResponse.INVALID_CREDENTIALS, "Invalid email or password");
        }

        String token = jwtService.generateToken(
                user.getUsername(),
                user.getRole().name(), //USER OR ADMIN
                user.getId()
        );

        String actId = adAccountConnectionRepository
                .findAllByUserIdAndProviderAndActiveTrue(user.getId(), "META")
                .stream()
                .map(AdAccountConnectionEntity::getAdAccountId)
                .findFirst()
                .orElse(null);

        // Prepare response
        LoginResponseDto response = new LoginResponseDto();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setRole(user.getRole().name());
        response.setActId(actId);

        return response;
    }
}
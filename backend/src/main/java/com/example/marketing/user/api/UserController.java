package com.example.marketing.user.api;
import com.example.marketing.infrastructure.api.BaseController;
import com.example.marketing.user.dto.ResetPasswordRequest;
import com.example.marketing.user.dto.TokenRequest;
import com.example.marketing.user.dto.UserDto;
import com.example.marketing.user.dto.UserResponseDto;
import com.example.marketing.user.service.UserService;
import com.example.marketing.userconfig.dto.ColumnConfigDto;
import com.example.marketing.userconfig.dto.InsightsConfigDto;
import com.example.marketing.userconfig.dto.ThemeConfigDto;
import com.example.marketing.userconfig.service.UserConfigService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController extends BaseController {

    private final UserService userService;
    private final UserConfigService userConfigService;

    public UserController(UserService userService, UserConfigService userConfigService) {
        this.userService = userService;
        this.userConfigService = userConfigService;
    }

    @GetMapping()
    public BaseResponse<List<UserResponseDto>> getAllUsers() {
        List<UserResponseDto> usersDto = userService.getAllUsers();
        return ok(usersDto);
    }

    @PutMapping()
    public  BaseResponse<String> updateUser(@Valid @RequestBody UserDto userDto,@RequestParam String newPassword){
        return ok(userService.updatePassword(userDto.getEmail(), userDto.getPassword(), newPassword));
    }

    @PostMapping("/request-password-reset")
    public BaseResponse<String> sendResetToken(@RequestBody TokenRequest tokenRequest) {
        userService.sendResetToken(tokenRequest.getEmail());
        return ok("Password reset token sent to email.");
    }

    @PostMapping("/reset-password")
    public BaseResponse<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        boolean isValid = userService.verifyToken(request);
        if (isValid) {
            userService.resetPassword(request);
            return ok("Password has been reset successfully.");
        }
        return notFound("Password has not been reset.");
    }

    @GetMapping("/insights-config")
    public BaseResponse<InsightsConfigDto> getInsightsConfig(Authentication auth) {
        Long userId = extractUserId(auth);
        return ok(userConfigService.getInsightsConfig(userId));
    }

    @PatchMapping("/insights-config")
    public BaseResponse<InsightsConfigDto> saveInsightsConfig(Authentication auth,
                                                               @RequestBody InsightsConfigDto dto) {
        Long userId = extractUserId(auth);
        return ok(userConfigService.saveInsightsConfig(userId, dto));
    }

    @GetMapping("/column-config")
    public BaseResponse<ColumnConfigDto> getColumnConfig(Authentication auth,
                                                          @RequestParam String entityType) {
        Long userId = extractUserId(auth);
        return ok(userConfigService.getColumnConfig(userId, entityType));
    }

    @PatchMapping("/column-config")
    public BaseResponse<ColumnConfigDto> saveColumnConfig(Authentication auth,
                                                           @RequestBody ColumnConfigDto dto) {
        Long userId = extractUserId(auth);
        return ok(userConfigService.saveColumnConfig(userId, dto));
    }

    @GetMapping("/theme-config")
    public BaseResponse<ThemeConfigDto> getThemeConfig(Authentication auth) {
        Long userId = extractUserId(auth);
        return ok(userConfigService.getThemeConfig(userId));
    }

    @PatchMapping("/theme-config")
    public BaseResponse<ThemeConfigDto> saveThemeConfig(Authentication auth,
                                                         @RequestBody ThemeConfigDto dto) {
        Long userId = extractUserId(auth);
        return ok(userConfigService.saveThemeConfig(userId, dto));
    }
}
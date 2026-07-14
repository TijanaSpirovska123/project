package com.example.marketing.user.dto;

import com.example.marketing.user.util.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

import static com.example.marketing.user.util.UserUtil.*;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class UserDto {
    private Long id;

    @Size(min = 1, max = 255, message = FIRST_NAME_MESSAGE)
    private String fullName;

    @Size(min = 1, max = 255, message = USERNAME_MESSAGE)
    private String username;

    @Size(min = 1, max = 255, message = EMAIL_MESSAGE)
    private String email;

    private Integer phoneNumber;

    @Size(min = 1, max = 255, message = ADDRESS_MESSAGE)
    private String address;

    private String password;

    private LocalDate accountExpiredDate;
    private Boolean accountLocked;
    private LocalDate credentialsExpiredDate;
    private Boolean enabled;

    private Role role;
}

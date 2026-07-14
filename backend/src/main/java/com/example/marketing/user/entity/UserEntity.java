package com.example.marketing.user.entity;

import com.example.marketing.adcreative.entity.AdAssetEntity;
import com.example.marketing.user.util.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
@Entity
@Table(name = "USERS")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity implements Serializable, UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "FULL_NAME", nullable = false)
    private String fullName;

    @Column(name = "USERNAME", unique = true, length = 255, nullable = false)
    private String username;

    @Column(name = "EMAIL", unique = true, length = 255, nullable = false)
    private String email;

    @Column(name = "PHONE_NUMBER")
    private Integer phoneNumber;

    @Column(name = "ADDRESS", length = 255)
    private String address;

    @Column(name = "PASSWORD", length = 255, nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false)
    private Role role = Role.USER; // Default role

    @OneToMany(mappedBy = "user")
    private List<AdAssetEntity> adAssets = new ArrayList<>();



    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PasswordResetToken> passwordResetTokens = new ArrayList<>();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

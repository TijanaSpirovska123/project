package com.example.marketing.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long jwtExpirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.expiration}") long jwtExpirationMs
    ) {
        this.jwtExpirationMs = jwtExpirationMs;

        // ✅ BEST PRACTICE: use Base64 secret (recommended)
        // If you are NOT using base64, replace Decoders.BASE64.decode(secretKey) with secretKey.getBytes(StandardCharsets.UTF_8)
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretKey);
        } catch (Exception e) {
            // fallback to raw string if not base64
            keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        }

        // ✅ enforce minimum key length (HS512 needs a strong key)
        if (keyBytes.length < 64) {
            throw new IllegalStateException("JWT secret is too short. Use at least 64 bytes for HS512.");
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String username, String role, Long userId) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)      // "USER" or "ADMIN"
                .claim("userId", userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public Long extractUserId(String token) {
        return getClaims(token).get("userId", Long.class);
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

}

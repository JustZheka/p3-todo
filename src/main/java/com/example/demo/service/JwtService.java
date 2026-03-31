package com.example.demo.service;

import com.example.demo.model.RevokedAccessToken;
import com.example.demo.repository.RevokedAccessTokenRepository;
import com.example.demo.utils.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.val;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true)
public class JwtService {

    JwtProperties props;
    RevokedAccessTokenRepository revokedAccessTokenRepository;

    @NonFinal
    SecretKey key;

    @NonFinal
    Duration accessTtl;

    @Getter
    @NonFinal
    Duration refreshTtl;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(
            props.getSecret().getBytes(StandardCharsets.UTF_8)
        );
        this.accessTtl = Duration.ofMinutes(props.getAccessExpirationMinutes());
        this.refreshTtl = Duration.ofDays(props.getRefreshExpirationDays());
    }

    public String generateAccessToken(
        final String username,
        final Collection<String> roles
    ) {
        val now = Instant.now();
        val normalizedRoles =
            roles == null ? List.<String>of() : List.copyOf(roles);
        return Jwts.builder()
            .subject(username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTtl)))
            .claim("typ", "access")
            .claim("roles", normalizedRoles)
            .signWith(key)
            .compact();
    }

    public String generateRefreshToken(
        final String username,
        final Collection<String> roles
    ) {
        val now = Instant.now();
        val normalizedRoles =
            roles == null ? List.<String>of() : List.copyOf(roles);
        return Jwts.builder()
            .subject(username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(refreshTtl)))
            .claim("typ", "refresh")
            .claim("roles", normalizedRoles)
            .signWith(key)
            .compact();
    }

    public boolean isTokenValid(final String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (final JwtException thrown) {
            return false;
        }
    }

    public String extractUsername(final String token) {
        val jwt = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token);
        return jwt.getPayload().getSubject();
    }

    public List<String> extractRoles(final String token) {
        try {
            val jwt = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
            val rawRoles = jwt.getPayload().get("roles");
            if (!(rawRoles instanceof List<?> roles)) {
                return List.of();
            }
            return roles
                .stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
        } catch (final JwtException thrown) {
            return List.of();
        }
    }

    public boolean isRefreshToken(final String token) {
        try {
            val jwt = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
            val typ = jwt.getPayload().get("typ");
            return "refresh".equals(typ);
        } catch (final JwtException thrown) {
            return false;
        }
    }

    public void revokeAccessToken(final String token) {
        try {
            val jwt = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
            val exp = jwt.getPayload().getExpiration();
            if (exp != null) {
                revokedAccessTokenRepository.save(
                    RevokedAccessToken.builder()
                        .tokenHash(hashToken(token))
                        .expiry(exp.toInstant())
                        .build()
                );
            }
        } catch (final JwtException ignored) {}
    }

    public boolean isAccessRevoked(final String token) {
        revokedAccessTokenRepository.deleteByExpiryBefore(Instant.now());
        if (!revokedAccessTokenRepository.existsById(hashToken(token))) {
            return false;
        }
        return true;
    }

    private String hashToken(final String token) {
        try {
            val digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                digest.digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (final NoSuchAlgorithmException thrown) {
            throw new IllegalStateException(
                "Не удалось вычислить хэш токена",
                thrown
            );
        }
    }
}

package com.example.demo.service;

import com.example.demo.utils.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.val;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true)
public class JwtService {
    JwtProperties props;

    @NonFinal
    SecretKey key;

    @NonFinal
    Duration accessTtl;

    @Getter
    @NonFinal
    Duration refreshTtl;

    Map<String, Instant> revokedAccess = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(props.getAccessExpirationMinutes());
        this.refreshTtl = Duration.ofDays(props.getRefreshExpirationDays());
    }

    public String generateAccessToken(final String username) {
        val now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .claim("typ", "access")
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(final String username) {
        val now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl)))
                .claim("typ", "refresh")
                .signWith(key)
                .compact();
    }

    public boolean isTokenValid(final String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
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
                revokedAccess.put(token, exp.toInstant());
            }
        } catch (final JwtException ignored) {
        }
    }

    public boolean isAccessRevoked(final String token) {
        val exp = revokedAccess.get(token);
        if (exp == null) {
            return false;
        }
        if (exp.isBefore(Instant.now())) {
            revokedAccess.remove(token);
            return false;
        }
        return true;
    }
}

package com.example.demo.service;

import com.example.demo.utils.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
@Data
public class JwtService {

    private final JwtProperties props;
    private SecretKey key;
    private Duration accessTtl;
    private Duration refreshTtl;

    public JwtService(JwtProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(props.getAccessExpirationMinutes());
        this.refreshTtl = Duration.ofDays(props.getRefreshExpirationDays());
    }

    public String generateAccessToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .claim("typ", "access")
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl)))
                .claim("typ", "refresh")
                .signWith(key)
                .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        var jwt = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
        return jwt.getPayload().getSubject();
    }

    public boolean isRefreshToken(String token) {
        var jwt = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        Object typ = jwt.getPayload().get("typ");
        return "refresh".equals(typ);
    }
}
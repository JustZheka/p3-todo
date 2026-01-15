package com.example.demo.service;

import com.example.demo.model.RefreshToken;
import com.example.demo.repository.RefreshTokenRepository;
import lombok.val;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true)
@Slf4j
public class RefreshTokenService {
    RefreshTokenRepository repo;
    JwtService jwtService;

    public RefreshToken createOrReplace(final String username) {
        val newToken = jwtService.generateRefreshToken(username);
        val newExpiry = Instant.now().plus(jwtService.getRefreshTtl());

        val maybeExisting = repo.findByUsername(username);

        if (maybeExisting.isPresent()) {
            val existing = maybeExisting.get();
            existing.setToken(newToken);
            existing.setExpiry(newExpiry);
            existing.setRevoked(false);
            return repo.save(existing);
        }

        val refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .token(newToken)
                .expiry(newExpiry)
                .revoked(false)
                .build();
        return repo.save(refreshToken);
    }

    public RefreshToken rotate(final String username, final String oldToken) {
        repo.findByToken(oldToken).ifPresent(refreshToken -> {
            refreshToken.setRevoked(true);
            repo.save(refreshToken);
        });

        return createOrReplace(username);
    }

    public Optional<RefreshToken> findValid(final String token) {
        return repo.findByToken(token)
                .filter(refreshToken -> !refreshToken.isRevoked())
                .filter(refreshToken -> refreshToken.getExpiry().isAfter(Instant.now()))
                .filter(refreshToken -> jwtService.isTokenValid(token))
                .filter(refreshToken -> jwtService.isRefreshToken(token));
    }

    public void revoke(final String token) {
        repo.findByToken(token).ifPresent(refreshToken -> {
            refreshToken.setRevoked(true);
            repo.save(refreshToken);
        });
    }
}

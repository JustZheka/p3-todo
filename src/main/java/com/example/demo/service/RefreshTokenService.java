package com.example.demo.service;

import com.example.demo.model.RefreshToken;
import com.example.demo.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true)
@Slf4j
public class RefreshTokenService {

    RefreshTokenRepository repo;
    JwtService jwtService;

    public RefreshToken createOrReplace(
        final String username,
        final List<String> roles
    ) {
        val newToken = jwtService.generateRefreshToken(username, roles);
        val newTokenHash = hashToken(newToken);
        val newExpiry = Instant.now().plus(jwtService.getRefreshTtl());

        val maybeExisting = repo.findByUsername(username);

        if (maybeExisting.isPresent()) {
            val existing = maybeExisting.get();
            existing.setTokenHash(newTokenHash);
            existing.setExpiry(newExpiry);
            existing.setRevoked(false);
            existing.setToken(newToken);
            return repo.save(existing);
        }

        val refreshToken = RefreshToken.builder()
            .id(UUID.randomUUID().toString())
            .username(username)
            .tokenHash(newTokenHash)
            .token(newToken)
            .expiry(newExpiry)
            .revoked(false)
            .build();
        return repo.save(refreshToken);
    }

    public RefreshToken rotate(
        final String username,
        final String oldToken,
        final List<String> roles
    ) {
        repo
            .findByTokenHashOrTokenHash(hashToken(oldToken), oldToken)
            .ifPresent(refreshToken -> {
                refreshToken.setRevoked(true);
                repo.save(refreshToken);
            });

        return createOrReplace(username, roles);
    }

    public Optional<RefreshToken> findValid(final String token) {
        return repo
            .findByTokenHashOrTokenHash(hashToken(token), token)
            .filter(refreshToken -> !refreshToken.isRevoked())
            .filter(refreshToken ->
                refreshToken.getExpiry().isAfter(Instant.now())
            )
            .filter(refreshToken -> jwtService.isTokenValid(token))
            .filter(refreshToken -> jwtService.isRefreshToken(token));
    }

    public void revoke(final String token) {
        repo
            .findByTokenHashOrTokenHash(hashToken(token), token)
            .ifPresent(refreshToken -> {
                refreshToken.setRevoked(true);
                repo.save(refreshToken);
            });
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

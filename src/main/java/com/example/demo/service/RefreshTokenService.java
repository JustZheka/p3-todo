package com.example.demo.service;

import com.example.demo.model.RefreshToken;
import com.example.demo.repository.RefreshTokenRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private final JwtService jwtService;

    public RefreshTokenService(RefreshTokenRepository repo, JwtService jwtService) {
        this.repo = repo;
        this.jwtService = jwtService;
    }

    @Transactional
    public RefreshToken createOrReplace(String username) {
        String newToken = jwtService.generateRefreshToken(username);
        Instant newExpiry = Instant.now().plus(jwtService.getRefreshTtl());

        Optional<RefreshToken> maybeExisting = repo.findByUsername(username);

        if (maybeExisting.isPresent()) {
            RefreshToken existing = maybeExisting.get();
            existing.setToken(newToken);
            existing.setExpiry(newExpiry);
            existing.setRevoked(false);
            return repo.save(existing); // обновление по существующему id
        } else {
            RefreshToken rt = new RefreshToken();
            rt.setId(UUID.randomUUID().toString());
            rt.setUsername(username);
            rt.setToken(newToken);
            rt.setExpiry(newExpiry);
            rt.setRevoked(false);
            return repo.save(rt); // создаём новую запись
        }
    }

    @Transactional
    public RefreshToken rotate(String username, String oldToken) {
        // старый токен помечаем как отозванный
        repo.findByToken(oldToken).ifPresent(rt -> {
            rt.setRevoked(true);
            repo.save(rt);
        });

        // создаём новый refresh-токен (или обновляем существующую запись)
        return createOrReplace(username);
    }

    public Optional<RefreshToken> findValid(String token) {
        return repo.findByToken(token)
                .filter(rt -> !rt.isRevoked())
                .filter(rt -> rt.getExpiry().isAfter(Instant.now()))
                .filter(rt -> jwtService.isTokenValid(token))
                .filter(rt -> jwtService.isRefreshToken(token));
    }

    public void revoke(String token) {
        repo.findByToken(token).ifPresent(r -> {
            r.setRevoked(true);
            repo.save(r);
        });
    }
}

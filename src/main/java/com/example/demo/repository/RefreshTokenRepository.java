package com.example.demo.repository;

import com.example.demo.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findByToken(final String token);
    List<RefreshToken> findAllByUsernameAndRevokedFalse(final String username);
    Optional<RefreshToken> findByUsername(final String username);
}

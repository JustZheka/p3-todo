package com.example.demo.repository;

import com.example.demo.model.RefreshToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository
    extends JpaRepository<RefreshToken, String>
{
    Optional<RefreshToken> findByTokenHash(final String tokenHash);

    Optional<RefreshToken> findByTokenHashOrTokenHash(
        final String tokenHash,
        final String legacyToken
    );

    List<RefreshToken> findAllByUsernameAndRevokedFalse(final String username);

    Optional<RefreshToken> findByUsername(final String username);
}

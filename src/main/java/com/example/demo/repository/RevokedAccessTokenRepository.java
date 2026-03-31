package com.example.demo.repository;

import com.example.demo.model.RevokedAccessToken;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevokedAccessTokenRepository
    extends JpaRepository<RevokedAccessToken, String> {

    void deleteByExpiryBefore(Instant expiry);
}

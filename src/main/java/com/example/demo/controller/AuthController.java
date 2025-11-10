package com.example.demo.controller;

import com.example.demo.dto.LoginRequest;
import com.example.demo.model.RefreshToken;
import com.example.demo.service.JwtService;
import com.example.demo.service.RefreshTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthenticationManager authManager,
                          JwtService jwtService,
                          RefreshTokenService refreshTokenService) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword())
            );

            String access = jwtService.generateAccessToken(req.getUsername());
            RefreshToken saved = refreshTokenService.createOrReplace(req.getUsername());

            return ResponseEntity.ok(Map.of(
                    "accessToken", access,
                    "refreshToken", saved.getToken()
            ));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refresh = body.get("refreshToken");
        if (refresh == null || refresh.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "refreshToken required"));
        }

        Optional<RefreshToken> stored = refreshTokenService.findValid(refresh);
        if (stored.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired refresh token"));
        }

        String username = jwtService.extractUsername(refresh);

        // Проверяем, что refresh реально из базы
        if (!stored.get().getUsername().equals(username)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Refresh token mismatch"));
        }

        // Генерируем новый access token
        String newAccess = jwtService.generateAccessToken(username);

        // По желанию можно также обновить refresh token
        RefreshToken newRefresh = refreshTokenService.rotate(username, refresh);

        return ResponseEntity.ok(Map.of(
                "accessToken", newAccess,
                "refreshToken", newRefresh.getToken()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> body) {
        String refresh = body.get("refreshToken");
        if (refresh != null) refreshTokenService.revoke(refresh);
        return ResponseEntity.ok(Map.of("status", "logged out"));
    }
}
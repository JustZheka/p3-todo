package com.example.demo.controller;

import com.example.demo.dto.LoginRequest;
import com.example.demo.service.JwtService;
import com.example.demo.service.RefreshTokenService;
import lombok.val;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true)
@Slf4j
public class AuthController {
    AuthenticationManager authManager;
    JwtService jwtService;
    RefreshTokenService refreshTokenService;

    @PostMapping("/login")
    public ResponseEntity<?> login(final @RequestBody LoginRequest req) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password())
            );

            val access = jwtService.generateAccessToken(req.username());
            val saved = refreshTokenService.createOrReplace(req.username());

            val cookie = ResponseCookie.from("refreshToken", saved.getToken())
                    .httpOnly(true)
                    .secure(true)
                    .path("/auth")
                    .maxAge(jwtService.getRefreshTtl())
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(Map.of("accessToken", access));
        } catch (final AuthenticationException thrown) {
            if (log.isWarnEnabled()) {
                log.warn("Ошибка аутентификации пользователя: {}", req.username());
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Неверные учетные данные"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(final @CookieValue(name = "refreshToken", required = false) String refresh) {
        if (StringUtils.isBlank(refresh)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "refreshToken обязателен"));
        }

        val stored = refreshTokenService.findValid(refresh);
        if (stored.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Неверный или просроченный refresh token"));
        }

        val username = jwtService.extractUsername(refresh);

        if (!stored.get().getUsername().equals(username)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Несовпадение refresh token"));
        }

        val newRefresh = refreshTokenService.rotate(username, refresh).getToken();
        val cookie = ResponseCookie.from("refreshToken", newRefresh)
                .httpOnly(true)
                .secure(true)
                .path("/auth")
                .maxAge(jwtService.getRefreshTtl())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("accessToken", jwtService.generateAccessToken(username)));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            final HttpServletRequest request,
            final @CookieValue(name = "refreshToken", required = false) String refresh) {
        
        val authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String username = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val accessToken = authHeader.substring(7);
            if (jwtService.isTokenValid(accessToken) && !jwtService.isRefreshToken(accessToken)) {
                username = jwtService.extractUsername(accessToken);
                jwtService.revokeAccessToken(accessToken);
            }
        }
        
        if (refresh != null && jwtService.isTokenValid(refresh) && jwtService.isRefreshToken(refresh)) {
            if (username != null && !jwtService.extractUsername(refresh).equals(username)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Несовпадение refresh token"));
            }
            refreshTokenService.revoke(refresh);
        }
        
        val cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/auth")
                .maxAge(0)
                .build();
                
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("status", "выход выполнен"));
    }
}

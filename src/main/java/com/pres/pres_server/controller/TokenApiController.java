package com.pres.pres_server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.Token.CreateAccessTokenRequest;
import com.pres.pres_server.dto.Token.CreateAccessTokenResponse;
import com.pres.pres_server.service.token.TokenService;
import java.time.Duration;
import com.pres.pres_server.security.jwt.TokenProvider;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
public class TokenApiController {
    private final TokenService tokenService;
    private final TokenProvider tokenProvider;

    // 테스트용 JWT 토큰 발급 API
    @PostMapping("/test-token")
    public ResponseEntity<String> generateTestToken(
            @RequestParam String email,
            @RequestParam Long id) {
        User testUser = User.builder()
                .id(id)
                .email(email)
                .build();
        String token = tokenProvider.generateToken(testUser, Duration.ofDays(120)); // 120일(약 4개월)짜리 토큰 발급
        return ResponseEntity.ok(token);
    }

}

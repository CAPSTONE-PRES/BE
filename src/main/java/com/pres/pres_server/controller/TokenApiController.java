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
import com.pres.pres_server.service.user.UserService;
import java.time.Duration;
import com.pres.pres_server.security.jwt.TokenProvider;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
public class TokenApiController {
    private final TokenService tokenService;
    private final TokenProvider tokenProvider;
    private final UserService userService;

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

    // 개발/테스트용 짧은 만료 토큰 발급 API (기본 3분)
    @PostMapping("/test-token-short")
    public ResponseEntity<String> generateShortTestToken(
            @RequestParam String email,
            @RequestParam(required = false, defaultValue = "3") Long minutes) {
        // DB에서 사용자 존재 확인 후 해당 User 엔티티로 토큰 발급 (비밀번호 검증 없음, 개발/테스트용)
        try {
            User user = userService.findByEmail(email);
            String token = tokenProvider.generateToken(user, Duration.ofMinutes(minutes)); // minutes 분 유효
            return ResponseEntity.ok(token);
        } catch (IllegalArgumentException e) {
            // 사용자 미존재 또는 조회 오류인 경우 테스트용으로 400과 명시 메시지 반환
            return ResponseEntity.badRequest().body("User not found or invalid: " + email);
        }
    }

}

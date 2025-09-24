package com.pres.pres_server.service.token;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.pres.pres_server.domain.RefreshToken;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.security.jwt.TokenProvider;
import com.pres.pres_server.service.user.UserService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class TokenService {

    private final TokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    public String createAccessToken(String refreshToken) {
        // 토큰 유효성 검사에 실패하면 예외 발생
        if (!tokenProvider.validToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        // 유효한 토큰이면, 리프레시 토큰으로 사용자 id 찾음
        Long userId = refreshTokenService.findByRefreshToken(refreshToken).getUserId();
        User user = userService.findById(userId);
        // 사용자 id로 액세스 토큰 생성 (2시간 유효)
        return tokenProvider.generateToken(user, Duration.ofHours(2));
    }

    public String createRefreshToken(User user) {
        // 사용자 id로 리프레시 토큰 생성 (14일 유효)
        String refreshToken = tokenProvider.generateToken(user, Duration.ofDays(14));
        // DB에 리프레시 토큰 저장
        refreshTokenService.save(new RefreshToken(user.getId(), refreshToken));
        return refreshToken;
    }

    // JWT 리프레시 토큰 무효화 (예: DB에서 삭제)
    public void invalidateRefreshToken(String refreshToken) {
        refreshTokenService.deleteByRefreshToken(refreshToken);
    }
}

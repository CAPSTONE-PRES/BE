package com.pres.pres_server.service.token;

import org.springframework.stereotype.Service;

import com.pres.pres_server.domain.RefreshToken;
import com.pres.pres_server.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;

    // 리프레시 토큰으로 DB에서 리프레시 토큰 엔티티 찾기
    public RefreshToken findByRefreshToken(String refreshToken) {
        return refreshTokenRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Unexpected refresh token"));
    }

    // 리프레시 토큰 저장
    public void save(RefreshToken refreshToken) {
        refreshTokenRepository.save(refreshToken);
    }

    // 리프레시 토큰 저장 또는 업데이트 (기존 토큰이 있으면 업데이트)
    public void saveOrUpdate(Long userId, String newRefreshToken) {
        RefreshToken existingToken = refreshTokenRepository.findByUserId(userId).orElse(null);

        if (existingToken != null) {
            // 기존 토큰이 있으면 업데이트
            existingToken.update(newRefreshToken);
            refreshTokenRepository.save(existingToken);
        } else {
            // 기존 토큰이 없으면 새로 생성
            refreshTokenRepository.save(new RefreshToken(userId, newRefreshToken));
        }
    }

    // 리프레시 토큰 삭제
    public void deleteByRefreshToken(String refreshToken) {
        refreshTokenRepository.findByRefreshToken(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }

    // DB에서 카카오 액세스 토큰을 가져오는 메서드
    public String getKakaoAccessTokenFromDB(String refreshToken) {
        // 리프레시 토큰에 해당하는 사용자를 찾고,
        // 해당 사용자의 카카오 액세스 토큰을 DB에서 가져오는 로직
        return "가상_카카오_액세스_토큰";
    }
}
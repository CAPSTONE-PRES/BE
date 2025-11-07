package com.pres.pres_server.dto.User;

import com.pres.pres_server.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사용자 정보 응답 DTO
 * (보안을 위해 User 엔티티 직접 노출 방지)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDto {

    /**
     * 사용자 ID
     */
    private Long id;

    /**
     * 이메일
     */
    private String email;

    /**
     * 사용자명
     */
    private String username;

    /**
     * 이메일 인증 여부
     */
    private boolean emailVerified;

    /**
     * 프로필 이미지 URL (선택)
     */
    private String profileUrl;

    /**
     * User 엔티티를 DTO로 변환하는 정적 팩토리 메서드
     */
    public static UserResponseDto of(User user, String resolvedProfileUrl) {
        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .emailVerified(user.isEmailVerified())
                .profileUrl(resolvedProfileUrl)
                .build();
    }
}

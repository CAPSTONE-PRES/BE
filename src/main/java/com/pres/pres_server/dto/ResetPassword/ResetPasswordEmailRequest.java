package com.pres.pres_server.dto.ResetPassword;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
// 이메일 요청 DTO
// 비밀번호 재설정 시 사용
public class ResetPasswordEmailRequest {
    private String email;
}

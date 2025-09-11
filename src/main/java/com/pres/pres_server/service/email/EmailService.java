package com.pres.pres_server.service;

import org.springframework.stereotype.Service;

//인증 코드 생성·전송 로직
//코드 저장·검증 로직 (Redis나 DB)
//역할: 이메일 발송·인증 비즈니스 로직 처리
//구성: sendCode(email), verifyCode(email, code) 같은 메서드
//로직: SMTP 연동, 코드 생성/저장/검증, 예외 처리 등

@Service
public interface EmailService {
    void sendCode(String email);
    boolean verifyCode(String email, String code);
    boolean isVerified(String email);

}

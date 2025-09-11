package com.pres.pres_server.service;

import com.pres.pres_server.dto.SignupDto;
import org.springframework.stereotype.Service;

//회원가입 로직
//EmailService가 “인증됐는지”만 알려주면, 나머지 “가입 처리”는 별도의 서비스가 담당해야 한다.
@Service
public interface UserAuthService {
    void signup(SignupDto dto); //이메일 검증 및 저장
    boolean emailExists(String email); //이메일 중복 체크
}

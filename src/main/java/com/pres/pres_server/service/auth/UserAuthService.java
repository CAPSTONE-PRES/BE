package com.pres.pres_server.service.auth;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.KakaoUserInfo;
import com.pres.pres_server.dto.Signup.SignupRequest;
import com.pres.pres_server.repository.UserRepository;
import com.pres.pres_server.service.email.EmailService;
import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

//회원가입 로직
//EmailService가 “인증됐는지”만 알려주면, 나머지 “가입 처리”는 UserAuthService가 담당

@RequiredArgsConstructor // final이 붙거나 @NotNull이 붙은 필드의 생성자 추가
@Service // 빈으로 추가
public class UserAuthService {

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // 회원가입
    public void signup(SignupRequest dto) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        if (!emailService.isVerified(dto.getEmail())) {
            throw new IllegalStateException("이메일 인증이 완료되지 않았습니다. 인증 후 다시 시도해주세요.");
        }
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new IllegalStateException("이미 가입된 이메일입니다.");
        }
        if (!dto.getPassword().equals(dto.getPasswordConfirm())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        if (dto.getPassword().length() < 6) {
            throw new IllegalArgumentException("비밀번호가 너무 짧습니다.");
        }
        
        User user = User.builder()
                .email(dto.getEmail())
                .password(encoder.encode(dto.getPassword()))
                .username(dto.getUsername())
                .emailVerified(true)
                .build();
        userRepository.save(user);
    }

    // 카카오 회원가입
    public User signupByKakao(KakaoUserInfo kakaoUserInfo) {
        if (userRepository.findByEmail(kakaoUserInfo.getEmail()).isPresent()) {
            throw new IllegalStateException("이미 가입된 이메일입니다.");
        }
        User user = User.builder()
                .email(kakaoUserInfo.getEmail())
                .username(kakaoUserInfo.getNickname())
                .emailVerified(true)
                .password("") // 소셜 회원가입은 비밀번호 없음
                .build();
        return userRepository.save(user);
    }

    // 이메일 중복 체크
    public boolean emailExists(String email) {
        return userRepository.findByEmail(email) != null;
    }

    // 로그인
    public boolean login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException("이메일로 사용자를 찾을 수 없습니다: " + email);
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }
}

package com.pres.pres_server.service.email;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.pres.pres_server.repository.EmailAuthCodeRepository;
import com.pres.pres_server.domain.EmailAuthCode;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.transaction.annotation.Transactional;

//인증 코드 생성·전송 로직
//코드 저장·검증 로직 (Redis나 DB)
//역할: 이메일 발송·인증 비즈니스 로직 처리
//구성: sendCode(email), verifyCode(email, code) 같은 메서드

@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final EmailAuthCodeRepository emailAuthCodeRepository;
    private final UserRepository userRepository;

    private static final long CODE_TTL_MINUTES = 5;
    private static final long VERIFIED_TTL_HOURS = 1;

    public String generateCode() {
        int num = (int) (Math.random() * 900_000) + 100_000;
        return Integer.toString(num);
    }

    // 인증 코드 전송
    @Transactional
    public void sendCode(String email) {
        String code = generateCode();
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES);

        // 기존 인증코드 삭제 후 새로 저장
        deleteAuthCodeByEmail(email);
        EmailAuthCode authCode = EmailAuthCode.builder()
                .email(email)
                .code(code)
                .expireAt(expireAt)
                .build();
        emailAuthCodeRepository.save(authCode);

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(email);
        msg.setSubject("회원가입 인증 코드");
        msg.setText("인증 코드: " + code + " 유효 시간: " + CODE_TTL_MINUTES + "분");
        mailSender.send(msg);
    }

    // 인증코드 검증
    public boolean verifyCode(String email, String code) {
        EmailAuthCode authCode = emailAuthCodeRepository.findByEmail(email).orElse(null);
        User user = userRepository.findByEmail(email).orElse(null);

        if (authCode == null)
            return false;
        if (!authCode.getCode().equals(code))
            return false;
        if (authCode.getExpireAt().isBefore(LocalDateTime.now())) {
            deleteAuthCodeByEmail(email);
            return false;
        }

        // 인증 성공 시 사용자 이메일 인증 상태 업데이트
        // 인증 상태를 DB에 저장하거나, User 엔티티에 인증 필드 추가해서 관리하는 것이 일반적
        if (user != null) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(LocalDateTime.now()); // 인증 성공 시각 저장
            userRepository.save(user);
        }

        // 인증 성공 시 인증코드 삭제
        deleteAuthCodeByEmail(email);
        return true;
    }

    // 인증 상태를 DB에 저장하거나, User 엔티티에 인증 필드 추가해서 관리하는 것이 일반적
    // 인증 코드 삭제 로직을 별도 메서드로 분리
    @Transactional
    private void deleteAuthCodeByEmail(String email) {
        // 인증 상태 업데이트
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            user.setEmailVerified(false);
            userRepository.save(user);
        }
        emailAuthCodeRepository.deleteByEmail(email);
    }

    // 인증 상태 관리가 필요하다면 User 엔티티에 인증 여부 필드 추가 권장
    public boolean isVerified(String email) {
        // 인증코드가 삭제된 상태면 인증된 것으로 간주 (혹은 User 엔티티에서 관리)
        return !emailAuthCodeRepository.findByEmail(email).isPresent();
    }

    // 만료된 인증코드 주기적 삭제 (예: 1분마다)
    // 스케줄러 활성화를 위해 @EnableScheduling 필요 (메인 애플리케이션 클래스에 추가)
    @Transactional
    @Scheduled(fixedRate = 60000) // 1분마다 실행
    public void deleteExpiredCodes() {
        LocalDateTime now = LocalDateTime.now();
        emailAuthCodeRepository.deleteAllByExpireAtBefore(now);
    }
}

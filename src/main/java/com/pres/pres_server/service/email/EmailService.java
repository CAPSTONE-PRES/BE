package com.pres.pres_server.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pres.pres_server.domain.EmailAuthCode;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.repository.EmailAuthCodeRepository;
import com.pres.pres_server.repository.UserRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailAuthCodeRepository emailAuthCodeRepository;
    private final UserRepository userRepository;

    private static final long CODE_TTL_MINUTES = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /* 인증 코드 전송 (업서트 + 전송 실패 시 정합성 회복) */
    @Transactional
    public void sendCode(String email) {
        String code = generateCode();
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES);

        // Upsert
        emailAuthCodeRepository.findByEmail(email).ifPresentOrElse(existing -> {
            existing.setCode(code);
            existing.setExpireAt(expireAt);
            emailAuthCodeRepository.save(existing);
        }, () -> {
            emailAuthCodeRepository.save(
                    EmailAuthCode.builder()
                            .email(email)
                            .code(code)
                            .expireAt(expireAt)
                            .build()
            );
        });

        SimpleMailMessage msg = buildMessage(email, code, CODE_TTL_MINUTES);
        try {
            mailSender.send(msg);
        } catch (Exception e) {
            // 전송 실패 시 코드 제거(정합성 유지)
            emailAuthCodeRepository.deleteByEmail(email);
            throw new IllegalStateException("메일 전송 실패", e);
        }
    }

    /* 인증코드 검증 (성공 시 사용자 인증 플래그 업데이트, 코드 정리) */
    @Transactional
    public boolean verifyCode(String email, String code) {
        log.info("verifyCode start");
        if (email == null || code == null) return false;
        email = email.trim(); code = code.trim();

        var opt = emailAuthCodeRepository.findByEmail(email);
        if (opt.isEmpty()) return false;

        var auth = opt.get();
        var now  = LocalDateTime.now();

        if (!auth.getExpireAt().isAfter(now)) { // 만료
            emailAuthCodeRepository.deleteByEmail(email);
            return false;
        }
        if (!code.equals(auth.getCode())) {     // 불일치 -> 유지
            return false;
        }

        userRepository.findByEmail(email).ifPresent(u -> {
            u.setEmailVerified(true);
            u.setEmailVerifiedAt(now);
            userRepository.save(u);
        });
        emailAuthCodeRepository.deleteByEmail(email); // 성공 시 삭제
        return true;
    }

    public boolean isVerified(String email) {

        return userRepository.findByEmail(email)
                .map(User::isEmailVerified)
                .orElse(false);
    }

    /* 만료 코드 정리 (애플리케이션 스케줄러, 5분 주기) */
    @Scheduled(fixedRate = 300_000)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteExpiredCodes() {
        int deleted = emailAuthCodeRepository.deleteExpired(LocalDateTime.now());
        log.info("[SCHED] expired auth codes deleted: {}", deleted);
    }

    /* --- 내부 유틸 --- */

    private static SimpleMailMessage buildMessage(String to, String code, long ttlMin) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("회원가입 인증 코드");
        msg.setText("인증 코드: " + code + " / 유효 시간: " + ttlMin + "분");
        return msg;
    }

    private String generateCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000)); // 000000~999999
    }
}

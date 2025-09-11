package com.pres.pres_server.service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

import com.pres.pres_server.repository.EmailAuthCodeRepository;
import com.pres.pres_server.domain.EmailAuthCode;
import java.time.LocalDateTime;

import jakarta.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EmailServiceTest {

    @Autowired
    private EmailService emailService;

    @Autowired
    private EmailAuthCodeRepository emailAuthCodeRepository;

    // 반드시 추가: Spring Security OAuth2의 자동 설정이 테스트 컨텍스트에서
    // ClientRegistrationRepository 빈을 필요로 하므로,
    // 이를 MockBean으로 등록하지 않으면 테스트 실행 시 NoSuchBeanDefinitionException이 발생합니다.
    @MockBean
    ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    OAuth2AuthorizedClientRepository authorizedClientRepository;

    @BeforeEach
    void resetDb() {
        emailAuthCodeRepository.deleteAll();
        emailAuthCodeRepository.flush(); // 즉시 DB 반영
    }

    //인증코드 발송 후, 코드 검증 및 인증 상태 확인 테스트
    @Transactional
    @Test
    void sendAuthCode() {
        String email = "jhs381@naver.com";
        emailService.sendCode(email);

        // DB에서 직접 인증코드 조회 (테스트용)
        EmailAuthCode authCode = emailAuthCodeRepository.findByEmail(email).orElse(null);
        assertNotNull(authCode, "인증코드가 DB에 저장되어야 함");
        String code = authCode.getCode();

        boolean result = emailService.verifyCode(email, code);
        assertTrue(result, "인증코드 검증 성공해야 함");
        assertTrue(emailService.isVerified(email), "이메일 인증 상태 OK여야 함");
    }

    //잘못된 인증코드로 검증 시 실패하는지 테스트
    @Test
    void verifyAuthCode() {
        String email = "jhs381@naver.com";
        emailService.sendCode(email);

        boolean result = emailService.verifyCode(email, "wrongCode");
        assertFalse(result, "잘못된 인증코드는 실패해야 함");
        assertFalse(emailService.isVerified(email), "이메일 인증 상태 OK가 아니어야 함");
    }

    //인증코드가 만료되었을 때 정상적으로 없어지는지 테스트
    @Transactional
    @Test
    void verifyAuthCodeExpiration() throws InterruptedException {
        String email = "jhs381@naver.com";
        // 테스트용 짧은 TTL로 인증코드 저장
        EmailAuthCode authCode = EmailAuthCode.builder()
                .email(email)
                .code("123456")
                .expireAt(LocalDateTime.now().plusSeconds(1))
                .build();
        emailAuthCodeRepository.save(authCode);
        Thread.sleep(1500);
        // 만료된 인증코드 직접 삭제 (스케줄러 대신)
        emailAuthCodeRepository.deleteAllByExpireAtBefore(LocalDateTime.now());
        EmailAuthCode afterDelete = emailAuthCodeRepository.findByEmail(email).orElse(null);
        assertNull(afterDelete, "만료 후 인증코드는 없어야 함");
    }
}

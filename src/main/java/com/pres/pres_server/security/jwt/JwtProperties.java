package com.pres.pres_server.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Setter
@Getter
@Component
@ConfigurationProperties("jwt") // application.yml의 jwt 속성 매핑
public class JwtProperties {

    private String issuer;
    private String secretKey;

    // 토큰 만료 시간 설정: Spring Boot은 ISO-8601 Duration 문자열을 지원합니다.
    // 예: PT2H (2 hours), P14D (14 days), P1D (1 day)
    private Duration accessTokenDuration = Duration.ofHours(2);
    private Duration refreshTokenDuration = Duration.ofDays(14);
    private Duration oauthAccessTokenDuration = Duration.ofDays(1);
    // 테스트용 액세스 토큰 만료 시간 (기본 3분)
    private Duration testAccessTokenDuration = Duration.ofMinutes(3);

}

package com.pres.pres_server.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Component
@ConfigurationProperties("jwt") // application.yml의 jwt 속성 매핑
public class JwtProperties {

    private String issuer;
    private String secretKey;

}

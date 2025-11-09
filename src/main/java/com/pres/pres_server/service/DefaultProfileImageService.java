package com.pres.pres_server.service;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConfigurationProperties(prefix = "app.s3")
@Getter
@Setter
@Slf4j
public class DefaultProfileImageService {

    // application.yml에서 자동 주입
    private List<String> defaultProfileKeys;

    public String pickDefaultKey(String email) {
        if (defaultProfileKeys == null || defaultProfileKeys.isEmpty()) {
            log.warn("기본 프로필 키 미설정 상태 — fallback 적용");
            defaultProfileKeys = List.of("default-profiles/default.svg"); // fallback
        }

        int idx = Math.abs(email.hashCode()) % defaultProfileKeys.size();
        return defaultProfileKeys.get(idx);
    }

    public boolean isDefaultKey(String key) {
        return key != null && defaultProfileKeys != null && defaultProfileKeys.contains(key);
    }

}

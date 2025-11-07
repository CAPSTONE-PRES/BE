package com.pres.pres_server.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConfigurationProperties(prefix = "app.cdn")
@Getter
@Setter
public class DefaultProfileImageService {

    // application.yml에서 자동 주입
    private List<String> defaultProfileKeys;

    /**
     * 이메일 기반 결정적 랜덤 프로필 이미지 반환
     * 같은 이메일은 항상 같은 이미지를 받음
     */
    public String pickDefaultKey(String email) {
        if (defaultProfileKeys == null || defaultProfileKeys.isEmpty()) {
            throw new IllegalStateException("기본 프로필 key 미설정");
        }
        int idx = Math.abs(email.hashCode()) % defaultProfileKeys.size();
        return defaultProfileKeys.get(idx);
    }

    public boolean isDefaultKey(String key) {
        return key != null && defaultProfileKeys != null && defaultProfileKeys.contains(key);
    }

}

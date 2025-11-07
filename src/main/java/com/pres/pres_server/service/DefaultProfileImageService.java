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
    private List<String> defaultProfileUrl;

    /**
     * 이메일 기반 결정적 랜덤 프로필 이미지 반환
     * 같은 이메일은 항상 같은 이미지를 받음
     */
    public String getDefaultProfileImage(String email) {
        if (defaultProfileUrl == null || defaultProfileUrl.isEmpty()) {
            throw new IllegalStateException("기본 프로필 이미지 URL이 설정되지 않았습니다.");
        }

        int hash = Math.abs(email.hashCode());
        int index = hash % defaultProfileUrl.size();
        return defaultProfileUrl.get(index);
    }

    /**
     * 해당 URL이 기본 프로필 이미지인지 판별
     */
    public boolean isDefaultImage(String imageUrl) {
        if (imageUrl == null || defaultProfileUrl == null) {
            return false;
        }
        return defaultProfileUrl.contains(imageUrl);
    }
}

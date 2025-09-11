package com.pres.pres_server.config.oauth;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@RequiredArgsConstructor
@Service
public class OAuth2UserCustomService extends DefaultOAuth2UserService {
    private final UserRepository userRepository;

    // 사용자 정보 가져오기
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(userRequest);
        saveORUpdate(user);
        return user;
    }

    // 유저가 있으면 업데이트, 없으면 유저 생성
    private void saveORUpdate(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        // 카카오의 경우 email, nickname은 kakao_account, properties 내부에 있음
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
        Map<String, Object> properties = (Map<String, Object>) attributes.get("properties");
        String name = properties != null ? (String) properties.get("nickname") : null;

        User user = userRepository.findByEmail(email)
                .map(entity -> {
                    entity.update(name);
                    return entity;
                })
                .orElseGet(() -> User.builder()
                        .email(email)
                        .username(name)
                        .build());

        userRepository.save(user);
    }
}

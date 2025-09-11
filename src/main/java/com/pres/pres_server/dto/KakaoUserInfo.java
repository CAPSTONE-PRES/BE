package com.pres.pres_server.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KakaoUserInfo {
    private String email;
    private String nickname;

    public KakaoUserInfo(String email, String nickname) {
        this.email = email;
        this.nickname = nickname;
    }
}

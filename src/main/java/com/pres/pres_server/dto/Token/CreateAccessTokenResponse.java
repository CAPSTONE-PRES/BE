package com.pres.pres_server.dto.Token;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class CreateAccessTokenResponse {
    private String accessToken;
    private String refreshToken;
}

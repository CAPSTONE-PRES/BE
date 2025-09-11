package com.pres.pres_server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import com.pres.pres_server.dto.Token.CreateAccessTokenRequest;
import com.pres.pres_server.dto.Token.CreateAccessTokenResponse;
import com.pres.pres_server.service.token.TokenService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
public class TokenApiController {
    private final TokenService tokenService;

    @PostMapping("/token")
    public ResponseEntity<CreateAccessTokenResponse> createAccessToken(
            @RequestBody CreateAccessTokenRequest request) {

        String newAccessToken = tokenService.createAccessToken(request.getRefreshToken());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateAccessTokenResponse(newAccessToken, request.getRefreshToken()));
    }

}

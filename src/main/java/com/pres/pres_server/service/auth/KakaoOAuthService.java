package com.pres.pres_server.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.Token.KakaoTokenResponse;
import com.pres.pres_server.dto.KakaoUserInfo;
import com.pres.pres_server.dto.Token.CreateAccessTokenResponse;
import com.pres.pres_server.service.user.UserService;
import com.pres.pres_server.service.token.TokenService;
import com.pres.pres_server.exception.KakaoBadRequestException;
import com.pres.pres_server.exception.KakaoInternalServerException;
import com.pres.pres_server.exception.KakaoUnauthorizedException;

import jakarta.servlet.http.HttpSession;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoOAuthService {

    private static final String SESSION_ACCESS_TOKEN_KEY = "access_token";

    private final UserService userService;
    private final TokenService tokenService;
    private final UserAuthService userAuthService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${kakao.client-id}")
    public String clientId;

    @Value("${kakao.client-secret}")
    public String clientSecret;

    @Value("${kakao.redirect-uri}")
    public String redirectUri;

    @Value("${kakao.kauth-host}")
    public String kauthHost; // 예: https://kauth.kakao.com

    @Value("${kakao.kapi-host}")
    public String kapiHost; // 예: https://kapi.kakao.com

    // 카카오 인가 코드 요청 URL 생성
    public String getOauthRedirectURL() {
        if (clientId == null || redirectUri == null || kauthHost == null) {
            throw new IllegalStateException("Kakao OAuth 설정이 올바르지 않습니다.");
        }
        return UriComponentsBuilder.fromUriString(kauthHost + "/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .build()
                .toUriString();
    }

    // scope 옵션 추가, 카카오 인가 코드 요청 URL 생성
    public String getAuthUrl(String scope) {
        return UriComponentsBuilder
                .fromUriString(kauthHost + "/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParamIfPresent("scope", scope != null ? java.util.Optional.of(scope) : java.util.Optional.empty())
                .build()
                .toUriString();
    }

    // 인가코드로 카카오 토큰 발급
    public KakaoTokenResponse getToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new org.springframework.util.LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        try {
            // DTO 클래스를 응답 타입으로 지정하여 자동 파싱
            ResponseEntity<KakaoTokenResponse> response = restTemplate.postForEntity(kauthHost + "/oauth/token",
                    request, KakaoTokenResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new KakaoInternalServerException("카카오 토큰 발급 실패: " + response.getStatusCode());
            }

            return response.getBody();
        } catch (RestClientException e) {
            // 통신 오류 처리
            throw new KakaoInternalServerException("카카오 토큰 발급 중 통신 오류 발생", e);
        }
    }

    // 사용자 조회 및 회원가입
    public User processKakaoUser(KakaoTokenResponse kakaoTokenResponse) {
        if (kakaoTokenResponse == null || kakaoTokenResponse.getAccessToken() == null) {
            throw new KakaoBadRequestException("액세스 토큰이 올바르지 않습니다.");
        }

        KakaoUserInfo kakaoUserInfo = getKakaoUserInfo(kakaoTokenResponse.getAccessToken());
        User user = null;
        try {
            user = userService.findByEmail(kakaoUserInfo.getEmail());
        } catch (Exception e) {
            // DB 조회 실패 시 신규 회원가입
            user = null;
        }
        if (user == null) {
            user = userAuthService.signupByKakao(kakaoUserInfo); // 회원가입 로직
        }
        return user;
    }

    // jwt 토큰 발급
    // tokenService에 위임
    public CreateAccessTokenResponse issueJwtToken(User user) {
        // String refreshToken = tokenService.createRefreshToken(user);
        // String accessToken = tokenService.createAccessToken(refreshToken);
        // return new CreateAccessTokenResponse(accessToken, refreshToken);
        return null;
    }

    public KakaoUserInfo getKakaoUserInfo(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) {
            // 액세스 토큰 누락
            throw new KakaoBadRequestException("액세스 토큰이 올바르지 않습니다.");
        }
        String url = "https://kapi.kakao.com/v2/user/me";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            // HTTP 요청: 응답 타입을 JsonNode로 직접 받습니다. (필요에 따라 DTO 사용도 가능)
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

            // 응답 상태 코드 체크: HTTP 에러 코드를 받은 경우
            if (response.getStatusCode().is4xxClientError()) {
                // 4xx 에러 중 401은 Unauthorized, 그 외는 Bad Request로 처리
                if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    throw new KakaoUnauthorizedException("카카오 사용자 정보 조회 실패: 유효하지 않은 액세스 토큰");
                } else {
                    throw new KakaoBadRequestException("카카오 사용자 정보 조회 실패: " + response.getStatusCode());
                }
            }
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new KakaoInternalServerException("카카오 사용자 정보 조회 실패: " + response.getStatusCode());
            }

            // JSON 파싱 및 데이터 추출
            JsonNode jsonNode = response.getBody();
            String email = jsonNode.path("kakao_account").path("email").asText();
            String nickname = jsonNode.path("properties").path("nickname").asText();

            // 추출된 정보 유효성 검사 (필요 시)
            if (email == null || email.isEmpty()) {
                throw new KakaoInternalServerException("카카오 사용자 정보에 이메일이 없습니다.");
            }

            return new KakaoUserInfo(email, nickname);
        } catch (RestClientException e) {
            // 통신 오류 처리
            throw new KakaoInternalServerException("카카오 사용자 정보 조회 중 통신 오류 발생", e);
        }
    }

    // 카카오 계정 연결 끊기
    public void unlinkKakaoAccount(String accessToken) {

        if (accessToken == null || accessToken.isEmpty()) {
            throw new KakaoBadRequestException("액세스 토큰이 올바르지 않습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    kapiHost + "/v1/user/unlink",
                    entity,
                    String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new KakaoInternalServerException("카카오 계정 연결 끊기 실패: " + response.getStatusCode());
            }
            log.info("카카오 계정 연결이 성공적으로 해제되었습니다.");
        } catch (RestClientException e) {
            throw new KakaoInternalServerException("카카오 계정 연결 끊기 중 통신 오류 발생", e);
        }
    }

}
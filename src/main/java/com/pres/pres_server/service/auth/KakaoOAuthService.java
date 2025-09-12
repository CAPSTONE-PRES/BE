package com.pres.pres_server.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
import jakarta.servlet.http.HttpSession;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoOAuthService {

    private final UserService userService;
    private final TokenService tokenService;
    private final UserAuthService userAuthService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${kakao.client-id}")
    public String clientId;

    @Value("${kakao.client-secret}")
    String clientSecret;

    @Value("${kakao.redirect-uri}")
    public String redirectUri;

    @Value("${kakao.kauth-host}")
    public String kauthHost; // 예: https://kauth.kakao.com

    @Value("${kakao.kapi-host}")
    public String kapiHost; // 예: https://kapi.kakao.com

    public String getOauthRedirectURL() {
        return UriComponentsBuilder.fromHttpUrl(kauthHost + "/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .build()
                .toUriString();
    }

    public String requestAccessToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<String> responseEntity = restTemplate.postForEntity(
                kauthHost + "/oauth/token",
                request,
                String.class);

        // http 오류 / null
        if (!responseEntity.getStatusCode().is2xxSuccessful() || responseEntity.getBody() == null) {
            throw new IllegalArgumentException("카카오 토큰 교환 실패" + responseEntity.getStatusCode());
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(responseEntity.getBody());

            if (jsonNode.hasNonNull("error")) {
                throw new IllegalStateException("카카오 에러" + jsonNode.get("error").asText());
            }
            String accessToken = jsonNode.get("access_token").asText(null);
            if (accessToken == null) {
                throw new IllegalStateException("카카오 access token is null");
            }
            return accessToken;

        } catch (Exception e) {
            throw new IllegalStateException("카카오 토큰 파싱 실패", e);
        }

    }

    public String requestAccessTokenUsingURL(String code) {
        try {
            URL url = new URL(kapiHost + "/oauth/token");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            Map<String, Object> params = new HashMap<>();
            params.put("code", code);
            params.put("client_id", clientId);
            params.put("client_secret", clientSecret);
            params.put("redirect_uri", redirectUri);
            params.put("grant_type", "authorization_code");

            String parameterString = params.entrySet().stream()
                    .map(x -> x.getKey() + "=" + x.getValue())
                    .collect(Collectors.joining("&"));

            BufferedOutputStream bous = new BufferedOutputStream(conn.getOutputStream());
            bous.write(parameterString.getBytes());
            bous.flush();
            bous.close();

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));

            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            if (conn.getResponseCode() == 200) {
                return sb.toString();
            }
            return "카카오 로그인 요청 처리 실패";
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "알 수 없는 카카오 로그인 Access Token 요청 URL 입니다 :: " + kapiHost + "/oauth/token");
        }
    }

    public User processKakaoUser(String accessToken) {
        KakaoUserInfo kakaoUserInfo = getKakaoUserInfo(accessToken);
        User user;
        try {
            user = userService.findByEmail(kakaoUserInfo.getEmail());
        } catch (Exception e) {
            user = null;
        }
        if (user == null) {
            user = userAuthService.signupByKakao(kakaoUserInfo); // 회원가입 로직(직접 구현 필요)
        }
        return user;
    }

    public CreateAccessTokenResponse issueJwtToken(User user) {
        String refreshToken = tokenService.createRefreshToken(user);
        String accessToken = tokenService.createAccessToken(refreshToken);
        return new CreateAccessTokenResponse(accessToken, refreshToken);
    }

    public KakaoUserInfo getKakaoUserInfo(String accessToken) {
        String url = "https://kapi.kakao.com/v2/user/me";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                String email = jsonNode.path("kakao_account").path("email").asText();
                String nickname = jsonNode.path("properties").path("nickname").asText();
                return new KakaoUserInfo(email, nickname);
            } catch (Exception e) {
                throw new IllegalArgumentException("카카오 사용자 정보 파싱 실패", e);
            }
        }
        throw new IllegalArgumentException("카카오 사용자 정보 조회 실패");
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
        ResponseEntity<String> response = restTemplate.postForEntity(kapiHost + "/oauth/token", request, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            try {
                return objectMapper.readValue(response.getBody(), KakaoTokenResponse.class);
            } catch (Exception e) {
                throw new RuntimeException("카카오 토큰 파싱 실패", e);
            }
        }
        throw new RuntimeException("카카오 토큰 발급 실패: " + response.getStatusCode());
    }

    private HttpSession getSession() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attr.getRequest().getSession();
    }

    private void saveAccessToken(String accessToken) {
        getSession().setAttribute("access_token", accessToken);
    }

    private String getAccessToken() {
        return (String) getSession().getAttribute("access_token");
    }

    private void invalidateSession() {
        getSession().invalidate();
    }

    private String call(String method, String urlString, String body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request;
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            request = new HttpEntity<>(body, headers);
        } else {
            request = new HttpEntity<>(headers);
        }

        HttpMethod httpMethod = HttpMethod.valueOf(method);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    urlString,
                    httpMethod,
                    request,
                    String.class);
            return response.getBody();
        } catch (Exception e) {

            if (e instanceof org.springframework.web.client.HttpStatusCodeException) {
                org.springframework.web.client.HttpStatusCodeException httpException = (org.springframework.web.client.HttpStatusCodeException) e;
                return httpException.getResponseBodyAsString();
            }

            throw e;
        }
    }

    public String getAuthUrl(String scope) {
        return UriComponentsBuilder
                .fromHttpUrl(kauthHost + "/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParamIfPresent("scope", scope != null ? java.util.Optional.of(scope) : java.util.Optional.empty())
                .build()
                .toUriString();
    }

    public boolean handleAuthorizationCallback(String code) {
        try {
            KakaoTokenResponse tokenResponse = getToken(code);
            if (tokenResponse != null) {
                saveAccessToken(tokenResponse.getAccess_token());
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    // 3. 사용자 정보 조회
    public ResponseEntity<?> getUserProfile() {
        try {
            String KakaoResponse = call("GET", kapiHost + "/v2/user/me", null);
            return ResponseEntity.ok(objectMapper.readValue(KakaoResponse, Object.class));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    public ResponseEntity<?> logout() {
        try {
            String response = call("POST", kapiHost + "/v1/user/logout", null);
            invalidateSession();
            return ResponseEntity.ok(objectMapper.readValue(response, Object.class));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

}
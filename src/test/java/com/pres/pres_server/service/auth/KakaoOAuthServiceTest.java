package com.pres.pres_server.service.auth;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.KakaoUserInfo;
import com.pres.pres_server.service.user.UserService;
import com.pres.pres_server.service.token.TokenService;
import com.pres.pres_server.dto.Token.KakaoTokenResponse;
import com.pres.pres_server.dto.Token.CreateAccessTokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
/*
@ExtendWith(MockitoExtension.class)
class KakaoOAuthServiceTest {
    @InjectMocks
    private KakaoOAuthService kakaoOAuthService;
    @Mock
    private UserService userService;
    @Mock
    private UserAuthService userAuthService;
    @Mock
    private TokenService tokenService;
    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // @Value 주입 필드 세팅
        ReflectionTestUtils.setField(kakaoOAuthService, "kauthHost", "https://kauth.kakao.com");
        ReflectionTestUtils.setField(kakaoOAuthService, "clientId", "test-client-id");
        ReflectionTestUtils.setField(kakaoOAuthService, "clientSecret", "test-secret");
        ReflectionTestUtils.setField(kakaoOAuthService, "redirectUri", "http://localhost/callback");
    }

    @Test
    @DisplayName("getOauthRedirectURL 정상 동작 테스트")
    void getOauthRedirectURL_shouldReturnUrl() {
        // given: 환경 변수 값 세팅 (Reflection 사용)
        kakaoOAuthService.clientId = "98b9a68288985b80e9afd47551cbe3da";
        kakaoOAuthService.redirectUri = "http://localhost:8080/callback";
        kakaoOAuthService.kauthHost = "https://kauth.kakao.com";

        // when
        String url = kakaoOAuthService.getOauthRedirectURL();

        // then
        assertThat(url).startsWith("https://kauth.kakao.com/oauth/authorize?");
        assertThat(url).contains("client_id=98b9a68288985b80e9afd47551cbe3da");
        assertThat(url).contains("redirect_uri=http://localhost:8080/callback");
        assertThat(url).contains("response_type=code");
    }

    @Test
    @DisplayName("processKakaoUser 신규 회원가입 로직 테스트")
    void processKakaoUser_shouldSignupIfUserNotExist() {
        // given
        String accessToken = "dummy-access-token";
        // 주입 누락 방지
        KakaoOAuthService spyService = Mockito.spy(kakaoOAuthService);
        ReflectionTestUtils.setField(spyService, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(spyService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(spyService, "objectMapper", objectMapper);
        KakaoUserInfo kakaoUserInfo = new KakaoUserInfo("test@kakao.com", "tester");
        User newUser = Mockito.mock(User.class);

        Mockito.doReturn(kakaoUserInfo).when(spyService).getKakaoUserInfo(anyString());
        Mockito.doThrow(new RuntimeException()).when(userService).findByEmail(kakaoUserInfo.getEmail());
        Mockito.doReturn(newUser).when(userAuthService).signupByKakao(any(KakaoUserInfo.class));

        System.out.println("kakaoUserInfo: " + kakaoUserInfo.getEmail());
        System.out.println("findByEmail exception Mock ");
        System.out.println("signupByKakao Mock ");

        // when
        User result = spyService.processKakaoUser(accessToken);

        // then
        assertThat(result).isNotNull();

    }

    @Test
    @DisplayName("processKakaoUser 기존 회원 로직 테스트")
    void processKakaoUser_shouldReturnExistingUser() {
        // given
        String accessToken = "dummy-access-token";
        KakaoOAuthService spyService = Mockito.spy(kakaoOAuthService);
        // 주입 누락 방지
        ReflectionTestUtils.setField(spyService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(spyService, "objectMapper", objectMapper);
        KakaoUserInfo kakaoUserInfo = new KakaoUserInfo("test@kakao.com", "tester");
        Mockito.doReturn(kakaoUserInfo).when(spyService).getKakaoUserInfo(anyString());
        User existingUser = Mockito.mock(User.class);
        Mockito.doReturn(existingUser).when(userService).findByEmail(anyString());

        // when
        User result = spyService.processKakaoUser(accessToken);

        // then
        assertThat(result).isEqualTo(existingUser);
    }

    @Test
    @DisplayName("issueJwtToken 정상 동작 테스트")
    void issueJwtToken_shouldReturnTokens() {
        // given
        User user = Mockito.mock(User.class);
        String refreshToken = "refresh-token";
        String accessToken = "access-token";
        Mockito.doReturn(refreshToken).when(tokenService).createRefreshToken(user);
        Mockito.doReturn(accessToken).when(tokenService).createAccessToken(refreshToken);

        // when
        CreateAccessTokenResponse result = kakaoOAuthService.issueJwtToken(user);

        // then
        assertThat(result.getAccessToken()).isEqualTo(accessToken);
        assertThat(result.getRefreshToken()).isEqualTo(refreshToken);
    }

    @Test
    @DisplayName("requestAccessToken 정상 동작 테스트")
    void requestAccessToken_shouldReturnTokenString() {
        // given
        String code = "dummy-code";
        KakaoOAuthService spyService = Mockito.spy(kakaoOAuthService);

        ReflectionTestUtils.setField(spyService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(spyService, "objectMapper", new ObjectMapper());
        // 주입 누락 방지
        String body = "{\"access_token\":\"access-token\",\"refresh_token\":\"refresh-token\"}";
        String expectedToken = "access-token";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(body, HttpStatus.OK);
        Mockito.doReturn(mockResponse).when(restTemplate)
                .postForEntity(Mockito.anyString(), Mockito.any(), Mockito.eq(String.class));

        // when
        String result = spyService.requestAccessToken(code);

        // then
        assertThat(result).isEqualTo(expectedToken);
    }

    @Test
    @DisplayName("getKakaoUserInfo 정상 동작 테스트")
    void getKakaoUserInfo_shouldReturnUserInfo() {
        // given
        String accessToken = "dummy-access-token";
        // 주입 누락 방지
        KakaoOAuthService spyService = Mockito.spy(kakaoOAuthService);
        ReflectionTestUtils.setField(spyService, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(spyService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(spyService, "objectMapper", new ObjectMapper());

        String mockJson = "{\"kakao_account\":{\"email\":\"test@kakao.com\"},\"properties\":{\"nickname\":\"tester\"}}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(mockJson,
                HttpStatus.OK);
        Mockito.doReturn(mockResponse).when(restTemplate)
                .exchange(Mockito.anyString(), Mockito.any(), Mockito.any(),
                        Mockito.eq(String.class));
        // when
        KakaoUserInfo result = spyService.getKakaoUserInfo(accessToken);

        // then
        assertThat(result.getEmail()).isEqualTo("test@kakao.com");
        assertThat(result.getNickname()).isEqualTo("tester");
    }

    @Test
    @DisplayName("getToken 정상 동작 테스트")
    void getToken_shouldReturnKakaoTokenResponse() throws Exception {
        // given
        String code = "dummy-code";
        // 주입 누락 방지
        KakaoOAuthService spyService = Mockito.spy(kakaoOAuthService);
        ReflectionTestUtils.setField(spyService, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(spyService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(spyService, "objectMapper", objectMapper);
        spyService.kapiHost = "https://kapi.kakao.com";
        spyService.clientId = "dummy-client-id";
        spyService.clientSecret = "dummy-client-secret";
        spyService.redirectUri = "http://localhost:8080/callback";

        String mockJson = "{\"access_token\":\"access-token\",\"refresh_token\":\"refresh-token\"}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(mockJson, HttpStatus.OK);
        Mockito.doReturn(mockResponse).when(restTemplate)
                .postForEntity(Mockito.anyString(), Mockito.any(), Mockito.eq(String.class));

        // when
        KakaoTokenResponse result = spyService.getToken(code);

        // then
        assertThat(result.getAccess_token()).isEqualTo("access-token");
        assertThat(result.getRefresh_token()).isEqualTo("refresh-token");
        System.out.println("result: " + result.getAccess_token() + ", " + result.getRefresh_token());
    }

}
*/
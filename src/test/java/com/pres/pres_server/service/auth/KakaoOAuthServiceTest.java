package com.pres.pres_server.service.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.KakaoUserInfo;
import com.pres.pres_server.dto.Token.KakaoTokenResponse;
import com.pres.pres_server.exception.KakaoBadRequestException;
import com.pres.pres_server.exception.KakaoInternalServerException;
import com.pres.pres_server.exception.KakaoUnauthorizedException;
import com.pres.pres_server.service.token.TokenService;
import com.pres.pres_server.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KakaoOAuthServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private TokenService tokenService;
    @Mock
    private UserAuthService userAuthService;
    @Mock
    private RestTemplate restTemplate;
    @Spy
    @InjectMocks
    private KakaoOAuthService kakaoOAuthService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        kakaoOAuthService.clientId = "test-client-id";
        kakaoOAuthService.clientSecret = "test-client-secret";
        kakaoOAuthService.redirectUri = "http://localhost/callback";
        kakaoOAuthService.kauthHost = "https://kauth.kakao.com";
        kakaoOAuthService.kapiHost = "https://kapi.kakao.com";
    }

    @Test
    void getOauthRedirectURL_returnsCorrectUrl() {
        String url = kakaoOAuthService.getOauthRedirectURL();
        assertTrue(url.contains("client_id=test-client-id"));
        assertTrue(url.contains("redirect_uri=http://localhost/callback"));
        assertTrue(url.contains("response_type=code"));
    }

    @Test
    void getOauthRedirectURL_throwsException_whenConfigMissing() {
        kakaoOAuthService.clientId = null;
        assertThrows(IllegalStateException.class, () -> kakaoOAuthService.getOauthRedirectURL());
    }

    @Test
    void getAuthUrl_withScope_returnsUrlWithScope() {
        String url = kakaoOAuthService.getAuthUrl("profile");
        assertTrue(url.contains("scope=profile"));
    }

    @Test
    void getAuthUrl_withoutScope_returnsUrlWithoutScope() {
        String url = kakaoOAuthService.getAuthUrl(null);
        assertFalse(url.contains("scope="));
    }

    @Test
    @DisplayName("getToken 성공 시, 토큰 응답 반환")
    void getToken_success_returnsTokenResponse() {
        // Given
        String code = "valid-code";
        KakaoTokenResponse mockResponse = new KakaoTokenResponse();
        mockResponse.setAccessToken("access-token");
        mockResponse.setRefreshToken("refresh-token");
        ResponseEntity<KakaoTokenResponse> entity = new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(KakaoTokenResponse.class)))
                .thenReturn(entity);

        // When
        KakaoTokenResponse result = kakaoOAuthService.getToken(code);

        // Then
        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("getToken 실패 시, 내부 서버 예외 발생")
    void getToken_failure_throwsInternalServerException() {
        // given
        ResponseEntity<KakaoTokenResponse> entity = new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(KakaoTokenResponse.class)))
                .thenReturn(entity);
        // When & Then
        assertThrows(KakaoInternalServerException.class, () -> kakaoOAuthService.getToken("invalid-code"));

    }

    @Test
    @DisplayName("getToken 통신 오류 시, 내부 서버 예외 발생")
    void getToken_restClientException_throwsInternalServerException() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(KakaoTokenResponse.class)))
                .thenThrow(new RestClientException("error"));

        assertThrows(KakaoInternalServerException.class, () -> kakaoOAuthService.getToken("code123"));
    }

    @Test
    @DisplayName("processKakaoUser: 기존 사용자일 경우, 기존 사용자 정보 반환")
    void processKakaoUser_existingUser_returnsExistingUser() {
        // Given
        KakaoTokenResponse tokenResponse = new KakaoTokenResponse();
        tokenResponse.setAccessToken("access-token");
        KakaoUserInfo kakaoUserInfo = new KakaoUserInfo("existing@kakao.com", "existing_user");
        User existingUser = mock(User.class);

        doReturn(kakaoUserInfo).when(kakaoOAuthService).getKakaoUserInfo(tokenResponse.getAccessToken());
        when(userService.findByEmail(anyString())).thenReturn(existingUser);

        // When
        User result = kakaoOAuthService.processKakaoUser(tokenResponse);

        // Then
        assertThat(result).isNotNull().isEqualTo(existingUser);
        verify(userAuthService, never()).signupByKakao(any(KakaoUserInfo.class));
    }

    @Test
    @DisplayName("processKakaoUser: 신규 사용자일 경우, 회원가입 로직 호출")
    void processKakaoUser_newUser_callsSignup() {
        // Given
        KakaoTokenResponse tokenResponse = new KakaoTokenResponse();
        tokenResponse.setAccessToken("access-token");
        KakaoUserInfo kakaoUserInfo = new KakaoUserInfo("new_user@kakao.com", "new_user");
        User newUser = mock(User.class);

        // Stubbing `getKakaoUserInfo` and `userService` mocks
        doReturn(kakaoUserInfo).when(kakaoOAuthService).getKakaoUserInfo(tokenResponse.getAccessToken());
        when(userService.findByEmail(anyString())).thenThrow(new RuntimeException("User not found"));
        when(userAuthService.signupByKakao(any(KakaoUserInfo.class))).thenReturn(newUser);

        // When
        User result = kakaoOAuthService.processKakaoUser(tokenResponse);

        // Then
        assertThat(result).isNotNull().isEqualTo(newUser);
        verify(userAuthService, times(1)).signupByKakao(any(KakaoUserInfo.class));

    }

    @Test
    void processKakaoUser_nullToken_throwsBadRequest() {
        assertThrows(KakaoBadRequestException.class, () -> kakaoOAuthService.processKakaoUser(null));
    }

    @Test
    void issueJwtToken_returnsNull() {
        assertNull(kakaoOAuthService.issueJwtToken(mock(User.class)));
    }

    @Test
    void getKakaoUserInfo_success_returnsUserInfo() {
        ObjectNode jsonNode = objectMapper.createObjectNode();
        jsonNode.set("kakao_account", objectMapper.createObjectNode().put("email", "test@email.com"));
        jsonNode.set("properties", objectMapper.createObjectNode().put("nickname", "nick"));

        ResponseEntity<JsonNode> entity = new ResponseEntity<>(jsonNode, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(entity);

        KakaoUserInfo info = kakaoOAuthService.getKakaoUserInfo("access-token");
        assertEquals("test@email.com", info.getEmail());
        assertEquals("nick", info.getNickname());
    }

    @Test
    void getKakaoUserInfo_unauthorized_throwsUnauthorizedException() {
        ResponseEntity<JsonNode> entity = new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(entity);

        assertThrows(KakaoUnauthorizedException.class, () -> kakaoOAuthService.getKakaoUserInfo("access-token"));
    }

    @Test
    void getKakaoUserInfo_badRequest_throwsBadRequestException() {
        ResponseEntity<JsonNode> entity = new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(entity);

        assertThrows(KakaoBadRequestException.class, () -> kakaoOAuthService.getKakaoUserInfo("access-token"));
    }

    @Test
    void getKakaoUserInfo_internalError_throwsInternalServerException() {
        ResponseEntity<JsonNode> entity = new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(entity);

        assertThrows(KakaoInternalServerException.class, () -> kakaoOAuthService.getKakaoUserInfo("access-token"));
    }

    @Test
    void getKakaoUserInfo_missingEmail_throwsInternalServerException() {
        ObjectNode jsonNode = objectMapper.createObjectNode();
        jsonNode.set("kakao_account", objectMapper.createObjectNode());
        jsonNode.set("properties", objectMapper.createObjectNode().put("nickname", "nick"));

        ResponseEntity<JsonNode> entity = new ResponseEntity<>(jsonNode, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(entity);

        assertThrows(KakaoInternalServerException.class, () -> kakaoOAuthService.getKakaoUserInfo("access-token"));
    }

    @Test
    void getKakaoUserInfo_restClientException_throwsInternalServerException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(new RestClientException("error"));

        assertThrows(KakaoInternalServerException.class, () -> kakaoOAuthService.getKakaoUserInfo("access-token"));
    }

    @Test
    void getKakaoUserInfo_nullAccessToken_throwsBadRequest() {
        assertThrows(KakaoBadRequestException.class, () -> kakaoOAuthService.getKakaoUserInfo(null));
    }

    @Test
    @DisplayName("unlinkKakaoAccount 성공 시, 예외가 발생하지 않음")
    void unlinkKakaoAccount_success_noException() {
        // Given
        ResponseEntity<String> entity = new ResponseEntity<>("{}", HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(entity);

        // When & Then
        assertThatCode(() -> kakaoOAuthService.unlinkKakaoAccount("access-token"))
                .doesNotThrowAnyException();
    }

    @Test
    void unlinkKakaoAccount_success_logsInfo() {
        ResponseEntity<String> entity = new ResponseEntity<>("{}", HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(entity);

        assertDoesNotThrow(() -> kakaoOAuthService.unlinkKakaoAccount("access-token"));
    }

    @Test
    void unlinkKakaoAccount_failure_throwsInternalServerException() {
        ResponseEntity<String> entity = new ResponseEntity<>("{}", HttpStatus.BAD_REQUEST);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(entity);

        assertThrows(KakaoInternalServerException.class, () -> kakaoOAuthService.unlinkKakaoAccount("access-token"));
    }

    @Test
    void unlinkKakaoAccount_restClientException_throwsInternalServerException() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("error"));

        assertThrows(KakaoInternalServerException.class, () -> kakaoOAuthService.unlinkKakaoAccount("access-token"));
    }

    @Test
    void unlinkKakaoAccount_nullAccessToken_throwsBadRequest() {
        assertThrows(KakaoBadRequestException.class, () -> kakaoOAuthService.unlinkKakaoAccount(null));
    }
}
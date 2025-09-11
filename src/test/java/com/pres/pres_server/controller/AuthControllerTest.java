package com.pres.pres_server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.service.auth.KakaoOAuthService;
import com.pres.pres_server.service.auth.UserAuthService;
import com.pres.pres_server.service.email.EmailService;
import com.pres.pres_server.service.token.TokenService;
import com.pres.pres_server.service.user.UserService;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private EmailService emailService;
        @MockBean
        private UserAuthService userAuthService;
        @MockBean
        private KakaoOAuthService kakaoOAuthService;
        @MockBean
        private UserService userService;
        @MockBean
        private TokenService tokenService;

        @Test
        @DisplayName("로그아웃 API 정상 동작 테스트")
        void logout_shouldReturnSuccess() throws Exception {
                mockMvc.perform(MockMvcRequestBuilders.post("/auth/logout"))
                                .andExpect(MockMvcResultMatchers.status().isOk())
                                .andExpect(MockMvcResultMatchers.content().string("logout success"));
        }

        @Test
        @DisplayName("카카오 로그아웃 API 정상 동작 테스트")
        void kakaoLogout_shouldReturnSuccess() throws Exception {
                mockMvc.perform(MockMvcRequestBuilders.post("/auth/kakao/logout")
                                .param("accessToken", "dummyToken"))
                                .andExpect(MockMvcResultMatchers.status().isOk())
                                .andExpect(MockMvcResultMatchers.content().string("Kakao logout success"));
        }

        @Test
        @DisplayName("로그인 API 정상 동작 테스트")
        void login_shouldReturnToken() throws Exception {
                String requestBody = "{ \"email\": \"test@example.com\", \"password\": \"1234\" }";
                when(userAuthService.login("test@example.com", "1234")).thenReturn(true);
                when(userService.findByEmail("test@example.com"))
                                .thenReturn(User.builder()
                                                .email("test@example.com")
                                                .password("1234")
                                                .username("tester")
                                                .emailVerified(true)
                                                .build());
                when(tokenService.createRefreshToken(any(User.class))).thenReturn("refresh-token");
                when(tokenService.createAccessToken("refresh-token")).thenReturn("access-token");
                mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                                .contentType("application/json")
                                .content(requestBody))
                                .andExpect(MockMvcResultMatchers.status().isOk());
        }

        @Test
        @DisplayName("이메일 인증코드 전송 API 정상 동작 테스트")
        void sendSignupEmail_shouldReturnSuccess() throws Exception {
                String requestBody = "{ \"email\": \"test@example.com\" }";
                mockMvc.perform(MockMvcRequestBuilders.post("/auth/email/sendcode")
                                .contentType("application/json")
                                .content(requestBody))
                                .andExpect(MockMvcResultMatchers.status().isOk());
        }

        @Test
        @DisplayName("이메일 인증코드 검증 API 정상 동작 테스트") // 요청 데이터 오류
        void verifySignupEmail_shouldReturnSuccess() throws Exception {
                String requestBody = "{ \"email\": \"test@example.com\", \"authCode\": \"123456\" }";

                when(emailService.verifyCode("test@example.com", "123456")).thenReturn(true);
                when(emailService.isVerified("test@example.com")).thenReturn(true);
                mockMvc.perform(MockMvcRequestBuilders.post("/auth/email/verify")
                                .contentType("application/json")
                                .content(requestBody))
                                .andExpect(MockMvcResultMatchers.status().isOk());
        }

        @Test
        @DisplayName("이메일 회원가입 API 정상 동작 테스트") // 요청 데이터 오류
        void signup_shouldReturnToken() throws Exception {
                String requestBody = "{ \"email\": \"test@example.com\", \"password\": \"1234\", \"passwordConfirm\": \"1234\", \"username\": \"tester\" }";
                when(emailService.isVerified("test@example.com")).thenReturn(true);
                when(userService.findByEmail("test@example.com")).thenReturn(User.builder()
                                .email("test@example.com")
                                .password("1234")
                                .username("tester")
                                .emailVerified(true)
                                .build());
                mockMvc.perform(MockMvcRequestBuilders.post("/auth/email/signup")
                                .contentType("application/json")
                                .content(requestBody))
                                .andExpect(MockMvcResultMatchers.status().isOk());
        }

        @Test
        @DisplayName("카카오 로그인 API 정상 동작 테스트") // client_error 반환
        void kakaoLogin_shouldRedirect() throws Exception {
                when(kakaoOAuthService.getOauthRedirectURL()).thenReturn(
                                "https://kauth.kakao.com/oauth/authorize?client_id=dummy&redirect_uri=dummy&response_type=code");
                mockMvc.perform(MockMvcRequestBuilders.post("/auth/kakao/login"))
                                .andExpect(MockMvcResultMatchers.status().is3xxRedirection());
        }

        @Test
        @DisplayName("카카오 콜백 API 정상 동작 테스트")
        void kakaoCallback_shouldReturnToken() throws Exception {
                mockMvc.perform(MockMvcRequestBuilders.get("/auth/kakao/callback")
                                .param("code", "dummyCode"))
                                .andExpect(MockMvcResultMatchers.status().isOk());
        }
}

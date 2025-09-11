package com.pres.pres_server.controller;

import com.pres.pres_server.dto.EmailAuth.EmailAuthSendRequest;
import com.pres.pres_server.dto.EmailAuth.EmailAuthSendResponse;
import com.pres.pres_server.dto.EmailAuth.EmailAuthVerifyRequest;
import com.pres.pres_server.dto.EmailAuth.EmailAuthVerifyResponse;
import com.pres.pres_server.dto.Login.LoginRequest;
import com.pres.pres_server.dto.Signup.SignupRequest;
import com.pres.pres_server.dto.Token.CreateAccessTokenResponse;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.service.email.EmailService;
import com.pres.pres_server.service.token.TokenService;
import com.pres.pres_server.service.user.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.pres.pres_server.service.auth.UserAuthService;
import com.pres.pres_server.service.auth.KakaoOAuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 이메일 인증코드 전송, 검증 및 이메일 회원가입 API
// 카카오로그인 API

@Tag(name = "Auth", description = "인증 관련 API") // Swagger UI에서 컨트롤러 설명 추가
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final EmailService emailService;
    private final UserAuthService userAuthService;
    private final KakaoOAuthService kakaoAuthService;
    private final UserService userService;
    private final TokenService tokenService;

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인합니다.")
    @PostMapping("/login")
    public ResponseEntity<CreateAccessTokenResponse> login(@RequestBody @Valid LoginRequest req) {
        // 인증 로직은 UserAuthService에서 처리
        boolean success = userAuthService.login(req.getEmail(), req.getPassword());
        if (!success) {
            return ResponseEntity.status(401).body(null);
        }
        User user = userService.findByEmail(req.getEmail());
        // accessToken, refreshToken 동시 발급
        String refreshToken = tokenService.createRefreshToken(user);
        String accessToken = tokenService.createAccessToken(refreshToken);
        CreateAccessTokenResponse tokenDto = new CreateAccessTokenResponse(accessToken, refreshToken);
        return ResponseEntity.ok(tokenDto);
    }

    @Operation(summary = "로그아웃", description = "현재 로그인된 사용자를 로그아웃합니다.")
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(
                request,
                response,
                SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.ok("logout success");
    }

    @Operation(summary = "이메일 인증 코드 전송", description = "회원가입을 위해 이메일로 인증 코드를 전송합니다.")
    @PostMapping("/email/sendcode")
    // requestBody로 요청 값 매핑
    public ResponseEntity<EmailAuthSendResponse> sendSignupEmail(@RequestBody @Valid EmailAuthSendRequest req) {
        emailService.sendCode(req.getEmail());
        return ResponseEntity.ok(new EmailAuthSendResponse("인증 코드 발송 완료"));
    }

    @Operation(summary = "이메일 인증 코드 검증", description = "전송된 이메일 인증 코드를 검증합니다.")
    @PostMapping("/email/verify")
    public ResponseEntity<EmailAuthVerifyResponse> verifySignupEmail(
            @RequestBody @Valid EmailAuthVerifyRequest req) {
        boolean success = emailService.verifyCode(req.getEmail(), req.getAuthCode());
        if (success) {
            return ResponseEntity.ok(new EmailAuthVerifyResponse(true, "인증 성공"));
        } else {
            return ResponseEntity.badRequest().body(new EmailAuthVerifyResponse(false, "인증 실패"));
        }
    }

    @Operation(summary = "이메일 인증 후 회원가입", description = "이메일 인증 후 회원가입을 처리합니다.")
    @PostMapping("/email/signup")
    public ResponseEntity<CreateAccessTokenResponse> signup(@RequestBody @Valid SignupRequest dto) {
        // 이메일 인증 확인
        if (!emailService.isVerified(dto.getEmail())) {
            return ResponseEntity.badRequest().build();
        }
        userAuthService.signup(dto);
        // 회원가입 후 accessToken, refreshToken 동시 발급
        User user = userService.findByEmail(dto.getEmail());
        String refreshToken = tokenService.createRefreshToken(user);
        String accessToken = tokenService.createAccessToken(refreshToken);
        CreateAccessTokenResponse tokenDto = new CreateAccessTokenResponse(accessToken, refreshToken);
        return ResponseEntity.ok(tokenDto);
    }

    @Operation(summary = "카카오 로그인", description = "카카오 계정으로 로그인합니다.")
    @PostMapping("/kakao/login")
    public void kakaoLogin(HttpServletResponse response) throws IOException {
        String redirectUrl = kakaoAuthService.getOauthRedirectURL();
        response.sendRedirect(redirectUrl);
    }

    @Operation(summary = "카카오 토큰 생성", description = "카카오 로그인 후 콜백으로 전달된 코드를 통해 토큰을 생성합니다.")
    @GetMapping("/kakao/callback")
    public ResponseEntity<?> kakaoCallback(@RequestParam("code") String code) {
        // 1. access token 발급
        String kakaoAccessToken = kakaoAuthService.requestAccessToken(code);
        // 2. 사용자 정보 조회 및 회원가입/인증
        User user = kakaoAuthService.processKakaoUser(kakaoAccessToken);
        // 3. accessToken, refreshToken 동시 발급
        String refreshToken = tokenService.createRefreshToken(user);
        String accessToken = tokenService.createAccessToken(refreshToken);
        CreateAccessTokenResponse tokenDto = new CreateAccessTokenResponse(accessToken, refreshToken);
        return ResponseEntity.ok(tokenDto);
    }

    @Operation(summary = "카카오 로그아웃", description = "카카오 계정으로 로그인한 사용자를 로그아웃합니다.")
    @PostMapping("/kakao/logout")
    public ResponseEntity<String> kakaoLogout(@RequestParam("accessToken") String accessToken) {
        kakaoAuthService.logout();
        return ResponseEntity.ok("Kakao logout success");
    }
}

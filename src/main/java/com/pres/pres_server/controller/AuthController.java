package com.pres.pres_server.controller;

import com.pres.pres_server.dto.EmailAuth.EmailAuthSendRequest;
import com.pres.pres_server.dto.EmailAuth.EmailAuthSendResponse;
import com.pres.pres_server.dto.EmailAuth.EmailAuthVerifyRequest;
import com.pres.pres_server.dto.EmailAuth.EmailAuthVerifyResponse;
import com.pres.pres_server.dto.Login.LoginRequest;
import com.pres.pres_server.dto.Signup.SignupRequest;
import com.pres.pres_server.dto.Token.CreateAccessTokenResponse;
import com.pres.pres_server.dto.Token.KakaoTokenResponse;
import com.pres.pres_server.exception.KakaoBadRequestException;
import com.pres.pres_server.security.jwt.TokenProvider;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.service.email.EmailService;
import com.pres.pres_server.service.token.TokenService;
import com.pres.pres_server.service.user.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;

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

@Tag(name = "Auth", description = "인증 관련 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final EmailService emailService;
    private final UserAuthService userAuthService;
    private final KakaoOAuthService kakaoAuthService;
    private final UserService userService;
    private final TokenService tokenService;
    private final TokenProvider tokenProvider;

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateAccessTokenResponse.class), examples = @ExampleObject(name = "Successful Login", value = "{ \"accessToken\": \".accessToken value\", \"refreshToken\": \"refreshToken value\" }"))),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
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

    @Operation(summary = "로그아웃", description = "현재 로그인된 사용자를 로그아웃합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "성공", content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class), examples = @ExampleObject(name = "Successful Logout", value = "logout success"))),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(
                request,
                response,
                SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.ok("logout success");
    }

    @Operation(summary = "이메일 인증 코드 전송", description = "회원가입을 위해 이메일로 인증 코드를 전송합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmailAuthSendResponse.class), examples = @ExampleObject(name = "Successful Email Send", value = "{ \"message\": \"인증 코드 발송 완료\" }"))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @PostMapping("/email/sendcode")
    // requestBody로 요청 값 매핑
    public ResponseEntity<EmailAuthSendResponse> sendSignupEmail(@RequestBody @Valid EmailAuthSendRequest req) {
        emailService.sendCode(req.getEmail());
        return ResponseEntity.ok(new EmailAuthSendResponse("인증 코드 발송 완료"));
    }

    @Operation(summary = "이메일 인증 코드 검증", description = "전송된 이메일 인증 코드를 검증합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmailAuthVerifyResponse.class), examples = @ExampleObject(name = "Successful Verification", value = "{ \"success\": true, \"message\": \"인증 성공\" }"))),
            @ApiResponse(responseCode = "400", description = "인증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmailAuthVerifyResponse.class), examples = @ExampleObject(name = "Failed Verification", value = "{ \"success\": false, \"message\": \"인증 실패\" }")))
    })
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

    @Operation(summary = "이메일 인증 후 회원가입", description = "이메일 인증 후 회원가입을 처리합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateAccessTokenResponse.class), examples = @ExampleObject(name = "Successful Signup", value = "{ \"accessToken\": \".accessToken value\", \"refreshToken\": \"refreshToken value\" }"))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateAccessTokenResponse.class), examples = @ExampleObject(name = "Bad Request", value = "{ \"message\": \"잘못된 요청\" }")))
    })
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

    @Operation(summary = "카카오 로그인", description = "카카오 계정으로 로그인합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "성공", content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class), examples = @ExampleObject(name = "Successful Redirect", value = "Redirecting to Kakao OAuth"))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class), examples = @ExampleObject(name = "Failed Redirect", value = "Failed to redirect to Kakao OAuth"))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class), examples = @ExampleObject(name = "Bad Request", value = "Bad request")))
    })
    @PostMapping("/kakao/login")
    public void kakaoLogin(HttpServletResponse response) throws IOException {
        String redirectUrl = kakaoAuthService.getOauthRedirectURL();
        response.sendRedirect(redirectUrl);
    }

    @Operation(summary = "카카오 토큰 생성", description = "카카오 로그인 후 콜백으로 전달된 코드를 통해 토큰을 생성합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateAccessTokenResponse.class), examples = @ExampleObject(name = "Successful Token Creation", value = "{ \"accessToken\": \".accessToken value\", \"refreshToken\": \"refreshToken value\" }"))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class), examples = @ExampleObject(name = "Unauthorized", value = "Unauthorized"))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class), examples = @ExampleObject(name = "Bad Request", value = "Bad request")))
    })
    @GetMapping("/kakao/callback")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<?> kakaoCallback(@RequestParam("code") String code) {
        // 코드 유효성 검사
        if (code == null || code.isEmpty()) {
            throw new KakaoBadRequestException("코드가 올바르지 않습니다.");
        }
        // 1. 인가 코드로 카카오 토큰 정보(객체)를 발급받음
        KakaoTokenResponse kakaoTokenResponse = kakaoAuthService.getToken(code);

        // 2. 토큰 정보(객체)를 통해 사용자 정보 조회 및 회원가입/인증
        User user = kakaoAuthService.processKakaoUser(kakaoTokenResponse);

        // 3. JWT 토큰 발급 및 반환
        String refreshToken = tokenService.createRefreshToken(user);
        String accessToken = tokenService.createAccessToken(refreshToken);
        CreateAccessTokenResponse tokenDto = new CreateAccessTokenResponse(accessToken, refreshToken);

        return ResponseEntity.ok(tokenDto);
    }

    @Operation(summary = "카카오 로그아웃", description = "카카오 계정으로 로그인한 사용자를 리프레시 토큰을 무효화하는 방식으로 로그아웃합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "성공", content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class), examples = @ExampleObject(name = "Successful Kakao Logout", value = "Kakao logout success"))),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @PostMapping("/kakao/logout")
    public ResponseEntity<?> kakaoLogout(HttpServletRequest request, @RequestParam("accessToken") String accessToken) {
        // 1. 헤더에서 리프레시 토큰 추출
        String refreshToken = tokenProvider.resolveRefreshToken(request);

        // 2. 리프레시 토큰 무효화 (DB에서 삭제)
        tokenService.invalidateRefreshToken(refreshToken);

        return ResponseEntity.ok("Kakao logout success");
    }

}

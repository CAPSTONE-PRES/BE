package com.pres.pres_server.controller;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.EmailAuth.EmailAuthSendRequest;
import com.pres.pres_server.dto.EmailAuth.EmailAuthSendResponse;
import com.pres.pres_server.dto.EmailAuth.EmailAuthVerifyRequest;
import com.pres.pres_server.dto.EmailAuth.EmailAuthVerifyResponse;
import com.pres.pres_server.dto.ResetPassword.ResetPasswordEmailRequest;
import com.pres.pres_server.dto.ResetPassword.ResetPasswordRequest;
import com.pres.pres_server.dto.User.UserResponseDto;
import com.pres.pres_server.dto.User.UserUpdateDto;
import com.pres.pres_server.service.user.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ExampleObject;

import com.pres.pres_server.service.auth.KakaoOAuthService;
import com.pres.pres_server.service.email.EmailService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Optional;

@Tag(name = "User", description = "사용자 관련 API")
@RestController
@Slf4j
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final EmailService emailService;
    private final KakaoOAuthService kakaoAuthService;

    @Operation(summary = "내 정보 조회", description = "로그인된 사용자의 정보를 반환합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = UserResponseDto.class), examples = @ExampleObject(value = "{ \"id\": 1, \"email\": \"test@example.com\", \"username\": \"홍길동\", \"emailVerified\": true }"))),
            @ApiResponse(responseCode = "404", description = "사용자 정보 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "\"사용자 정보 없음\"")))
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getMyInfo(@AuthenticationPrincipal User user) {
        try {
            User myInfo = userService.getUser(user.getId());
            UserResponseDto dto = userService.toDto(myInfo);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "내 정보 수정", description = "로그인된 사용자의 정보를 수정합니다.")
    @PatchMapping("/me")
    public ResponseEntity<UserResponseDto> updateMyInfo(@AuthenticationPrincipal User user,
            @RequestBody UserUpdateDto updateUserDto) {
         User updatedUser = userService.updateUser(user.getId(), updateUserDto);
        UserResponseDto resp = userService.toDto(updatedUser);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "회원 탈퇴", description = "로그인된 사용자의 계정을 삭제합니다.")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyInfo(
            @AuthenticationPrincipal User user,
            HttpServletResponse response
    ) {
        userService.deleteUser(user.getId());
        //CookieUtil.deleteCookie(response, "refresh_token");
        return ResponseEntity.noContent().build();
    }

    /**
     * 내 프로필 이미지 업로드
     */
    @Operation(summary = "프로필 이미지 업로드", description = "로그인된 사용자의 프로필 이미지를 업로드합니다.")
    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponseDto> updateMyProfileImage(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) {

        // 파일 검증
        if (file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어 있습니다.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
        }

        // 파일 크기 검증 (선택)
        long maxSize = 5 * 1024 * 1024; // 5MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("파일 크기는 5MB 이하여야 합니다.");
        }

        User updatedUser = userService.updateProfileImage(user.getId(), file);
        return ResponseEntity.ok(userService.toDto(updatedUser));
    }

    /**
     * 내 프로필 이미지 삭제 (기본 이미지로 복원)
     */
    @Operation(summary = "프로필 이미지 삭제", description = "프로필 이미지를 기본 이미지로 복원합니다.")
    @DeleteMapping("/me/profile-image")
    public ResponseEntity<UserResponseDto> deleteMyProfileImage(
            @AuthenticationPrincipal User user) {

        User updatedUser = userService.deleteProfileImage(user.getId());
        return ResponseEntity.ok(userService.toDto(updatedUser));
    }

    // User 엔티티에 kakaoAccessToken/RefreshToken 필드 추가?
    // 현재는 임시로 주석 처리
    // @Operation(summary = "카카오 계정 연결 끊기", description = "로그인된 사용자의 카카오 계정 연결을
    // 끊습니다.", responses = {
    // @ApiResponse(responseCode = "204", description = "연결 끊기 성공")
    // })
    // @DeleteMapping("/me/kakao")
    // public ResponseEntity<Void> unlinkKakaoAccount(@AuthenticationPrincipal User
    // user) {
    // String kakaoAccessToken = user.getKakaoAccessToken(); // 또는 서비스에서 조회
    // kakaoAuthService.unlinkKakaoAccount(kakaoAccessToken);
    // return ResponseEntity.noContent().build();
    // }

    @Operation(summary = "특정 사용자 정보 조회 (관리자)", description = "관리자가 특정 사용자의 정보를 조회합니다.", parameters = {
            @Parameter(name = "id", description = "조회할 사용자 ID", required = true) }, responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = UserResponseDto.class), examples = @ExampleObject(value = "{ \"id\": 2, \"email\": \"admin@example.com\", \"username\": \"관리자\", \"emailVerified\": true }"))),
                    @ApiResponse(responseCode = "404", description = "사용자 정보 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "\"사용자 정보 없음\"")))

    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(userService.toDto(user));
    }

    @Operation(summary = "전체 회원 목록 조회 (관리자)", description = "관리자가 전체 회원 목록을 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponseDto.class)), examples = @ExampleObject(value = "[{ \"id\": 1, \"email\": \"test@example.com\", \"username\": \"홍길동\", \"emailVerified\": true }, { \"id\": 2, \"email\": \"admin@example.com\", \"username\": \"관리자\", \"emailVerified\": true }]")))
    })

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/list")
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        List<User> users = userService.listUsers();
        List<UserResponseDto> response = users.stream()
                .map(userService::toDto) // 메서드 레퍼런스 사용
                .toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "아이디(이메일) 찾기 인증코드 발송", description = "입력한 이메일로 인증코드를 발송합니다.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "이메일 정보", required = true, content = @Content(schema = @Schema(implementation = EmailAuthSendRequest.class))), responses = {
            @ApiResponse(responseCode = "200", description = "발송 성공", content = @Content(schema = @Schema(implementation = EmailAuthSendResponse.class), examples = @ExampleObject(value = "{ \"message\": \"입력하신 이메일로 인증코드를 발송했습니다.\" }")))
    })
    @PostMapping("/find-id/send-code")
    public ResponseEntity<EmailAuthSendResponse> sendFindIdCode(@RequestBody EmailAuthSendRequest req) {
        emailService.sendCode(req.getEmail());
        return ResponseEntity.ok(new EmailAuthSendResponse("입력하신 이메일로 인증코드를 발송했습니다."));
    }

    @Operation(summary = "아이디(이메일) 인증코드 검증", description = "입력한 인증코드를 검증합니다.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "이메일 및 인증코드", required = true, content = @Content(schema = @Schema(implementation = EmailAuthVerifyRequest.class))), responses = {
            @ApiResponse(responseCode = "200", description = "인증 성공", content = @Content(schema = @Schema(implementation = EmailAuthVerifyResponse.class), examples = @ExampleObject(value = "{ \"success\": true, \"message\": \"이메일 인증이 완료되었습니다. 안내 메일을 확인하세요.\" }"))),
            @ApiResponse(responseCode = "400", description = "인증 실패", content = @Content(schema = @Schema(implementation = EmailAuthVerifyResponse.class), examples = @ExampleObject(value = "{ \"success\": false, \"message\": \"인증 실패\" }")))
    })
    @PostMapping("/find-id/verify")
    public ResponseEntity<EmailAuthVerifyResponse> verifyFindId(@RequestBody EmailAuthVerifyRequest req) {
        boolean success = emailService.verifyCode(req.getEmail(), req.getAuthCode());
        if (success) {
            return ResponseEntity.ok(new EmailAuthVerifyResponse(true, "이메일 인증이 완료되었습니다. 안내 메일을 확인하세요."));
        } else {
            return ResponseEntity.badRequest().body(new EmailAuthVerifyResponse(false, "인증 실패"));
        }
    }

    @Operation(summary = "비밀번호 재설정 인증코드 발송", description = "비밀번호 재설정을 위한 인증코드를 이메일로 발송합니다.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "이메일 정보", required = true, content = @Content(schema = @Schema(implementation = ResetPasswordEmailRequest.class))), responses = {
            @ApiResponse(responseCode = "200", description = "발송 성공", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "\"인증코드 발송 완료\""))),
            @ApiResponse(responseCode = "404", description = "이메일 없음")
    })
    @PostMapping("/send-reset-code")
    public ResponseEntity<String> sendResetCode(@RequestBody ResetPasswordEmailRequest req) {
        Optional<User> user = userService.findOptionalByEmail(req.getEmail());
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        emailService.sendCode(req.getEmail());
        return ResponseEntity.ok("인증코드 발송 완료");
    }

    @Operation(summary = "비밀번호 재설정 인증코드 검증", description = "비밀번호 재설정 인증코드를 검증합니다.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "이메일 및 인증코드", required = true, content = @Content(schema = @Schema(implementation = EmailAuthVerifyRequest.class))), responses = {
            @ApiResponse(responseCode = "200", description = "인증 성공/실패", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "\"인증 성공\"")))
    })
    @PostMapping("/verify-reset-code")
    public ResponseEntity<String> verifyResetCode(@RequestBody EmailAuthVerifyRequest req) {
        boolean success = emailService.verifyCode(req.getEmail(), req.getAuthCode());
        return ResponseEntity.ok(success ? "인증 성공" : "인증 실패");
    }

    @Operation(summary = "비밀번호 재설정", description = "새 비밀번호로 변경합니다.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "이메일 및 새 비밀번호", required = true, content = @Content(schema = @Schema(implementation = ResetPasswordRequest.class))), responses = {
            @ApiResponse(responseCode = "200", description = "변경 성공", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "\"비밀번호 변경 완료\""))),
            @ApiResponse(responseCode = "400", description = "변경 실패", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "\"비밀번호 변경 실패: 비밀번호가 너무 짧습니다.\"")))
    })
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest req) {
        try {
            userService.updatePassword(req.getEmail(), req.getNewPassword());
            return ResponseEntity.ok("비밀번호 변경 완료");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("비밀번호 변경 실패: " + e.getMessage());
        }
    }
}

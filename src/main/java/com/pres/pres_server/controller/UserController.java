package com.pres.pres_server.controller;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.EmailAuth.EmailAuthSendRequest;
import com.pres.pres_server.dto.EmailAuth.EmailAuthSendResponse;
import com.pres.pres_server.dto.EmailAuth.EmailAuthVerifyRequest;
import com.pres.pres_server.dto.EmailAuth.EmailAuthVerifyResponse;
import com.pres.pres_server.dto.ResetPassword.ResetPasswordEmailRequest;
import com.pres.pres_server.dto.ResetPassword.ResetPasswordRequest;
import com.pres.pres_server.service.user.UserService;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.pres.pres_server.service.email.EmailService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@Tag(name = "User", description = "사용자 관련 API")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final EmailService emailService;

    // 로그인된 사용자 본인 정보 조회
    @GetMapping("/me")
    public ResponseEntity<User> getMyInfo(@AuthenticationPrincipal User user) {
        try {
            User myInfo = userService.getUser(user.getId());
            return ResponseEntity.ok(myInfo);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 로그인된 사용자 본인 정보 수정
    @PatchMapping("/me")
    public ResponseEntity<User> updateMyInfo(@AuthenticationPrincipal User user,
            @RequestBody com.pres.pres_server.dto.User.UserUpdateDto updateUserDto) {
        User updatedUser = userService.updateUser(user.getId(), updateUserDto);
        return ResponseEntity.ok(updatedUser);
    }

    // 본인 계정 삭제(탈퇴)
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(@AuthenticationPrincipal User user) {
        userService.deleteUser(user.getId());
        return ResponseEntity.noContent().build();
    }

    // 특정 사용자 정보 조회 (관리자용)
    // 관리자 권한이 필요
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    // 전체 회원 목록 조회 (관리자용)
    // 관리자 권한이 필요
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/list")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userService.listUsers();
        return ResponseEntity.ok(users);
    }

    // 아이디(이메일) 찾기: 이메일 입력 → 인증코드 발송(항상 성공 메시지 반환)
    @PostMapping("/find-id/send-code")
    public ResponseEntity<EmailAuthSendResponse> sendFindIdCode(@RequestBody EmailAuthSendRequest req) {
        emailService.sendCode(req.getEmail());
        return ResponseEntity.ok(new EmailAuthSendResponse("입력하신 이메일로 인증코드를 발송했습니다."));
    }

    // 인증 성공 시 안내 메시지 반환
    @PostMapping("/find-id/verify")
    public ResponseEntity<EmailAuthVerifyResponse> verifyFindId(@RequestBody EmailAuthVerifyRequest req) {
        boolean success = emailService.verifyCode(req.getEmail(), req.getAuthCode());
        if (success) {
            return ResponseEntity.ok(new EmailAuthVerifyResponse(true, "이메일 인증이 완료되었습니다. 안내 메일을 확인하세요."));
        } else {
            return ResponseEntity.badRequest().body(new EmailAuthVerifyResponse(false, "인증 실패"));
        }
    }

    // 비밀번호 재설정: 인증코드 발송
    @PostMapping("/send-reset-code")
    public ResponseEntity<String> sendResetCode(@RequestBody ResetPasswordEmailRequest req) {
        // 기존 EmailService 활용
        // 이메일이 존재하는지 확인
        User user = userService.findByEmail(req.getEmail());
        if (user == null)
            return ResponseEntity.notFound().build();
        // 인증코드 발송
        emailService.sendCode(req.getEmail());
        return ResponseEntity.ok("인증코드 발송 완료");
    }

    // 비밀번호 재설정: 인증코드 검증
    @PostMapping("/verify-reset-code")
    public ResponseEntity<String> verifyResetCode(@RequestBody EmailAuthVerifyRequest req) {
        boolean success = emailService.verifyCode(req.getEmail(), req.getAuthCode());
        return ResponseEntity.ok(success ? "인증 성공" : "인증 실패");
    }

    // 비밀번호 재설정: 새 비밀번호 설정
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

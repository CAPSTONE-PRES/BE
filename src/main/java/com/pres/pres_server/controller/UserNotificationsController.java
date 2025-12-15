package com.pres.pres_server.controller;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.UserNotificationsDto;
import com.pres.pres_server.dto.UserNotificationsUpdateDto;
import com.pres.pres_server.service.user.UserNotificationsService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me/email-notifications")
public class UserNotificationsController {

    private final UserNotificationsService service;

    @Operation(summary = "내 이메일 알림 설정 조회")
    @GetMapping
    public ResponseEntity<UserNotificationsDto> getMyNotifications(@AuthenticationPrincipal User user) {
        UserNotificationsDto dto = service.getForUserReadOnly(user.getId());
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "내 이메일 알림 설정 수정")
    @PatchMapping
    public ResponseEntity<UserNotificationsDto> updateMyNotifications(@AuthenticationPrincipal User user,
            @RequestBody UserNotificationsUpdateDto req) {
        UserNotificationsDto updated = service.updateForUser(user.getId(), req);
        return ResponseEntity.ok(updated);
    }
}

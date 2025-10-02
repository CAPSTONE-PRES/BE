package com.pres.pres_server.controller;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.Workspace.WorkspaceRequest;
import com.pres.pres_server.service.ProjectService;
import com.pres.pres_server.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspace")
@RequiredArgsConstructor
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    @PostMapping("/create")
    public ResponseEntity<?> createWorkspace(
            @RequestBody WorkspaceRequest request,
            @AuthenticationPrincipal User user) {
        System.out.println("로그인한 유저 이메일: " + user.getEmail());
        return ResponseEntity.ok(workspaceService.createWorkspace(request, user));
    }

}

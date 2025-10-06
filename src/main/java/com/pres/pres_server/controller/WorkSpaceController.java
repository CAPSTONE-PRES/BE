package com.pres.pres_server.controller;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.Workspace.WorkspaceInfoDTO;
import com.pres.pres_server.dto.Workspace.WorkspaceRequest;
import com.pres.pres_server.service.WorkSpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workspace")
@RequiredArgsConstructor
public class WorkSpaceController {

    private final WorkSpaceService workSpaceService;

    @PostMapping("/create")
    public ResponseEntity<?> createWorkspace(
            @RequestBody WorkspaceRequest request,
            @AuthenticationPrincipal User user) {
        System.out.println("로그인한 유저 이메일: " + user.getEmail());
        return ResponseEntity.ok(workSpaceService.createWorkspace(request, user));
    }

    @GetMapping("/{workspaceId}/info")
    public WorkspaceInfoDTO getWorkspaceInfo(@PathVariable Long workspaceId,@AuthenticationPrincipal User user) {
        return workSpaceService.getWorkspaceInfo(user, workspaceId);
    }
}
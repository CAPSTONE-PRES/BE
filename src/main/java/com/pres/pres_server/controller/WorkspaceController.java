package com.pres.pres_server.controller;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.Workspace.WorkspaceInfoDTO;
import com.pres.pres_server.dto.Workspace.WorkspaceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.pres.pres_server.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Tag(name = "Workspace Controller", description = "워크스페이스 관련 API")
@RestController
@RequestMapping("/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @Operation(summary = "워크스페이스 생성", description = "워크스페이스 생성에 필요한 정보를 저장합니다")
    @PostMapping("/create")
    public ResponseEntity<?> createWorkspace(
            @RequestBody WorkspaceRequest request,
            @AuthenticationPrincipal User user) {
        System.out.println("로그인한 유저 이메일: " + user.getEmail());
        return ResponseEntity.ok(workspaceService.createWorkspace(request, user));
    }

    @Operation(summary = "워크스페이스 정보 반환", description = "워크스페이스에 저장된 정보를 불러옵니다.")
    @GetMapping("/{workspaceId}/info")
    public WorkspaceInfoDTO getWorkspaceInfo(@PathVariable Long workspaceId,@AuthenticationPrincipal User user) {
        return workspaceService.getWorkspaceInfo(user, workspaceId);
    }

    @Operation(summary = "워크스페이스 리스트", description = "모든 워크플레이스 불러오기 (type값 1은 최근방문 순, 2는 제목순)")
    @GetMapping("/list")
    public List<WorkspaceInfoDTO> getWorkspaceList(
            @RequestParam int type,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        return workspaceService.getWorkspaceList(user, type);
    }

}
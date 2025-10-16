package com.pres.pres_server.controller;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.domain.WorkSpace;
import com.pres.pres_server.dto.Workspace.TeamMemberEditRequest;
import com.pres.pres_server.dto.Workspace.WorkspaceInfoDTO;
import com.pres.pres_server.dto.Workspace.WorkspaceRequest;
import com.pres.pres_server.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.pres.pres_server.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Workspace Controller", description = "워크스페이스 관련 API")
@RestController
@RequestMapping("/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final UserService userService;

    @Operation(summary = "워크스페이스 생성", description = "워크스페이스 생성에 필요한 정보를 저장합니다")
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createWorkspace(
            @RequestBody WorkspaceRequest request,
            @AuthenticationPrincipal User ownerUser) {

        Long workspaceId = workspaceService.createWorkspace(request, ownerUser);

        Map<String, Object> response = new HashMap<>();
        response.put("workspaceId", workspaceId);
        response.put("message", "워크스페이스가 성공적으로 생성되었습니다.");

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{workspaceId}/teammember/edit")
    @Operation(summary = "워크스페이스 팀 멤버 수정", description = "이메일 리스트로 팀 멤버를 덮어씌웁니다")
    public ResponseEntity<String> editTeamMembers(
            @PathVariable Long workspaceId,
            @RequestBody TeamMemberEditRequest request,
            @AuthenticationPrincipal User user) {

        workspaceService.editTeamMembers(workspaceId, request, user);
        return ResponseEntity.ok("워크스페이스 멤버가 성공적으로 업데이트되었습니다.");
    }

    @PatchMapping("/{workspaceId}/update")
    @Operation(summary = "워크스페이스 수정", description = "워크스페이스 정보를 수정합니다")
    public ResponseEntity<Map<String, Object>> editWorkspace(
            @PathVariable Long workspaceId,
            @RequestBody WorkspaceRequest request,
            @AuthenticationPrincipal User user) {

        workspaceService.editWorkspace(workspaceId, request, user);

        Map<String, Object> response = new HashMap<>();
        response.put("workspaceId", workspaceId);
        response.put("status", "워크스페이스 정보가 성공적으로 수정되었습니다.");

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{workspaceId}/delete")
    @Operation(summary = "워크스페이스 삭제", description = "워크스페이스를 삭제합니다")
    public ResponseEntity<Map<String, Object>> deleteWorkspace(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal User user) {

        workspaceService.deleteWorkspace(workspaceId, user);

        Map<String, Object> response = new HashMap<>();
        response.put("workspaceId", workspaceId);
        response.put("status", "워크스페이스가 성공적으로 삭제되었습니다.");

        return ResponseEntity.ok(response);
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
package com.pres.pres_server.controller;

import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.domain.WorkSpace;
import com.pres.pres_server.dto.practice.CueCardUncheckedDTO;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.TeamMemberRepository;
import com.pres.pres_server.service.CueCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "CueCard Controller", description = "큐카드 및 발표자료 관련 API")
@RequestMapping("/projects")
public class CueCardController {

    private final CueCardService cueCardService;
    private final PresentationFileRepository presentationFileRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Operation(summary = "큐카드 체크/취소 api", description = "슬라이드별 큐카드(1,2)에 대한 체크 표시 생성 및 삭제")
    @PatchMapping("/{fileId}/{slideNumber}/cuecard/{cueId}/check")
    public ResponseEntity<Map<String,Object>> toggleCueCheck(
            @PathVariable Long fileId,
            @PathVariable int slideNumber,
            @PathVariable Long cueId,
            @RequestParam String status, // on 또는 off
            @AuthenticationPrincipal User user) {

        if (!"on".equalsIgnoreCase(status) && !"off".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status는 on 또는 off여야 합니다.");
        }
        boolean checked = "on".equalsIgnoreCase(status);
        cueCardService.setCheckStatus(fileId, slideNumber, cueId, user, checked);

        Map<String,Object> resp = new HashMap<>();
        resp.put("cueId", cueId);
        resp.put("status", checked ? "on" : "off");
        resp.put("message", "체크 상태가 업데이트되었습니다.");
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "큐카드 체크 안 한 멤버 조회", description = "슬라이드별 큐카드 체크 여부 확인")
    @GetMapping("/projects/{fileId}/{slideNumber}/cuecard/check/list")
    public ResponseEntity<CueCardUncheckedDTO> getUncheckedMembers(
            @PathVariable Long fileId,
            @PathVariable int slideNumber,
            @AuthenticationPrincipal User user) {

        PresentationFile file = presentationFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("해당 파일이 존재하지 않습니다."));

        WorkSpace workspace = file.getProject().getWorkspaceId();

        // 로그인한 user가 워크스페이스 멤버인지 확인
        boolean isMember = teamMemberRepository.existsByWorkspace_WorkspaceIdAndUser_Id(
                workspace.getWorkspaceId(), user.getId()
        );

        if (!isMember) {
            throw new AccessDeniedException("워크스페이스 멤버만 접근할 수 있습니다.");
        }

        CueCardUncheckedDTO response = cueCardService.getUncheckedMembers(fileId, slideNumber);
        return ResponseEntity.ok(response);
    }

}

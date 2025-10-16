package com.pres.pres_server.controller;

import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.domain.WorkSpace;
import com.pres.pres_server.dto.Comment.CommentRequestDTO;
import com.pres.pres_server.dto.Comment.CommentResponseDTO;
import com.pres.pres_server.dto.CueCard.CueCardCommentDTO;
import com.pres.pres_server.dto.CueCard.CueCardCreateResponseDTO;
import com.pres.pres_server.dto.CueCard.CueCardUpdateRequest;
import com.pres.pres_server.dto.CueCard.CueCardUpdateResponseDTO;
import com.pres.pres_server.dto.practice.CueCardUncheckedDTO;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.TeamMemberRepository;
import com.pres.pres_server.service.CommentService;
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
@Tag(name = "CueCard & Comment Controller", description = "큐카드 및 코멘트 관련 API")
@RequestMapping("/projects")
public class CueCardController {

    private final CueCardService cueCardService;
    private final PresentationFileRepository presentationFileRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CommentService commentService;

    @Operation(summary = "큐카드 내용 조회", description = "슬라이드별 큐카드 내용 조회")
    @GetMapping("/{fileId}/{slideNumber}/cuecard")
    public ResponseEntity<CueCardCreateResponseDTO> getCueCards(
            @PathVariable Long fileId,
            @PathVariable int slideNumber,
            @AuthenticationPrincipal User user) {

        CueCardCreateResponseDTO response = cueCardService.getCueCards(fileId, slideNumber, user);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "큐카드 내용 업데이트",
            description = "슬라이드별 큐카드 내용 수정 / 특별한 엔드포인트 발견하지 못해서, 페이지 넘길 때마다 #1, #2 넣어서 호출해주시면 됩니다")
    @PatchMapping("/{fileId}/{slideNumber}/cuecard/edit")
    public ResponseEntity<CueCardUpdateResponseDTO> updateCueCards(
            @PathVariable Long fileId,
            @PathVariable int slideNumber,
            @RequestBody CueCardUpdateRequest request,
            @AuthenticationPrincipal User user) {

        CueCardUpdateResponseDTO response = cueCardService.updateCueCards(fileId, slideNumber, request, user);
        return ResponseEntity.ok(response);
    }

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
    @GetMapping("/{fileId}/{slideNumber}/cuecard/check/list")
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

    // ----------------------------------- 코멘트 api -----------------------------------

    // 코멘트 생성 api
    @Operation(summary = "코멘트 생성", description = "특정 키워드에 코멘트를 생성합니다.")
    @PostMapping("/comment/create")
    public ResponseEntity<CommentResponseDTO> addComment(
            @RequestBody CommentRequestDTO request,
            @AuthenticationPrincipal User user) {

        CommentResponseDTO response = commentService.addComment(request, user);
        return ResponseEntity.ok(response);
    }

    // 코멘트 수정 api
    @Operation(summary = "코멘트 수정", description = "본인이 작성한 코멘트를 수정합니다.")
    @PatchMapping("/comment/{commentId}/update")
    public ResponseEntity<CommentResponseDTO> updateComment(
            @PathVariable Long commentId,
            @RequestBody CommentRequestDTO request,
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(commentService.updateComment(commentId, request, user));
    }

    // 코멘트 삭제 api
    @Operation(summary = "코멘트 삭제", description = "본인이 작성한 코멘트를 삭제합니다.")
    @DeleteMapping("/comment/{commentId}/delete")
    public ResponseEntity<CommentResponseDTO> deleteComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(commentService.deleteComment(commentId, user));
    }

    // 코멘트 슬라이드별 리스트로 불러오기 api
    @Operation(summary = "코멘트 리스트 불러오기", description = "특정 슬라이드에 해당하는 코멘트 리스트 불러오기.")
    @GetMapping("/{fileId}/{slideNumber}/comment/list")
    public ResponseEntity<List<CueCardCommentDTO>> getCommentsBySlide(
            @PathVariable Long fileId,
            @PathVariable int slideNumber,
            @AuthenticationPrincipal User user) {

        List<CueCardCommentDTO> comments = commentService.getCommentsBySlide(fileId, slideNumber, user);
        return ResponseEntity.ok(comments);
    }

}

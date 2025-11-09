package com.pres.pres_server.controller;

import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.domain.WorkSpace;
import com.pres.pres_server.dto.Comment.CommentRequestDTO;
import com.pres.pres_server.dto.Comment.CommentResponseDTO;
import com.pres.pres_server.dto.Comment.ReplyDTO;
import com.pres.pres_server.dto.CueCard.CueCardCommentDTO;
import com.pres.pres_server.dto.CueCard.CueCardCreateResponseDTO;
import com.pres.pres_server.dto.CueCard.CueCardUpdateRequest;
import com.pres.pres_server.dto.CueCard.CueCardUpdateResponseDTO;
import com.pres.pres_server.dto.practice.CueCardUncheckedDTO;
import com.pres.pres_server.repository.CueCardRepository;
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
    private final CueCardRepository cueCardRepository;

    @Operation(summary = "큐카드 내용 조회", description = "슬라이드별 큐카드 내용 조회")
    @GetMapping("/{fileId}/{slideNumber}/cuecard")
    public ResponseEntity<CueCardCreateResponseDTO> getCueCards(
            @PathVariable Long fileId,
            @PathVariable int slideNumber,
            @AuthenticationPrincipal User user) {

        CueCardCreateResponseDTO response = cueCardService.getCueCards(fileId, slideNumber, user);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "큐카드 + 비언어적 요소 조회", description = "슬라이드별 큐카드 내용 조회 (비언어적 요소 on/off)")
    @GetMapping("/{fileId}/{slideNumber}/cuecard/nonverbal")
    public ResponseEntity<CueCardCreateResponseDTO> getCueCardsNonVerbal(
            @PathVariable Long fileId,
            @PathVariable int slideNumber,
            @RequestParam String type,
            @AuthenticationPrincipal User user) {

        CueCardCreateResponseDTO response = cueCardService.getCueCardsNonVerbal(fileId, slideNumber, type, user);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "큐카드 내용 업데이트 (cueId + mode 기준)")
    @PatchMapping("/{cueId}/update")
    public ResponseEntity<CueCardUpdateResponseDTO> updateCueCardContent(
            @PathVariable Long cueId,
            @RequestBody CueCardUpdateRequest request,
            @AuthenticationPrincipal User user
    ) {
        try {
            CueCardUpdateResponseDTO response = cueCardService.updateCueCardMode(cueId, request, user);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CueCardUpdateResponseDTO.builder()
                            .message("예상치 못한 오류가 발생했습니다")
                            .build());
        }
    }

    @Operation(summary = "큐카드 체크/취소 api", description = "슬라이드별 큐카드(1,2)에 대한 체크 표시 생성 및 삭제")
    @PatchMapping("/cuecard/{cueId}/check")
    public ResponseEntity<Map<String,Object>> toggleCueCheck(
            @PathVariable Long cueId,
            @RequestParam String status, // on 또는 off
            @AuthenticationPrincipal User user) {

        if (!"on".equalsIgnoreCase(status) && !"off".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status는 on 또는 off여야 합니다.");
        }
        boolean checked = "on".equalsIgnoreCase(status);
        cueCardService.setCheckStatus(cueId, user, checked);

        Map<String,Object> resp = new HashMap<>();
        resp.put("cueId", cueId);
        resp.put("status", checked ? "on" : "off");
        resp.put("message", "체크 상태가 업데이트되었습니다.");
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "큐카드 체크 안 한 멤버 조회", description = "슬라이드별 큐카드 체크 여부 확인")
    @GetMapping("/cuecard/{cueId}/check/list")
    public ResponseEntity<CueCardUncheckedDTO> getUncheckedMembers(
            @PathVariable Long cueId,
            @AuthenticationPrincipal User user) {

        CueCard cueCard = cueCardRepository.findByCueId(cueId)
                .orElseThrow(() -> new IllegalArgumentException("해당 큐카드가 존재하지 않습니다."));

        WorkSpace workspace = cueCard.getPresentationFile().getProject().getWorkspaceId();

        // 로그인한 user가 워크스페이스 멤버인지 확인
        boolean isMember = teamMemberRepository.existsByWorkspace_WorkspaceIdAndUser_Id(
                workspace.getWorkspaceId(), user.getId()
        );

        if (!isMember) {
            throw new AccessDeniedException("워크스페이스 멤버만 접근할 수 있습니다.");
        }

        CueCardUncheckedDTO response = cueCardService.getUncheckedMembers(cueId);
        return ResponseEntity.ok(response);
    }

    // ----------------------------------- 코멘트 api -----------------------------------
    @Operation(summary = "큐카드에 최상위 댓글 생성")
    @PostMapping("/{cueId}/comments")
    public CommentResponseDTO createComment(@PathVariable Long cueId,
                                            @RequestBody CommentRequestDTO request,
                                            @AuthenticationPrincipal User user) {
        return commentService.createComment(cueId, request, user);
    }

    @Operation(summary = "최상위 댓글에 대댓글 생성")
    @PostMapping("/comments/{parentCommentId}/replies")
    public ReplyDTO createReply(@PathVariable Long parentCommentId,
                                @RequestBody CommentRequestDTO request,
                                @AuthenticationPrincipal User user) {
        return commentService.createReply(parentCommentId, request, user);
    }

    @Operation(summary = "특정 코멘트 수정")
    @PatchMapping("/comments/{commentId}")
    public CommentResponseDTO updateComment(@PathVariable Long commentId,
                                            @RequestBody CommentRequestDTO request,
                                            @AuthenticationPrincipal User user) {
        return commentService.updateComment(commentId, request, user);
    }

    @Operation(summary = "특정 코멘트 삭제")
    @DeleteMapping("/comments/{commentId}")
    public void deleteComment(@PathVariable Long commentId,
                              @AuthenticationPrincipal User user) {
        commentService.deleteComment(commentId, user);
    }

    @Operation(summary = "특정 cuecard에 코멘트 전체 불러오기")
    @GetMapping("/{cueId}/comments")
    public List<CommentResponseDTO> getComments(@PathVariable Long cueId,
                                                @AuthenticationPrincipal User user) {
        return commentService.getComments(cueId, user);
    }

    /*

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

     */

}

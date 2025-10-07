package com.pres.pres_server.controller;

import com.pres.pres_server.dto.practice.PracticeFeedbackDto;
import com.pres.pres_server.dto.practice.PracticeSessionStartDto;
import com.pres.pres_server.service.practice.PracticeSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Tag(name = "Practice Session Controller", description = "연습 세션 관리 API")
@RestController
@RequestMapping("/practice")
@RequiredArgsConstructor
public class PracticeSessionController {

    private final PracticeSessionService practiceSessionService;

    /**
     * 연습 세션 시작
     * 
     * @param projectId 연습할 프로젝트 ID
     * @return 세션 ID, 슬라이드 정보, 큐카드 목록
     */
    @Operation(summary = "연습 세션 시작", description = "새로운 연습 세션을 시작하고 슬라이드와 큐카드 정보를 반환합니다.")
    @PostMapping("/start")
    public ResponseEntity<PracticeSessionStartDto> startSession(
            @RequestParam("projectId") @NotNull Long projectId) {

        log.info("▶ 연습 세션 시작 요청 - projectId: {}", projectId);

        PracticeSessionStartDto response = practiceSessionService.startSession(projectId);

        log.info("✅ 연습 세션 생성 완료 - sessionId: {}, slides: {} 개",
                response.getSessionId(), response.getSlides().size());

        return ResponseEntity.ok(response);
    }

    /**
     * 연습 세션 종료 및 오디오 업로드
     * 
     * @param sessionId 종료할 세션 ID
     * @param audioFile 녹음된 전체 오디오 파일
     * @return 세션 ID
     */
    @Operation(summary = "연습 세션 종료", description = "연습 세션을 종료하고 녹음된 오디오를 업로드 및 분석합니다.")
    @PostMapping(value = "/{sessionId}/end", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Long> endSession(
            @PathVariable("sessionId") Long sessionId,
            @RequestParam("audio") MultipartFile audioFile) throws Exception {

        log.info("▶ 연습 세션 종료 요청 - sessionId: {}, audioFile: {}",
                sessionId, audioFile.getOriginalFilename());

        Long completedSessionId = practiceSessionService.endSession(sessionId, audioFile);

        log.info("✅ 연습 세션 종료 완료 - sessionId: {}", completedSessionId);

        return ResponseEntity.ok(completedSessionId);
    }

    /**
     * 연습 세션 피드백 조회
     * 
     * @param sessionId 조회할 세션 ID
     * @return 피드백 정보
     */
    @Operation(summary = "연습 세션 피드백 조회", description = "연습 세션의 피드백을 조회합니다.")
    @GetMapping("/{sessionId}/feedback")
    public ResponseEntity<PracticeFeedbackDto> getFeedback(
            @PathVariable("sessionId") Long sessionId) {

        log.info("▶ 피드백 조회 요청 - sessionId: {}", sessionId);

        PracticeFeedbackDto feedback = practiceSessionService.getFeedback(sessionId);

        log.info("✅ 피드백 조회 완료 - sessionId: {}, grade: {}", sessionId, feedback.getGrade());

        return ResponseEntity.ok(feedback);
    }
}

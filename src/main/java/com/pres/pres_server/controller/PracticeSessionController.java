package com.pres.pres_server.controller;

import com.pres.pres_server.dto.practice.PracticeFeedbackDto;
import com.pres.pres_server.dto.practice.PracticeSessionStartDto;
import com.pres.pres_server.dto.qna.QnaAnswerResponseDto;
import com.pres.pres_server.dto.qna.QnaQuestionDto;
import com.pres.pres_server.dto.qna.QnaComparisonDto;
import com.pres.pres_server.service.practice.PracticeQnaService;
import com.pres.pres_server.service.practice.PracticeSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

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
        private final PracticeQnaService practiceQnaService;

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
                        @RequestParam("audio") MultipartFile audioFile) {

                log.info("▶ 연습 세션 종료 요청 - sessionId: {}, audioFile: {}",
                                sessionId, audioFile.getOriginalFilename());

                Long completedSessionId = practiceSessionService.endSession(sessionId, audioFile);

                log.info("✅ 연습 세션 종료 완료 - sessionId: {}", completedSessionId);

                return ResponseEntity.ok(completedSessionId);
        }

        @Operation(summary = "QnA 질문 조회", description = "연습 세션의 예상 질문을 랜덤으로 조회합니다. 남은 질문이 없으면 204 응답을 반환합니다.")
        @GetMapping("/{sessionId}/qna-question")
        public ResponseEntity<QnaQuestionDto> getQuestion(
                        @PathVariable("sessionId") Long sessionId) {

                log.info("▶ QnA 질문 요청(랜덤 미응답 질문) - sessionId: {}", sessionId);

                QnaQuestionDto question = practiceQnaService.getRandomUnansweredQuestion(sessionId);

                if (question == null) {
                        log.info("ℹ️ 남은 QnA 질문이 없습니다 - sessionId: {}", sessionId);
                        return ResponseEntity.noContent().build();
                }

                log.info("✅ QnA 질문 반환 완료 - sessionId: {}, questionId: {}",
                                sessionId, question.getQuestionId());

                return ResponseEntity.ok(question);
        }

        @Operation(summary = "QnA 답변 업로드", description = "특정 질문에 대한 사용자의 음성 답변을 업로드하고 STT 결과를 반환합니다.")
        @PostMapping(value = "/{sessionId}/qna/{questionId}/answer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<QnaAnswerResponseDto> uploadAnswer(
                        @PathVariable("sessionId") Long sessionId,
                        @PathVariable("questionId") Long questionId,
                        @RequestParam("audioFile") MultipartFile audioFile) throws Exception {

                log.info("▶ QnA 답변 업로드 요청 - sessionId: {}, questionId: {}, audioFile: {}",
                                sessionId, questionId, audioFile.getOriginalFilename());

                QnaAnswerResponseDto response = practiceQnaService.submitAnswer(sessionId, questionId, audioFile);

                log.info("✅ QnA 답변 업로드 완료 - sessionId: {}, questionId: {}, answerId: {}",
                                sessionId, questionId, response.getAnswerId());

                return ResponseEntity.ok(response);
        }

        @Operation(summary = "QnA 질문 비교 실행", description = "특정 세션과 질문에 대해 사용자의 답변과 모범답안을 비교하고 결과를 반환합니다.")
        @PostMapping(value = "/{sessionId}/qna/{questionId}/compare")
        public ResponseEntity<QnaComparisonDto> compareQnaAnswer(
                        @PathVariable("sessionId") Long sessionId,
                        @PathVariable("questionId") Long questionId,
                        @RequestParam(value = "answerId", required = false) Long answerId) {
                try {
                        log.info("▶ QnA 비교(분석+ 분석 결과 저장) 요청 - sessionId: {}, questionId: {}, answerId: {}", sessionId,
                                        questionId, answerId);
                        QnaComparisonDto result = practiceQnaService.runAndSaveQuestionComparison(sessionId,
                                        questionId, answerId);
                        log.info("✅ QnA 비교(분석+ 분석 결과 저장) 완료 - sessionId: {}, questionId: {}, comparisonId: {}",
                                        sessionId, questionId, result.getComparisonId());
                        return ResponseEntity.ok(result);
                } catch (Exception e) {
                        log.error("QnA 비교 실패 - sessionId: {} questionId: {}", sessionId, questionId, e);
                        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(null);
                }
        }

        @Deprecated
        @Operation(summary = "질문 단위 QnA 피드백 조회", description = "DEPRECATED")
        @GetMapping("/{sessionId}/qna/{questionId}/feedback")
        public ResponseEntity<QnaComparisonDto> getQuestionFeedback(
                        @PathVariable("sessionId") Long sessionId,
                        @PathVariable("questionId") Long questionId) {

                log.info("▶ 질문 피드백 요청 - sessionId: {}, questionId: {}", sessionId, questionId);

                QnaComparisonDto comparison = practiceQnaService.getSavedQuestionComparison(sessionId, questionId);

                log.info("✅ 질문 피드백(DB) 반환 - sessionId: {}, questionId: {}, comparisonId: {}",
                                sessionId, questionId, comparison.getComparisonId());

                return ResponseEntity.ok(comparison);
        }

        /**
         * 연습 세션 피드백 조회
         * 
         * @param sessionId 조회할 세션 ID
         * @return 피드백 정보 (발표 피드백 + QnA 비교 결과)
         */
        @Operation(summary = "연습 세션 피드백 조회", description = "연습 세션의 피드백을 조회합니다. QnA 답변을 제출한 경우 비교 결과도 함께 포함됩니다.")
        @GetMapping("/{sessionId}/feedback")
        public ResponseEntity<PracticeFeedbackDto> getFeedback(
                        @PathVariable("sessionId") Long sessionId) {

                log.info("▶ 피드백 조회 요청 - sessionId: {}", sessionId);

                PracticeFeedbackDto feedback = practiceSessionService.getFeedback(sessionId);

                log.info("✅ 피드백 조회 완료 - sessionId: {}, grade: {}", sessionId, feedback.getGrade());

                log.info("Returning totalDurationSeconds to client: {}", feedback.getTotalDurationSeconds());

                return ResponseEntity.ok(feedback);
        }

        @Operation(summary = "세션의 QnA 비교(피드백) 조회", description = "세션에 저장된 QnA 비교 결과(피드백)를 반환합니다. 세션당 하나의 비교 결과만 존재합니다.")
        @GetMapping("/{sessionId}/qna/feedback")
        public ResponseEntity<QnaComparisonDto> getSessionQnaFeedback(
                        @PathVariable("sessionId") Long sessionId) {
                try {
                        log.info("▶ 세션의 QnA 피드백 요청 - sessionId: {}", sessionId);
                        List<QnaComparisonDto> list = practiceQnaService
                                        .getAllComparisons(sessionId);
                        if (list == null || list.isEmpty()) {
                                log.info("ℹ️ 세션에 저장된 QnA 피드백 없음 - sessionId: {}", sessionId);
                                return ResponseEntity.noContent().build();
                        }
                        com.pres.pres_server.dto.qna.QnaComparisonDto dto = list.get(0);
                        log.info("✅ QnA 피드백 반환 - sessionId: {}, comparisonId: {}", sessionId,
                                        dto == null ? null : dto.getComparisonId());
                        return ResponseEntity.ok(dto);
                } catch (Exception e) {
                        log.error("QnA 피드백 조회 실패 - sessionId: {}", sessionId, e);
                        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(null);
                }
        }
}

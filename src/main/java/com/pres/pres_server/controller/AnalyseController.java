package com.pres.pres_server.controller;

import com.pres.pres_server.service.analyse.AudioAnalysisService;
import com.pres.pres_server.service.analyse.dto.SlideTransition;
import com.pres.pres_server.service.analyse.AudioAnalysisService.AnalysisResult;
import com.pres.pres_server.dto.analyse.AnalysisResponseDto;
import com.pres.pres_server.dto.analyse.WindowDto;
import com.pres.pres_server.service.analyse.AnalysisResultService;
import com.pres.pres_server.service.analyse.TestRepetitiveService;
import com.pres.pres_server.repository.CueCardRepository;
import com.pres.pres_server.domain.CueCard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "Analyse", description = "음성 분석 API")
@RestController
@RequestMapping("/analyse")
@CrossOrigin(origins = "http://localhost:5173", // 허용할 출처
                allowedHeaders = "*")
public class AnalyseController {

        @Operation(summary = "오디오 분석 (테스트용)", description = "오디오 파일을 업로드하면 분석 결과를 반환합니다.")
        @PostMapping(value = "/test-audio", consumes = "multipart/form-data")
        public ResponseEntity<?> testAudioAnalysis(
                        @RequestPart("audioFile") MultipartFile audioFile,
                        @RequestParam(value = "projectId", required = false) Long projectId) {
                try {
                        String originalName = audioFile.getOriginalFilename();
                        log.info("[TEST] Audio analysis test request: {} (size={} bytes)", originalName,
                                        audioFile.getSize());
                        // 슬라이드별 반복 분석만 필요하므로 대본(cueCards) 조회 없이 호출
                        AnalysisResult result = audioAnalysisService.analyzeAudio(audioFile, projectId, null, null);
                        log.info("[TEST] Audio analysis test complete: {} (duration={}s, windows={})", originalName,
                                        result.getTotalDurationSeconds(), result.getWindows().size());
                        return ResponseEntity.ok(result);
                } catch (Exception e) {
                        log.error("[TEST] Audio analysis test failed", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of("error", e.getMessage()));
                }
        }

        private static final Logger log = LoggerFactory.getLogger(AnalyseController.class);

        private final AudioAnalysisService audioAnalysisService;
        private final AnalysisResultService analysisResultService;
        private final CueCardRepository cueCardRepository;
        private final TestRepetitiveService testRepetitiveService;

        public AnalyseController(
                        AudioAnalysisService audioAnalysisService,
                        AnalysisResultService analysisResultService,
                        CueCardRepository cueCardRepository,
                        TestRepetitiveService testRepetitiveService) {
                this.audioAnalysisService = audioAnalysisService;
                this.analysisResultService = analysisResultService;
                this.cueCardRepository = cueCardRepository;
                this.testRepetitiveService = testRepetitiveService;
        }

        @Operation(summary = "오디오 분석 (파일 업로드)", description = "오디오 파일을 업로드하고 분석 결과를 반환합니다.")
        @PostMapping(consumes = "multipart/form-data")
        public ResponseEntity<AnalysisResponseDto> analyse(
                        @RequestPart("audio") MultipartFile audioFile,
                        @RequestParam("projectId") Long projectId,
                        @RequestParam(value = "slideTransitions", required = false) String slideTransitionsJson) {

                try {
                        log.info("▶ Analysis request received: file='{}', size={} bytes, projectId={}",
                                        audioFile.getOriginalFilename(), audioFile.getSize(), projectId);

                        // 1. Parse slideTransitions JSON (optional)
                        List<SlideTransition> transitions = null;
                        if (slideTransitionsJson != null && !slideTransitionsJson.isBlank()) {
                                try {
                                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                        com.fasterxml.jackson.core.type.TypeReference<List<SlideTransition>> tr = new com.fasterxml.jackson.core.type.TypeReference<>() {
                                        };
                                        transitions = mapper.readValue(slideTransitionsJson, tr);
                                        log.info("▶ slideTransitions parsed - count={}", transitions.size());
                                } catch (Exception e) {
                                        log.warn("⚠ Failed to parse slideTransitions JSON, ignored - {}",
                                                        e.getMessage());
                                        transitions = null;
                                }
                        }

                        // 2. Retrieve slide scripts from DB (actual implementation)
                        // projectId == fileId assumed (adjust as needed)
                        List<CueCard> cueCards = cueCardRepository
                                        .findByPresentationFile_FileIdOrderBySlideNumberAscModeAscSectionNumberAsc(projectId);
                        List<String> slideScripts = new ArrayList<>();
                        for (CueCard cueCard : cueCards) {
                                slideScripts.add(cueCard.getContent());
                        }

                        // 3. Perform audio analysis (all analyses included)
                        AnalysisResult analysisResult = audioAnalysisService.analyzeAudio(audioFile, projectId,
                                        transitions, slideScripts);
                        List<WindowDto> windows = analysisResult.getWindows();

                        // 4. Save analysis result to DB (only if windows exist)
                        Long sessionId = saveAnalysisIfPresent(projectId, analysisResult);

                        // 5. Create response DTO (stat calculation logic delegated to static factory
                        // method)
                        AnalysisResponseDto response = AnalysisResponseDto.from(
                                        sessionId,
                                        projectId,
                                        analysisResult.getTotalDurationSeconds(),
                                        windows,
                                        analysisResult.getRepetitionResult());

                        log.info(" Analysis complete, {} windows returned (sessionId: {})", windows.size(), sessionId);
                        return ResponseEntity.ok(response);

                } catch (Exception e) {
                        log.error(" Analysis failed", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(AnalysisResponseDto.builder()
                                                        .sessionId(null)
                                                        .projectId(projectId)
                                                        .totalDurationSeconds(0)
                                                        .windows(new ArrayList<>())
                                                        .successCount(0)
                                                        .failCount(0)
                                                        .build());
                }
        }

        /**
         * Swagger 전용: STT 텍스트로 테스트용 반복 분석 실행
         * - 프론트에서 slideTransitions이 없을 때의 동작을 빠르게 확인하기 위함
         */
        @PostMapping(value = "/test-repetition", consumes = "application/x-www-form-urlencoded")
        @Operation(summary = "테스트용 반복 분석 (텍스트 입력)", description = "어휘 반복 분석 전용 api (STT 텍스트를 전달하면 테스트 전용 반복 분석 결과를 반환합니다.)")
        public ResponseEntity<?> testRepetition(@RequestParam("sttText") String sttText) {
                try {
                        log.info("[TEST] Repetition analysis (text) request: {} chars",
                                        sttText != null ? sttText.length() : 0);
                        com.pres.pres_server.service.analyse.RepetitiveTextAnalysisService.RepetitionAnalysisResult result = testRepetitiveService
                                        .analyzeRepetitionForTest(sttText);
                        return ResponseEntity.ok(result);
                } catch (Exception e) {
                        log.error("[TEST] Repetition analysis failed", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of("error", e.getMessage()));
                }
        }

        /**
         * 분석 결과를 DB에 저장 (윈도우가 있을 때만)
         * Controller의 책임을 명확히 하기 위해 분리
         */
        private Long saveAnalysisIfPresent(Long projectId, AudioAnalysisService.AnalysisResult analysisResult) {
                List<WindowDto> windows = analysisResult.getWindows();

                if (windows.isEmpty()) {
                        log.warn("  • No analyzed windows, not saving to DB");
                        return null;
                }

                Long sessionId = analysisResultService.saveAnalysisResult(projectId, analysisResult);

                long successCount = windows.stream()
                                .filter(w -> "SUCCESS".equals(w.getStatus()))
                                .count();
                long failCount = windows.stream()
                                .filter(w -> "FAILED".equals(w.getStatus()))
                                .count();

                log.info("  • DB save complete - sessionId: {}, success: {}, fail: {}",
                                sessionId, successCount, failCount);

                return sessionId;
        }

        /**
         * 실패한 윈도우만 재분석
         * 
         * @param sessionId 재분석할 세션 ID
         * @return 재분석 결과
         * 
         * 
         *         @PostMapping("/retry/{sessionId}")
         *         public ResponseEntity<?> retryFailedWindows(@PathVariable Long
         *         sessionId) {
         *         log.info("▶ Retry failed windows - sessionId: {}", sessionId);
         * 
         *         // TODO: Implementation needed
         *         // 1. Query SessionWindow for windows with status='FAILED'
         *         // 2. Cannot re-analyze because original audio file is missing
         *         // -> In practice, need to save original audio file or
         *         // have client re-upload it
         * 
         *         return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
         *         .body(Map.of(
         *         "message", "Retry analysis feature will be implemented in the
         *         future.",
         *         "reason",
         *         "Cannot retry because the original audio file is not saved."));
         *         }
         */
}

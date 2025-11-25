package com.pres.pres_server.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pres.pres_server.service.analyse.AudioAnalysisService;
import com.pres.pres_server.service.analyse.dto.SlideTransition;
import com.pres.pres_server.service.analyse.AudioAnalysisService.AnalysisResult;
import com.pres.pres_server.dto.analyse.AnalysisResponseDto;
// PracticeFeedbackDto and PracticeSessionService removed from this controller; feedback fetched via separate endpoint
import com.pres.pres_server.service.analyse.AnalysisResultService;
import com.pres.pres_server.repository.CueCardRepository;
import com.pres.pres_server.domain.CueCard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Analyse", description = "음성 분석 API")
@RestController
@RequestMapping("/analyse")
@CrossOrigin(origins = "http://localhost:5173", // 허용할 출처
        allowedHeaders = "*")
@Slf4j
@RequiredArgsConstructor
public class AnalyseController {

    private final AudioAnalysisService audioAnalysisService;
    private final AnalysisResultService analysisResultService;
    private final CueCardRepository cueCardRepository;
    private final ObjectMapper objectMapper;

    @Operation(summary = "오디오 분석", description = "오디오 파일 업로드 및 분석 수행" +
            "\n\n 예시 slideTransitions 값:\n" +
            "[\n" +
            "  { \"slideNumber\": 1, \"startSec\": 0.0,   \"endSec\": 8.42 },\n" +
            "  { \"slideNumber\": 2, \"startSec\": 8.42,  \"endSec\": 21.77 },\n" +
            "  { \"slideNumber\": 3, \"startSec\": 21.77, \"endSec\": 35.10 }\n" +
            "]")
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Long>> analyse(
            @RequestPart("audio") MultipartFile audioFile,
            @RequestParam("projectId") Long projectId,
            @RequestParam("slideTransitions") String slideTransitionsJson) {

        try {
            log.info("▶ Analysis request: file='{}', size={}, projectId={}",
                    audioFile.getOriginalFilename(), audioFile.getSize(), projectId);

            // 1. Parse slideTransitions
            List<SlideTransition> transitions = parseSlideTransitions(slideTransitionsJson);

            // 2. Fetch slideScripts from DB (요청으로 전달된 slideTransitions 길이에 맞춰 반환)
            List<String> slideScripts = null;
            if (projectId != null) {
                if (transitions != null && !transitions.isEmpty()) {
                    slideScripts = fetchSlideScripts(projectId, transitions.size());
                } else {
                    slideScripts = fetchSlideScripts(projectId);
                }
            }

            // 3. Log transition/script sizes for mismatch detection
            int transitionCount = transitions == null ? 0 : transitions.size();
            int scriptCount = slideScripts == null ? 0 : slideScripts.size();
            log.info("  • slideTransitions.size()={}, slideScripts.size()={}", transitionCount, scriptCount);
            if (transitionCount > 0 && scriptCount > 0 && transitionCount != scriptCount) {
                log.warn(
                        "  ⚠ slideTransitions count ({}) does not match slideScripts count ({}). Analysis will use controller-provided scripts as-is.",
                        transitionCount, scriptCount);
            }

            // 4. Perform analysis
            AnalysisResult analysisResult = audioAnalysisService.analyzeAudio(
                    audioFile, projectId, transitions, slideScripts);

            // 4. Save to DB
            Long sessionId = analysisResultService.saveAnalysisResult(projectId, analysisResult);

            // 5. Return minimal success response (frontend will call feedback endpoint)
            log.info("Analysis saved: sessionId={}, windows={}", sessionId, analysisResult.getWindows().size());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("sessionId", sessionId));

        } catch (Exception e) {
            log.error("Analysis failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Operation(summary = "오디오 분석 (테스트용)", description = "오디오 파일을 업로드하면 분석 결과를 반환합니다.")
    @PostMapping(value = "/test-audio", consumes = "multipart/form-data")
    public ResponseEntity<AnalysisResponseDto> testAudioAnalysis(
            @RequestPart("audioFile") MultipartFile audioFile,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "slideTransitions", required = false) String slideTransitionsJson) {
        try {
            String originalName = audioFile.getOriginalFilename();
            log.info("[TEST] Audio analysis test request: {} (size={} bytes)", originalName,
                    audioFile.getSize());
            // optional slideTransitions: parse and pass slide scripts when projectId
            // provided
            List<SlideTransition> transitions = parseSlideTransitions(slideTransitionsJson);
            List<String> slideScripts = null;
            if (projectId != null) {
                if (transitions != null && !transitions.isEmpty()) {
                    slideScripts = fetchSlideScripts(projectId, transitions.size());
                } else {
                    slideScripts = fetchSlideScripts(projectId);
                }
            }
            int transitionCount = transitions == null ? 0 : transitions.size();
            int scriptCount = slideScripts == null ? 0 : slideScripts.size();
            log.info("  • [TEST] slideTransitions.size()={}, slideScripts.size()={}", transitionCount, scriptCount);
            if (transitionCount > 0 && scriptCount > 0 && transitionCount != scriptCount) {
                log.warn("  ⚠ [TEST] slideTransitions count ({}) does not match slideScripts count ({}).",
                        transitionCount, scriptCount);
            }

            AnalysisResult result = audioAnalysisService.analyzeAudio(audioFile, projectId,
                    transitions == null || transitions.isEmpty() ? null : transitions,
                    slideScripts);
            log.info("[TEST] Audio analysis test complete: {} (duration={}s, windows={})", originalName,
                    result.getTotalDurationSeconds(), result.getWindows().size());

            AnalysisResponseDto dto = AnalysisResponseDto.from(null, projectId, result,
                    java.util.Collections.emptyList());
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("[TEST] Audio analysis test failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AnalysisResponseDto.error(projectId, e.getMessage()));
        }
    }

    @Operation(summary = "오디오 분석 (테스트용, PracticeFeedbackDto 반환)", description = "오디오 파일을 업로드하면 PracticeFeedbackDto 형태로 분석 결과를 반환합니다.")
    @PostMapping(value = "/test-audio-practice", consumes = "multipart/form-data")
    public ResponseEntity<com.pres.pres_server.dto.practice.PracticeFeedbackDto> testAudioPractice(
            @RequestPart("audioFile") MultipartFile audioFile,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "slideTransitions", required = false) String slideTransitionsJson) {
        try {
            String originalName = audioFile.getOriginalFilename();
            log.info("[TEST-PR] Audio analysis test request: {} (size={} bytes)", originalName,
                    audioFile.getSize());
            // optional slideTransitions: parse and pass slide scripts when projectId
            // provided
            List<SlideTransition> transitions = parseSlideTransitions(slideTransitionsJson);
            // 테스트용 임시 로직: slideTransitions가 비어있으면 기본 윈도우(0-30, 30-end)로 생성
            if (transitions == null || transitions.isEmpty()) {
                transitions = java.util.List.of(
                        SlideTransition.builder().slideNumber(1).startSec(0.0).endSec(30.0).build(),
                        SlideTransition.builder().slideNumber(2).startSec(30.0).endSec(999999.0).build());
            }
            List<String> slideScripts = null;
            if (projectId != null) {
                // 여기서는 transitions를 이미 기본값으로 채웠으므로 size 사용
                slideScripts = fetchSlideScripts(projectId, transitions.size());
            }
            int transitionCount = transitions == null ? 0 : transitions.size();
            int scriptCount = slideScripts == null ? 0 : slideScripts.size();
            log.info("  • [TEST-PR] slideTransitions.size()={}, slideScripts.size()={}", transitionCount, scriptCount);
            if (transitionCount > 0 && scriptCount > 0 && transitionCount != scriptCount) {
                log.warn("  ⚠ [TEST-PR] slideTransitions count ({}) does not match slideScripts count ({}).",
                        transitionCount, scriptCount);
            }
            // 분석 호출 (DB 저장 없음)
            AnalysisResult result = audioAnalysisService.analyzeAudio(audioFile, projectId,
                    transitions == null || transitions.isEmpty() ? null : transitions,
                    slideScripts);
            log.info("[TEST-PR] Audio analysis test complete: {} (duration={}s, windows={})", originalName,
                    result.getTotalDurationSeconds(), result.getWindows().size());

            // Build PracticeFeedbackDto from analysis result (in-memory, not persisted)
            // Compute basic scores similar to AnalysisResultService
            List<com.pres.pres_server.dto.analyse.WindowDto> windows = result.getWindows();
            double avgSpm = windows.stream().filter(w -> "SUCCESS".equals(w.getStatus())).mapToInt(
                    com.pres.pres_server.dto.analyse.WindowDto::getSpmScore).average().orElse(0.0);
            int spmScore = (int) Math.round(avgSpm);

            int totalFillers = windows.stream()
                    .mapToInt(w -> w.getFillers().values().stream().mapToInt(Integer::intValue).sum()).sum();
            int fillerScore = Math.max(0, 100 - (totalFillers * 2));

            int repeatScore = 100;
            if (result.getRepetitionResult() != null && result.getRepetitionResult().isSuccess()) {
                repeatScore = result.getRepetitionResult().getRepetitionScore();
            }

            int silenceScore = 100;
            if (result.getSilenceStats() != null && result.getSilenceStats().isSuccess()) {
                silenceScore = Math.max(0, 100 - (result.getSilenceStats().getSilenceCount() * 10));
            }

            int accuracyScore = 100;
            if (result.getAccuracyResult() != null && result.getAccuracyResult().isSuccess()) {
                accuracyScore = result.getAccuracyResult().getAccuracyScore();
            }

            int totalScore = (int) Math.round(
                    avgSpm * 0.2 + fillerScore * 0.15 + repeatScore * 0.15 + silenceScore * 0.15
                            + accuracyScore * 0.35);

            String grade = analysisResultService.computeGrade(totalScore);

            // Build slide feedbacks (derive issues/offsets from analysisResult) using
            // service helper
            List<com.pres.pres_server.dto.practice.SlideFeedbackDto> slideFeedbacks = analysisResultService
                    .buildSlideFeedbackDtosFromAnalysis(result);

            com.pres.pres_server.dto.practice.PracticeFeedbackDto dto = com.pres.pres_server.dto.practice.PracticeFeedbackDto
                    .builder()
                    .sessionId(null)
                    .feedbackId(null)
                    .spmScore(spmScore)
                    .fillerScore(fillerScore)
                    .repeatScore(repeatScore)
                    .silenceScore(silenceScore)
                    .accuracyScore(accuracyScore)
                    .totalScore(totalScore)
                    .grade(grade)
                    .totalDurationSeconds(result.getTotalDurationSeconds())
                    .history(java.util.Collections.emptyList())
                    .slideFeedbacks(slideFeedbacks)
                    .aiFeedback(java.util.Collections.emptyMap())
                    .build();

            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("[TEST-PR] Audio analysis test failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * slideTransitions JSON 파싱
     */
    private List<SlideTransition> parseSlideTransitions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            TypeReference<List<SlideTransition>> typeRef = new TypeReference<>() {
            };
            List<SlideTransition> transitions = objectMapper.readValue(json, typeRef);
            log.info("  • Parsed {} slide transitions", transitions.size());
            return transitions;
        } catch (Exception e) {
            log.warn("  ⚠ Failed to parse slideTransitions: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * DB에서 슬라이드별 대본 조회
     */
    private List<String> fetchSlideScripts(Long fileId, int expectedSlideCount) {
        try {
            List<CueCard> cueCards = cueCardRepository
                    .findByPresentationFile_FileIdOrderBySlideNumberAscModeAscSectionNumberAsc(fileId);

            if (cueCards == null || cueCards.isEmpty()) {
                log.info("  • No cue cards found for projectId={} (expected slides={})", fileId, expectedSlideCount);
                return java.util.Collections.nCopies(expectedSlideCount, "");
            }

            List<String> scripts = new java.util.ArrayList<>(java.util.Collections.nCopies(expectedSlideCount, ""));
            for (CueCard cc : cueCards) {
                int idx = cc.getSlideNumber();
                if (idx >= 1 && idx <= expectedSlideCount) {
                    String prev = scripts.get(idx - 1);
                    String content = cc.getContent() == null ? "" : cc.getContent().trim();
                    if (prev == null || prev.isBlank())
                        scripts.set(idx - 1, content);
                    else if (!content.isBlank())
                        scripts.set(idx - 1, prev + " " + content);
                }
            }

            log.info("  • Fetched {} cue cards and normalized into {} slide scripts", cueCards.size(),
                    expectedSlideCount);
            return scripts;

        } catch (Exception e) {
            log.warn("  ⚠ Failed to fetch slide scripts (expected={}): {}", expectedSlideCount, e.getMessage());
            return java.util.Collections.nCopies(expectedSlideCount, "");
        }
    }

    private List<String> fetchSlideScripts(Long fileId) {
        try {
            List<CueCard> cueCards = cueCardRepository
                    .findByPresentationFile_FileIdOrderBySlideNumberAscModeAscSectionNumberAsc(fileId);

            if (cueCards.isEmpty()) {
                log.info("  • No cue cards found for projectId={}", fileId);
                return List.of();
            }

            List<String> scripts = cueCards.stream()
                    .map(CueCard::getContent)
                    .filter(content -> content != null && !content.trim().isEmpty())
                    .collect(Collectors.toList());

            log.info("  • Fetched {} slide scripts", scripts.size());
            return scripts;

        } catch (Exception e) {
            log.warn("  ⚠ Failed to fetch slide scripts: {}", e.getMessage());
            return List.of();
        }
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

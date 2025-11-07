package com.pres.pres_server.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pres.pres_server.service.analyse.AudioAnalysisService;
import com.pres.pres_server.service.analyse.dto.SlideTransition;
import com.pres.pres_server.service.analyse.AudioAnalysisService.AnalysisResult;
import com.pres.pres_server.dto.analyse.AnalysisResponseDto;
import com.pres.pres_server.service.analyse.AnalysisResultService;
import com.pres.pres_server.service.analyse.TestRepetitiveService;
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
    public ResponseEntity<AnalysisResponseDto> analyse(
            @RequestPart("audio") MultipartFile audioFile,
            @RequestParam("projectId") Long projectId,
            @RequestParam("slideTransitions") String slideTransitionsJson) {

        try {
            log.info("▶ Analysis request: file='{}', size={}, projectId={}",
                    audioFile.getOriginalFilename(), audioFile.getSize(), projectId);

            // 1. Parse slideTransitions
            List<SlideTransition> transitions = parseSlideTransitions(slideTransitionsJson);

            // 2. Fetch slideScripts from DB
            List<String> slideScripts = fetchSlideScripts(projectId);

            // 3. Perform analysis
            AnalysisResult analysisResult = audioAnalysisService.analyzeAudio(
                    audioFile, projectId, transitions, slideScripts);

            // 4. Save to DB
            Long sessionId = analysisResultService.saveAnalysisResult(
                    projectId, analysisResult);

            // 5. Build response
            AnalysisResponseDto response = AnalysisResponseDto.from(
                    sessionId,
                    projectId,
                    analysisResult
            );

            log.info("Analysis complete: sessionId={}, windows={}",
                    sessionId, analysisResult.getWindows().size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Analysis failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AnalysisResponseDto.builder()
                            .projectId(projectId)
                            .build());
        }
    }

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

package com.pres.pres_server.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pres.pres_server.service.analyse.AudioAnalysisService;
import com.pres.pres_server.service.analyse.dto.SlideTransition;
import com.pres.pres_server.service.analyse.AudioAnalysisService.AnalysisResult;
import com.pres.pres_server.service.analyse.AnalysisResultService;
import com.pres.pres_server.repository.CueCardRepository;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.domain.PresentationFile;
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
import java.util.Optional;
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
    private final PresentationFileRepository presentationFileRepository;
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
            // 1. Parse slideTransitions
            List<SlideTransition> transitions = parseSlideTransitions(slideTransitionsJson);

            for (SlideTransition t : transitions) {
                log.info("TRANSITION CHECK slideNumber={}, start={}, end={}",
                        t.getSlideNumber(), t.getStartSec(), t.getEndSec());
            }

            // 2. Fetch slideScripts from DB (요청으로 전달된 slideTransitions 길이에 맞춰 반환)
            // NOTE: fetchSlideScripts expects a presentation fileId, not projectId.
            List<String> slideScripts = null;
            if (projectId != null) {
                Long fileId = null;
                try {
                    Optional<PresentationFile> pfOpt = presentationFileRepository.findByProject_ProjectId(projectId);
                    if (pfOpt.isPresent()) {
                        fileId = pfOpt.get().getFileId();
                    } else {
                        log.info("  • No presentation file found for projectId={}", projectId);
                    }
                } catch (Exception e) {
                    log.warn("  • Failed to lookup presentation file for projectId={}: {}", projectId, e.getMessage());
                }

                if (fileId != null) {
                    if (transitions != null && !transitions.isEmpty()) {
                        slideScripts = fetchSlideScripts(fileId, transitions.size());
                    } else {
                        slideScripts = fetchSlideScripts(fileId);
                    }
                } else {
                    // let AudioAnalysisService perform fallback (it can resolve fileId correctly)
                    slideScripts = null;
                }
            }

            // 3. Log transition/script sizes for mismatch detection
            int transitionCount = transitions == null ? 0 : transitions.size();
            int scriptCount = slideScripts == null ? 0 : slideScripts.size();
            //log.info("  • slideTransitions.size()={}, slideScripts.size()={}", transitionCount,scriptCount);
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

package com.pres.pres_server.controller;

import com.pres.pres_server.service.analyse.AudioAnalysisService;
import com.pres.pres_server.service.analyse.AudioAnalysisService.AnalysisResult;
import com.pres.pres_server.dto.analyse.AnalysisResponseDto;
import com.pres.pres_server.dto.analyse.WindowDto;
import com.pres.pres_server.service.analyse.AnalysisResultService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/analyse")
@CrossOrigin(origins = "http://localhost:5173", // 허용할 출처
        allowedHeaders = "*")
public class AnalyseController {
    private static final Logger log = LoggerFactory.getLogger(AnalyseController.class);

    private final AudioAnalysisService audioAnalysisService;
    private final AnalysisResultService analysisResultService;

    public AnalyseController(
            AudioAnalysisService audioAnalysisService,
            AnalysisResultService analysisResultService) {
        this.audioAnalysisService = audioAnalysisService;
        this.analysisResultService = analysisResultService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<AnalysisResponseDto> analyse(
            @RequestPart("audio") MultipartFile audioFile,
            @RequestParam("projectId") Long projectId) {

        try {
            log.info("▶ 분석 요청 수신: file='{}', size={} bytes, projectId={}",
                    audioFile.getOriginalFilename(), audioFile.getSize(), projectId);

            // 1. 오디오 분석 수행
            AnalysisResult analysisResult = audioAnalysisService.analyzeAudio(audioFile);
            List<WindowDto> windows = analysisResult.getWindows();

            // 2. DB에 분석 결과 저장 (윈도우가 있을 때만)
            Long sessionId = saveAnalysisIfPresent(projectId, windows, analysisResult.getTotalDurationSeconds());

            // 3. 응답 DTO 생성 (통계 계산 로직은 DTO의 정적 팩토리 메서드에 위임)
            AnalysisResponseDto response = AnalysisResponseDto.from(
                    sessionId,
                    projectId,
                    analysisResult.getTotalDurationSeconds(),
                    windows);

            log.info("✅ 분석 완료, {} 개 윈도우 반환 (sessionId: {})", windows.size(), sessionId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ 분석 실패", e);
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
     * 분석 결과를 DB에 저장 (윈도우가 있을 때만)
     * Controller의 책임을 명확히 하기 위해 분리
     */
    private Long saveAnalysisIfPresent(Long projectId, List<WindowDto> windows, double totalDuration) {
        if (windows.isEmpty()) {
            log.warn("  • 분석된 윈도우가 없어 저장하지 않음");
            return null;
        }

        Long sessionId = analysisResultService.saveAnalysisResult(projectId, windows, totalDuration);

        long successCount = windows.stream()
                .filter(w -> "SUCCESS".equals(w.getStatus()))
                .count();
        long failCount = windows.stream()
                .filter(w -> "FAILED".equals(w.getStatus()))
                .count();

        log.info("  • DB 저장 완료 - sessionId: {}, 성공: {}, 실패: {}",
                sessionId, successCount, failCount);

        return sessionId;
    }

    /**
     * 실패한 윈도우만 재분석
     * 
     * @param sessionId 재분석할 세션 ID
     * @return 재분석 결과
     */
    @PostMapping("/retry/{sessionId}")
    public ResponseEntity<?> retryFailedWindows(@PathVariable Long sessionId) {
        log.info("▶ 실패한 윈도우 재분석 시작 - sessionId: {}", sessionId);

        // TODO: 구현 필요
        // 1. SessionWindow에서 status='FAILED'인 윈도우 조회
        // 2. 원본 오디오 파일이 없으므로 재분석 불가
        // -> 실제로는 원본 오디오 파일을 저장하거나,
        // 클라이언트에서 다시 업로드하도록 해야 함

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of(
                        "message", "재분석 기능은 추후 구현 예정입니다.",
                        "reason", "원본 오디오 파일이 저장되지 않아 재분석이 불가능합니다."));
    }
}

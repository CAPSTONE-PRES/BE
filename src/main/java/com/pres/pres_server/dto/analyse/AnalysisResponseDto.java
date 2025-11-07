package com.pres.pres_server.dto.analyse;

import com.pres.pres_server.service.analyse.AudioAnalysisService;
import com.pres.pres_server.service.analyse.RepetitiveTextAnalysisService.RepetitionAnalysisResult;
import com.pres.pres_server.service.analyse.ScriptAccuracyService;
import com.pres.pres_server.service.analyse.SilenceDetectionService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 오디오 분석 결과 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponseDto {

        /**
         * DB에 저장된 세션 ID
         */
        private Long sessionId;

        /**
         * 프로젝트 ID
         */
        private Long projectId;

        /**
         * 전체 오디오 길이 (초)
         */
        private double totalDurationSeconds;

        /**
         * 윈도우별 분석 결과
         */
        private List<WindowDto> windows;

        /**
         * 반복 분석 결과
         */
        private RepetitionAnalysisResult repetitionResult;

        /**
         * 성공한 윈도우 개수
         */
        private long successCount;

        /**
         * 실패한 윈도우 개수
         */
        private long failCount;

        private SilenceDetectionService.SilenceStatistics silenceStats;
        private ScriptAccuracyService.AccuracyAnalysisResult accuracyResult;
        private AudioAnalysisService.SlideAnalysisResult slideAnalysis;


        public static AnalysisResponseDto from(
                Long sessionId,
                Long projectId,
                AudioAnalysisService.AnalysisResult result
        ) {
                List<WindowDto> windows = result.getWindows();

                long successCount = windows.stream()
                        .filter(w -> "SUCCESS".equals(w.getStatus()))
                        .count();

                long failCount = windows.stream()
                        .filter(w -> "FAILED".equals(w.getStatus()))
                        .count();

                return AnalysisResponseDto.builder()
                        .sessionId(sessionId)
                        .projectId(projectId)
                        .totalDurationSeconds(result.getTotalDurationSeconds())
                        .windows(result.getWindows()) // 이미 WindowDto 형태라면 그대로, 아니면 매핑 필요
                        .repetitionResult(result.getRepetitionResult())
                        .silenceStats(result.getSilenceStats())
                        .accuracyResult(result.getAccuracyResult())
                        .slideAnalysis(result.getSlideAnalysis())
                        .successCount(successCount)
                        .failCount(failCount)
                        .build();
        }

        /**
         * 에러 응답 생성
         */
        public static AnalysisResponseDto error(Long projectId, String errorMessage) {
                return AnalysisResponseDto.builder()
                        .projectId(projectId)
                        .totalDurationSeconds(0)
                        .windows(List.of())
                        .repetitionResult(null)
                        .silenceStats(null)
                        .accuracyResult(null)
                        .slideAnalysis(null)
                        .successCount(0)
                        .failCount(0)
                        .build();
        }
}
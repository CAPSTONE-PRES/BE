package com.pres.pres_server.dto.analyse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import com.pres.pres_server.service.analyse.RepetitiveTextAnalysisService.RepetitionAnalysisResult;
import com.pres.pres_server.service.analyse.AudioAnalysisService;
import com.pres.pres_server.dto.practice.IssueDto;
import java.util.Collections;

//개발 테스트용 원시 dto
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponseDto {

        /**
         * DB에 저장된 세션 ID (재분석 API 호출 시 필요)
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
         * 윈도우별 분석 결과 목록
         */
        private List<WindowDto> windows;

        /**
         * 반복 분석 결과 (Level1/L2/L3). 도메인 객체 형태로 포함하여 Swagger에서 확인 가능하도록 함.
         */
        private RepetitionAnalysisResult repetitionResult;

        /**
         * 슬라이드별 이슈 리스트 (각 슬라이드에 대한 IssueDto 목록)
         */
        private List<List<IssueDto>> slideIssues;

        /**
         * 성공한 윈도우 개수
         */
        private long successCount;

        /**
         * 실패한 윈도우 개수
         */
        private long failCount;

        /**
         * AnalysisResult로부터 응답 DTO 생성 (정적 팩토리 메서드)
         * Controller의 복잡도를 낮추고 통계 계산 로직을 캡슐화
         */
        public static AnalysisResponseDto from(Long sessionId, Long projectId,
                        double totalDurationSeconds,
                        List<WindowDto> windows,
                        RepetitionAnalysisResult repetitionResult) {
                long successCount = windows.stream()
                                .filter(w -> "SUCCESS".equals(w.getStatus()))
                                .count();

                long failCount = windows.stream()
                                .filter(w -> "FAILED".equals(w.getStatus()))
                                .count();

                return AnalysisResponseDto.builder()
                                .sessionId(sessionId)
                                .projectId(projectId)
                                .totalDurationSeconds(totalDurationSeconds)
                                .windows(windows)
                                .successCount(successCount)
                                .failCount(failCount)
                                .repetitionResult(repetitionResult)
                                .build();
        }

        /**
         * AnalysisResult 객체로부터 응답 DTO 생성 (슬라이드 이슈 포함 오버로드)
         */
        public static AnalysisResponseDto from(Long sessionId, Long projectId,
                        AudioAnalysisService.AnalysisResult result,
                        List<List<IssueDto>> slideIssues) {
                if (result == null) {
                        return AnalysisResponseDto.builder()
                                        .sessionId(sessionId)
                                        .projectId(projectId)
                                        .build();
                }

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
                                .windows(windows)
                                .successCount(successCount)
                                .failCount(failCount)
                                .repetitionResult(result.getRepetitionResult())
                                .slideIssues(slideIssues)
                                .build();
        }

        public static AnalysisResponseDto error(Long projectId, String message) {
                return AnalysisResponseDto.builder()
                                .projectId(projectId)
                                .windows(Collections.emptyList())
                                .build();
        }
}

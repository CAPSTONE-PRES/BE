package com.pres.pres_server.dto.analyse;

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
            List<WindowDto> windows) {
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
                .build();
    }
}

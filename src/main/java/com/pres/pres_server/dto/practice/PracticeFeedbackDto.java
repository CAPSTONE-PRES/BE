package com.pres.pres_server.dto.practice;

import com.pres.pres_server.dto.qna.QnaComparisonDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeFeedbackDto {

    private Long sessionId;
    private Long feedbackId;
    private Integer spmScore;
    private Integer fillerScore;
    private Integer repeatScore;
    private Integer totalScore;
    private String grade;

    // 공백 감지 관련 필드
    private Integer silenceCount; // 2.5초 이상 공백 횟수
    private Double totalSilenceDuration; // 총 공백 시간 (초)
    private Integer silenceScore; // 공백 점수 (0~100)
    // silenceAnalysisSuccess는 사용자에게 노출하지 않음 (내부 로그/모니터링용)

    // QnA 비교 결과 (선택적)
    private QnaComparisonDto qnaComparison; // QnA 진행 시에만 포함, 미진행 시 null
}

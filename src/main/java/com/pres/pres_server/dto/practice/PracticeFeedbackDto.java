package com.pres.pres_server.dto.practice;

import com.pres.pres_server.dto.qna.QnaComparisonDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

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
    private Integer accuracyScore; // 정확도 점수 추가
    private Integer totalScore;
    private String grade;

    // 슬라이드별 피드백 (이슈가 있는 슬라이드만)
    private List<SlideFeedbackDto> slideFeedbacks;

    // 전체 STT 텍스트
    private String fullSttText;

    // QnA 비교 결과 (선택적)
    private QnaComparisonDto qnaComparison; // QnA 진행 시에만 포함, 미진행 시 null
}

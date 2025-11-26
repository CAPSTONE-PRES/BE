package com.pres.pres_server.dto.practice;

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
    private Integer silenceScore;
    private Integer accuracyScore;
    private Integer totalScore;
    private String grade;
    private Double totalDurationSeconds;
    private List<PracticeHistoryDto> history;

    // 슬라이드별 피드백 (이슈가 있는 슬라이드만)
    private List<SlideFeedbackDto> slideFeedbacks;

    // aiFeedback: AI 코멘트
    private String overallFeedback;
}

package com.pres.pres_server.dto.practice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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

    // AI 기반 문장별/전반적 피드백 (keys: filler,silence,repetition,accuracy,pace)
    private Map<String, String> aiFeedback;
}

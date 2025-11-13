package com.pres.pres_server.dto.practice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 슬라이드별 피드백 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlideFeedbackDto {

    private Integer slideNumber; // 슬라이드 번호
    private Double timestampSeconds; // 슬라이드 시작 시각 (초)
    private String slideText; // 해당 슬라이드 STT 텍스트
    private String issueType; // 이슈 유형 (SPEED / FILLER / REPETITION / SILENCE / ACCURACY)

    // 속도 관련
    private Integer spmUser; // 사용자 SPM
    private Integer spmAverage; // 평균 SPM (290)

    // 필러 관련
    private Integer fillerCount; // 필러 개수
    private Map<String, Integer> fillerDetail; // 필러 상세 {"음":1,"뭐지":1}

    // 공백 관련
    private Integer silenceCount; // 2.5초 이상 공백 횟수
    private Double totalSilenceDuration; // 총 공백 시간 (초)
    private Integer silenceScore; // 공백 점수 (0~100)

    // 반복 어휘 관련
    private Integer repeatCount; // 반복 횟수
    private String repeatDetail; // 반복 상세 "그 다음에, 그러니까, 약간"

    // 정확도 관련
    private Integer errorCount; // 오류/불일치 개수

    // 코멘트
    private String comment; // 해당 슬라이드 코멘트
}

package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "slide_feedback")
@Getter
@Setter
public class SlideFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_id", nullable = false)
    private Feedback feedback;

    @Column(name = "slide_number", nullable = false)
    private Integer slideNumber;

    // 슬라이드 시작 시각 (초 단위) - 01:26:30 같은 값
    @Column(name = "timestamp_seconds")
    private Double timestampSeconds;

    // STT 텍스트
    @Column(name = "slide_text", columnDefinition = "TEXT")
    private String slideText;

    // 어떤 유형의 이슈인지 (SPEED / FILLER / REPETITION / ACCURACY 등)
    @Column(name = "issue_type", length = 50)
    private String issueType;

    // -------------------
    // 속도 관련 (issue_type = SPEED 일 때 사용)
    // -------------------
    @Column(name = "spm_user")
    private Integer spmUser;       // 사용자 SPM

    @Column(name = "spm_average")
    private Integer spmAverage;    // 비교 평균 SPM (290 등)

    // -------------------
    // 망설임 / 필러 (issue_type = FILLER)
    // -------------------
    @Column(name = "filler_count")
    private Integer fillerCount;   // 총 필러 개수

    @Column(name = "filler_detail", columnDefinition = "TEXT")
    private String fillerDetail;   // 예: {"음":1,"뭐지":1}

    // ===========공백============
    @Column(name = "silence_count")
    private Integer silenceCount; // 2.5초 이상 공백 횟수

    @Column(name = "total_silence_duration")
    private Double totalSilenceDuration; // 총 공백 시간 (초)

    @Column(name = "silence_score")
    private Integer silenceScore; // 공백 점수 (0~100)

    @Column(name = "silence_analysis_success")
    private Boolean silenceAnalysisSuccess; // 공백 분석 성공 여부 (내부 모니터링용, API 노출 안 함)

    // -------------------
    // 반복 어휘 (issue_type = REPETITION)
    // -------------------
    @Column(name = "repeat_count")
    private Integer repeatCount;   // 총 반복 횟수

    @Column(name = "repeat_detail", columnDefinition = "TEXT")
    private String repeatDetail;   // 예: "그 다음에, 그러니까, 약간"

    // -------------------
    // 정확도 (issue_type = ACCURACY)
    // -------------------
    @Column(name = "error_count")
    private Integer errorCount;    // 오류/불일치 부분 개수

    // -------------------
    // 공통 코멘트
    // -------------------
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    // 여러 이슈를 JSON 배열로 저장 (API 응답에서 issues 배열로 제공)
    @Column(name = "issues", columnDefinition = "TEXT")
    private String issues;
}

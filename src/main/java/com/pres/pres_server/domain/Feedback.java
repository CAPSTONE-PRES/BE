package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "feedback")
@Getter
@Setter
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Long feedbackId;

    @Column(name = "spm_score")
    private int spmScore;

    @Column(name = "filler_score")
    private int fillerScore;

    @Column(name = "repeat_score")
    private int repeatScore;

    @Column(name = "total_score")
    private int totalScore;

    @Column(name = "grade")
    private String grade;

    // 공백 감지 관련 필드
    @Column(name = "silence_count")
    private Integer silenceCount; // 2.5초 이상 공백 횟수

    @Column(name = "total_silence_duration")
    private Double totalSilenceDuration; // 총 공백 시간 (초)

    @Column(name = "silence_score")
    private Integer silenceScore; // 공백 점수 (0~100)

    @Column(name = "silence_analysis_success")
    private Boolean silenceAnalysisSuccess; // 공백 분석 성공 여부 (내부 모니터링용, API 노출 안 함)

    // 발표 정확도 관련 필드
    @Column(name = "accuracy_score")
    private Integer accuracyScore; // 대본 대비 발표 정확도 점수 (0~100)

    @Column(name = "script_similarity")
    private Double scriptSimilarity; // 대본과의 유사도 (0.0~1.0)

    @Column(name = "missing_keywords")
    private String missingKeywords; // 누락된 주요 키워드 (JSON 배열 형식)

    @OneToOne
    @JoinColumn(name = "session_id")
    private PracticeSession practiceSession;
}

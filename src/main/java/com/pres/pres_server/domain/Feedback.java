package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "feedback")
@Getter
@Setter
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Long feedbackId;

    @OneToOne
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private PracticeSession practiceSession;

    @OneToMany(mappedBy = "feedback", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SlideFeedback> slideFeedbacks = new ArrayList<>();

    // =======점수========
    // 말의 속도
    @Column(name = "spm_score")
    private int spmScore;

    // 말의 망설임 항목, filler+silence, 임의로 filler라고 명시
    @Column(name = "filler_score")
    private int fillerScore;

    // 전체 공백(silence) 관련 점수
    @Column(name = "silence_score")
    private Integer silenceScore;

    // 말의 반복
    @Column(name = "repeat_score")
    private int repeatScore;

    // 발표 정확도
    @Column(name = "accuracy_score")
    private Integer accuracyScore; // 대본 대비 발표 정확도 점수 (0~100)

    @Column(name = "total_score")
    private int totalScore;

    @Column(name = "grade")
    private String grade;

    // ==========정확도=============
    @Column(name = "script_similarity")
    private Double scriptSimilarity; // 대본과의 유사도 (0.0~1.0)

    @Column(name = "missing_keywords", columnDefinition = "TEXT")
    private String missingKeywords; // 누락된 주요 키워드 (JSON 배열 형식)

    // === 코멘트 ===
    @Column(name = "overall_comment", columnDefinition = "TEXT")
    private String overallComment;

    // === 생성 시각 ===
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 피드백이 제공된 슬라이드 번호 (슬라이드 별 피드백 제공)

}

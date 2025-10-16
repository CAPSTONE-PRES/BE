package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "qna_answer_comparisons", indexes = {
        @Index(name = "idx_qna_id_practice_session_id", columnList = "qna_id, practice_session_id")
})
@Getter
@Setter
public class QnaAnswerComparison {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comparison_id")
    private Long comparisonId;

    @ManyToOne
    @JoinColumn(name = "qna_id", nullable = false)
    private QnaQuestion qnaQuestion;

    @ManyToOne
    @JoinColumn(name = "ideal_answer_id", nullable = false)
    private QnaAnswer idealAnswer;

    @ManyToOne
    @JoinColumn(name = "user_answer_id", nullable = false)
    private QnaAnswer userAnswer;

    @ManyToOne
    @JoinColumn(name = "practice_session_id", nullable = false)
    private PracticeSession practiceSession;

    @Column(name = "sim_cosine")
    private Float simCosine;

    @Column(name = "bert_score_f1")
    private Float bertScoreF1;

    @Column(name = "rouge_l")
    private Float rougeL;

    @Column(name = "keyword_recall")
    private Float keywordRecall;

    @Column(name = "coverage")
    private Float coverage;

    @Column(name = "hallucination_risk")
    private Float hallucinationRisk;

    @Column(name = "grammar_score")
    private Float grammarScore;

    @Column(name = "structure_score")
    private Float structureScore;

    @Column(name = "timestamp", columnDefinition = "json")
    private String timestamp;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

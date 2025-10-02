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
    private Long comparison_id;

    @ManyToOne
    @JoinColumn(name = "qna_id", nullable = false)
    private QnaQuestion qna_question;

    @ManyToOne
    @JoinColumn(name = "ideal_answer_id", nullable = false)
    private QnaAnswer ideal_answer;

    @ManyToOne
    @JoinColumn(name = "user_answer_id", nullable = false)
    private QnaAnswer user_answer;

    @ManyToOne
    @JoinColumn(name = "practice_session_id", nullable = false)
    private PracticeSession practice_session;

    @Column(name = "sim_cosine")
    private Float sim_cosine;

    @Column(name = "bert_score_f1")
    private Float bert_score_f1;

    @Column(name = "rouge_l")
    private Float rouge_l;

    @Column(name = "keyword_recall")
    private Float keyword_recall;

    @Column(name = "coverage")
    private Float coverage;

    @Column(name = "hallucination_risk")
    private Float hallucination_risk;

    @Column(name = "grammar_score")
    private Float grammar_score;

    @Column(name = "structure_score")
    private Float structure_score;

    @Column(name = "timestamp", columnDefinition = "json")
    private String timestamp;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime created_at;
}

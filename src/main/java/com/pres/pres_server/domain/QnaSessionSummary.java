package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "qna_session_summaries")
@Getter
@Setter
public class QnaSessionSummary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "summary_id")
    private Long summary_id;

    @OneToOne
    @JoinColumn(name = "practice_session_id", nullable = false, unique = true)
    private PracticeSession practice_session;

    @Column(name = "q_count")
    private Integer q_count;

    @Column(name = "answered_count")
    private Integer answered_count;

    @Column(name = "avg_sim_cosine")
    private Float avg_sim_cosine;

    @Column(name = "avg_keyword_recall")
    private Float avg_keyword_recall;

    @ManyToOne
    @JoinColumn(name = "weakest_qna_id")
    private QnaQuestion weakest_qna;

    @ManyToOne
    @JoinColumn(name = "strongest_qna_id")
    private QnaQuestion strongest_qna;

    @Column(name = "created_at")
    private LocalDateTime created_at;
}

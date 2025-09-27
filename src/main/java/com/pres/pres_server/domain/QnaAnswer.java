package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "qna_answers", indexes = {
        @Index(name = "idx_qna_id_answer_type", columnList = "qna_id, answer_type")
})
@Getter
@Setter
public class QnaAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long answer_id;

    @ManyToOne
    @JoinColumn(name = "qna_id", nullable = false)
    private QnaQuestion qna_question;

    @Column(name = "answer_type")
    private String answer_type;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "origin")
    private String origin;

    @Column(name = "model")
    private String model;

    @Column(name = "confidence")
    private Float confidence;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User created_by;

    @ManyToOne
    @JoinColumn(name = "practice_session_id")
    private PracticeSession practice_session;

    @Column(name = "start_ms")
    private Integer start_ms;

    @Column(name = "end_ms")
    private Integer end_ms;

    @Column(name = "raw_stt", columnDefinition = "TEXT")
    private String raw_stt;

    @Column(name = "created_at")
    private LocalDateTime created_at;

    @Column(name = "updated_at")
    private LocalDateTime updated_at;
}

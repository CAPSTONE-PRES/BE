package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "qna_questions")
@Getter
@Setter
public class QnaQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "qna_id")
    private Long qnaId;

    @ManyToOne
    @JoinColumn(name = "file_id", nullable = false)
    private PresentationFile presentationFile;

    @Column(name = "title")
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "origin")
    private String origin;

    @Column(name = "model")
    private String model;

    @Column(name = "prompt_hash")
    private String prompt_hash;

    @Column(name = "confidence")
    private Float confidence;

    @Column(name = "tags")
    private String tags;

    @Column(name = "status")
    private String status;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User created_by;

    @ManyToOne
    @JoinColumn(name = "assigned_to")
    private User assigned_to;

    @Column(name = "answer_accepted_id")
    private Long answer_accepted_id;

    @Column(name = "created_at")
    private LocalDateTime created_at;

    @Column(name = "updated_at")
    private LocalDateTime updated_at;
}

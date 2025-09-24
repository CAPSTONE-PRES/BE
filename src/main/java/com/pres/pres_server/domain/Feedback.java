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

    @OneToOne
    @JoinColumn(name = "session_id")
    private PracticeSession practiceSessionId;
}

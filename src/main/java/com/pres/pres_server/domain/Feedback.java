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
    private Long feedback_id;

    @Column(name = "spm_score")
    private int spm_score;

    @Column(name = "filler_score")
    private int filler_score;

    @Column(name = "repeat_score")
    private int repeat_score;

    @Column(name = "total_score")
    private int total_score;

    @Column(name = "grade")
    private String grade;

    @OneToOne
    @JoinColumn(name = "session_id")
    private PracticeSession practice_session;
}

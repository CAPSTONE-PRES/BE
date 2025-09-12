package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Time;
import java.time.LocalDateTime;

@Entity
@Table(name = "practice_session")
@Getter
@Setter
public class PracticeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long session_id;

    @Column(name = "audio_url")
    private String audio_url;

    @Column(name = "stt_text")
    private String stt_text;

    @Column(name = "practiced_at")
    private LocalDateTime practiced_at;

    @Column(name = "duration")
    private Time duration; //Time? int?

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

}

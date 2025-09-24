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
    private Long sessionId;

    @Column(name = "audio_url")
    private String audioUrl;

    @Column(name = "stt_text")
    private String sttText;

    @Column(name = "practiced_at")
    private LocalDateTime practicedAt;

    @Column(name = "duration")
    private Time duration; // Time? int?

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private Project projectId;

}

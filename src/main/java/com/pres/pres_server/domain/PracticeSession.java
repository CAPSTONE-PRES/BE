package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Time;
import java.time.LocalDateTime;

@Entity
@Table(name = "practice_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PracticeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "audio_url")
    private String audioUrl;

    @Column(name = "stt_text", columnDefinition = "TEXT")
    private String sttText;

    @Column(name = "practiced_at")
    private LocalDateTime practicedAt;

    @Column(name = "duration")
    private Time duration; // Time? int?

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /**
     * 오디오 URL 업데이트
     */
    public void updateAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    /**
     * STT 텍스트 업데이트
     */
    public void updateSttText(String sttText) {
        this.sttText = sttText;
    }

    /**
     * 세션 종료 (duration 계산)
     */
    public void updateDuration(Time duration) {
        this.duration = duration;
    }
}

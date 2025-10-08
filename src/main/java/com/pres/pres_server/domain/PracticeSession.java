package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.*;

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
    private Double durationSeconds; // 오디오 길이 (초, 소수점 포함)

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
     * 오디오 길이 업데이트 (초 단위, 소수점 포함)
     */
    public void updateDuration(Double durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
}

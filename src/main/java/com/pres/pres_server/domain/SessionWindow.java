package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 연습 세션의 30초 단위 윈도우별 분석 데이터
 */
@Entity
@Table(name = "session_windows", indexes = {
        @Index(name = "idx_session_id", columnList = "session_id")
})
@Getter
@Setter
public class SessionWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "window_id")
    private Long windowId;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private PracticeSession practiceSession;

    @Column(name = "window_index", nullable = false)
    private int windowIndex;

    @Column(name = "start_time", nullable = false)
    private double startTime;

    @Column(name = "end_time", nullable = false)
    private double endTime;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    /**
     * 필러워드 카운트 (JSON 형식)
     * 예: {"음": 3, "어": 2, "그": 1}
     */
    @Column(name = "filler_counts", columnDefinition = "json")
    private String fillerCounts;

    @Column(name = "spm")
    private Integer spm;

    @Column(name = "spm_score")
    private Integer spmScore;

    /**
     * 분석 상태
     * SUCCESS: 정상 분석 완료
     * FAILED: 분석 실패 (재분석 필요)
     */
    @Column(name = "status", length = 20)
    private String status;

    /**
     * 에러 메시지 (실패 시)
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}

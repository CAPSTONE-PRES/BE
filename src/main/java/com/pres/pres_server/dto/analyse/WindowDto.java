package com.pres.pres_server.dto.analyse;

import java.util.Map;

import lombok.Data;

@Data
public class WindowDto {
    private double startSec;
    private double endSec;
    private String transcript; // Whisper로부터 받은 텍스트
    private Map<String, Integer> fillers; // 추임새 카운트 맵
    private int spm;
    private int spmScore;
    private String status; // SUCCESS, FAILED
    private String errorMessage; // 실패 시 에러 메시지

    public WindowDto(
            double startSec,
            double endSec,
            String transcript,
            Map<String, Integer> fillers,
            int spm,
            int spmScore) {
        this.startSec = startSec;
        this.endSec = endSec;
        this.transcript = transcript;
        this.fillers = fillers;
        this.spm = spm;
        this.spmScore = spmScore;
        this.status = "SUCCESS"; // 기본값
        this.errorMessage = null;
    }

    // 실패한 윈도우 생성용 생성자
    public WindowDto(
            double startSec,
            double endSec,
            String status,
            String errorMessage) {
        this.startSec = startSec;
        this.endSec = endSec;
        this.transcript = "[분석 실패]";
        this.fillers = new java.util.HashMap<>();
        this.spm = 0;
        this.spmScore = 0;
        this.status = status;
        this.errorMessage = errorMessage;
    }
}

package com.pres.pres_server.dto;

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
    }

}

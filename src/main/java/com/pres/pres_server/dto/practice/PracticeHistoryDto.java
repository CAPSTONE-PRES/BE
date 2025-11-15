package com.pres.pres_server.dto.practice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
public class PracticeHistoryDto {
    private Long sessionId;
    private LocalDateTime practicedAt; // 연습 일자
    private int totalScore;        // 점수
}


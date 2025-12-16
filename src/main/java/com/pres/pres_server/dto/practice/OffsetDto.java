package com.pres.pres_server.dto.practice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 오프셋 DTO: 하이라이트용 (begin inclusive, end exclusive)
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class OffsetDto {
    private Integer begin;
    private Integer end;
    private Integer slideIndex; // slideNumber (프론트 제공 라벨, 0-based)
    private Integer visitIndex; // 동일 슬라이드의 방문 순서 (0-based, 재방문 구분용)

    // 하이라이트 대상 텍스트
    private String text;
}

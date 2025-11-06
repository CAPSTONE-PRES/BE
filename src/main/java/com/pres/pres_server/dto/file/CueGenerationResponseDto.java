package com.pres.pres_server.dto.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 큐카드 생성 결과 응답 DTO (성공/실패만 반환)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CueGenerationResponseDto {

    /**
     * 성공 여부
     */
    private boolean success;

    /**
     * 응답 메시지
     */
    private String message;

    /**
     * 처리된 슬라이드 수
     */
    private int totalSlides;

    /**
     * 성공한 슬라이드 수
     */
    private int successCount;

    /**
     * 실패한 슬라이드 수
     */
    private int failureCount;

    /**
     * 실패한 슬라이드 정보 (선택적)
     * key: 슬라이드 번호, value: 에러 메시지
     */
    private Map<Integer, String> errors;
}
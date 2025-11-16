package com.pres.pres_server.dto.qna;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QnA 답변 제출 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QnaAnswerResponseDto {
    private Long answerId;
    private Long questionId;
    private String sttText;
    private String message;
    // 비교 결과가 이미 존재하는 경우 반환되는 비교 ID
    private Long comparisonId;
    // 비교 결과가 준비되어 있는지 여부
    private boolean comparisonAvailable;
}

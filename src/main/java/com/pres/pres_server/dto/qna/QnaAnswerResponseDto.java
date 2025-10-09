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
}

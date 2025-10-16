package com.pres.pres_server.dto.qna;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QnA 질문 DTO (질문만, 답변 제외)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QnaQuestionDto {
    private Long questionId;
    private String questionBody;
}

package com.pres.pres_server.dto.qna;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * QnA 비교 결과 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QnaComparisonDto {
    private Long comparisonId;
    private Long questionId;
    private String question;
    private String idealAnswer;
    private String userAnswer;
    private Float similarity;
    private Float keywordRecall;
    private Float coverage;
    private List<String> feedback;
    private List<String> missingKeywords;
}

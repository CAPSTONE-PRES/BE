package com.pres.pres_server.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class QnaDetailDto {
    private Long questionId;
    private String questionBody;
    private List<QnaAnswerDto> answers;
    private String origin;
    private String model;
    private Float confidence;
    private LocalDateTime createdAt;

    @Data
    public static class QnaAnswerDto {
        private Long answerId;
        private String answerBody;
        private String answerType;
        private String origin;
        private String model;
        private Float confidence;
        private LocalDateTime createdAt;
    }
}
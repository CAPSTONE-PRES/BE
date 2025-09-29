package com.pres.pres_server.dto;

import lombok.Data;
import java.util.List;

@Data
public class QnaListDto {
    private Long cueCardId;
    private int slideNumber;
    private int totalQuestions;
    private List<QnaDetailDto> questions;

    public QnaListDto() {
        this.totalQuestions = 0;
    }

    public void setQuestions(List<QnaDetailDto> questions) {
        this.questions = questions;
        this.totalQuestions = questions != null ? questions.size() : 0;
    }
}
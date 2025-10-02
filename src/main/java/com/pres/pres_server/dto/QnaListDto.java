package com.pres.pres_server.dto;

import lombok.Data;
import java.util.List;

@Data
public class QnaListDto {
    private Long fileId;
    private int totalQuestions;
    private List<QnaDetailDto> questions;
    private String status;

    public QnaListDto() {
        this.totalQuestions = 0;
        this.status = "success";
    }

    public void setQuestions(List<QnaDetailDto> questions) {
        this.questions = questions;
        this.totalQuestions = questions != null ? questions.size() : 0;
    }
}
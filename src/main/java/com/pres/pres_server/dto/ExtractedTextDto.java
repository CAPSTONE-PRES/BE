package com.pres.pres_server.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedTextDto {
    // 추출된 텍스트
    private String fullText;
    private List<String> slideTexts;

    // 텍스트가 부족한 슬라이드 정보
    private List<Integer> insufficientSlides;
    private String insufficientMessage;

    // 기존 생성자와의 호환성을 위한 생성자
    public ExtractedTextDto(String fullText, List<String> slideTexts) {
        this.fullText = fullText;
        this.slideTexts = slideTexts;
    }
}
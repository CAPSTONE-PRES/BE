package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "extracted_texts")
@Getter
@Setter
@NoArgsConstructor
public class ExtractedText {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "extracted_text_id")
    private Long extractedTextId;

    // 전체 추출된 텍스트
    @Column(name = "full_text", columnDefinition = "LONGTEXT")
    private String fullText;

    // 슬라이드별/페이지별 텍스트
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "slide_texts", joinColumns = @JoinColumn(name = "extracted_text_id"))
    @Column(name = "slide_text", columnDefinition = "TEXT")
    private List<String> slideTexts = new ArrayList<>();

    // 텍스트 품질 정보
    @Column(name = "is_sufficient")
    private Boolean isSufficient;

    @Column(name = "insufficient_slides", columnDefinition = "TEXT")
    private String insufficientSlides; // JSON 형태로 저장

    @Column(name = "insufficient_message", columnDefinition = "TEXT")
    private String insufficientMessage;

    // 추출 시점
    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;

    // PresentationFile과의 1:1 관계
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private PresentationFile presentationFile;

    // 생성자
    public ExtractedText(PresentationFile presentationFile, String fullText, List<String> slideTexts) {
        this.presentationFile = presentationFile;
        this.fullText = fullText;
        this.slideTexts = slideTexts;
        this.extractedAt = LocalDateTime.now();
    }
}
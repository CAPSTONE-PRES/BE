package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "presentation_files")
@Getter
@Setter
public class PresentationFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long fileId;

    // 외부 접근 URL
    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    // 내부 저장 경로 (서버/S3 등)
    @Column(name = "file_path", nullable = false)
    private String filePath;

    // 실제 파일 이름
    @Column(name = "original_name", nullable = false)
    private String originalName;

    // 저장된 파일 이름
    @Column(name = "save_name", nullable = false)
    private String saveName;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    // 파일 크기 (byte 단위)
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    // 슬라이드별 텍스트 리스트
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "presentation_slide_texts", joinColumns = @JoinColumn(name = "file_id"))
    @Column(name = "slide_text", columnDefinition = "TEXT")
    private java.util.List<String> slideTexts = new java.util.ArrayList<>();

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    // ExtractedText와의 1:1 관계
    @OneToOne(mappedBy = "presentationFile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ExtractedText extractedText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // 업로더 정보 (User)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;
}
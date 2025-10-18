package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "presentation_images",
        uniqueConstraints = @UniqueConstraint(columnNames = {"file_id", "page_no"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PresentationImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;

    // 부모 파일 (PresentationFile)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private PresentationFile file;

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber; // 슬라이드 또는 페이지 번호 (1-based)

    @Column(nullable = false, length = 1024)
    private String path;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(nullable = false)
    private Long size;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

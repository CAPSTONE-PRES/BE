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
    private Long file_id;

    @Column(name = "file_url", nullable = false)
    private String file_url;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extracted_text;

    @Column(name = "uploaded_at")
    private LocalDateTime uploaded_at;

    @OneToOne
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;
}
package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "cue_cards",
        uniqueConstraints = @UniqueConstraint(columnNames = { "file_id", "slide_number", "mode",
       "section_no_key" }),
        indexes = {
        @Index(name = "ix_cue_file_slide", columnList = "file_id, slide_number"),
        @Index(name = "ix_cue_qr_slug", columnList = "qr_slug")
})

@Getter
@Setter
public class CueCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cue_id")
    private Long cueId;

    @ManyToOne
    @JoinColumn(name = "file_id", nullable = false)
    private PresentationFile presentationFile;

    @Column(name = "slide_number", nullable = false)
    private int slideNumber;

    @Column(name = "section_number")
    private Integer sectionNumber;     // BASIC == ADVANCED

    @Column(
            name = "section_no_key",
            insertable = false, updatable = false,
            columnDefinition = "INT AS (IFNULL(section_number, 0)) STORED"
    )
    private Integer sectionNoKey;

    @Column(name = "section_keyword")
    private String  sectionKeyword;    // BASIC==ADVANCED

    @Column(name = "content", columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "mode", nullable = false)
    @Enumerated(EnumType.STRING)
    private Mode mode;

    @Column(name = "qr_slug", unique = true, length = 22)
    private String qrSlug;

    @Column(name = "qr_url", length = 512)
    private String qrUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    public enum Mode {BASIC, ADVANCED};
}

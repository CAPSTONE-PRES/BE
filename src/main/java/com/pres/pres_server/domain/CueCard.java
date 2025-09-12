package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;



@Entity
@Table(name = "cue_cards", uniqueConstraints = @UniqueConstraint(columnNames = { "file_id", "slide_number" }))
@Getter
@Setter
public class CueCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cue_id")
    private Long cue_id;

    @ManyToOne
    @JoinColumn(name = "file_id", nullable = false)
    private PresentationFile presentation_file;

    @Column(name = "slide_number", nullable = false)
    private int slide_number;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "mode")
    private String mode;

    @Column(name = "qr_url")
    private String qr_url;
}

package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "CueCard")
@Getter
@Setter
public class CueCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cueId;
    private int slideNumber;
    private String content;
    private String mode;
    private String qrUrl;

    @OneToOne
    @JoinColumn(name = "fileId")
    private PresentationFile presentationFile;

}

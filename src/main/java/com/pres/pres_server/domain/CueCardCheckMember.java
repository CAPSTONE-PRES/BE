package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "cue_card_checks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cue_id", "user_id"})
)
@Getter
@Setter
public class CueCardCheckMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cue_id", nullable = false)
    private CueCard cueCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "checked", nullable = false)
    private boolean checked;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;
}

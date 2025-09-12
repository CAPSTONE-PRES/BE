package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Getter
@Setter
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long comment_id;

    @ManyToOne
    @JoinColumn(name = "cue_id", nullable = false)
    private CueCard cue_card;

    @ManyToOne
    @JoinColumn(name = "author_user_id", nullable = false)
    private User author_user;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "location")
    private String location;

    @Column(name = "is_member_checked", nullable = false)
    private boolean is_member_checked = false;

    @Column(name = "created_at")
    private LocalDateTime created_at;
}
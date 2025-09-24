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
    private Long commentId;

    @ManyToOne
    @JoinColumn(name = "cue_id", nullable = false)
    private CueCard cueCard;

    @ManyToOne
    @JoinColumn(name = "author_user_id", nullable = false)
    private User authorUser;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "location")
    private String location;

    @Column(name = "is_member_checked", nullable = false)
    private boolean isMemberChecked = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
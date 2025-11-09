package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "comments")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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


    // 대댓글 위한 자기 참조 추가
    @ManyToOne
    @JoinColumn(name = "parent_comment_id")
    private Comment parentComment;

    @OneToMany(mappedBy = "parentComment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> replies = new ArrayList<>();
}
package com.pres.pres_server.dto.Comment;

import com.pres.pres_server.domain.Comment;
import com.pres.pres_server.domain.User;
import lombok.Builder;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommentResponseDTO {
    private Long commentId;
    private Long authorUserId;
    private String authorName;
    private String authorProfileImageUrl;
    private String content;
    private String location;
    private boolean editable;
    private LocalDateTime createdAt;
    private List<ReplyDTO> replies;

    public static CommentResponseDTO from(Comment comment, User currentUser) {
        return CommentResponseDTO.builder()
                .commentId(comment.getCommentId())
                .authorUserId(comment.getAuthorUser().getId())
                .authorName(comment.getAuthorUser().getUsername())
                .authorProfileImageUrl(comment.getAuthorUser().getProfileImageUrl())
                .content(comment.getContent())
                .location(comment.getLocation())
                .editable(comment.getAuthorUser().getId().equals(currentUser.getId()))
                .createdAt(comment.getCreatedAt())
                .replies(comment.getReplies().stream()
                        .map(ReplyDTO::from)
                        .collect(Collectors.toList()))
                .build();
    }
}
package com.pres.pres_server.dto.Comment;

import com.pres.pres_server.domain.Comment;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.service.user.UserService;
import lombok.Builder;
import lombok.*;

import java.time.LocalDateTime;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReplyDTO {
    private Long commentId;
    private Long authorUserId;
    private String authorName;
    private String authorProfileImageUrl;
    private boolean editable;
    private String content;
    private LocalDateTime createdAt;

    public static ReplyDTO from(Comment comment, User currentUser, UserService userService) {
        return ReplyDTO.builder()
                .commentId(comment.getCommentId())
                .authorUserId(comment.getAuthorUser().getId())
                .authorName(comment.getAuthorUser().getUsername())
                .authorProfileImageUrl(userService.resolveProfileUrl(comment.getAuthorUser()))
                .editable(comment.getAuthorUser().getId().equals(currentUser.getId()))
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}

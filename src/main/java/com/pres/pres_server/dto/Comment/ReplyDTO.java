package com.pres.pres_server.dto.Comment;

import com.pres.pres_server.domain.Comment;
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
    private String content;

    public static ReplyDTO from(Comment comment) {
        return ReplyDTO.builder()
                .commentId(comment.getCommentId())
                .authorUserId(comment.getAuthorUser().getId())
                .authorName(comment.getAuthorUser().getUsername())
                .authorProfileImageUrl(comment.getAuthorUser().getProfileImageUrl())
                .content(comment.getContent())
                .build();
    }
}

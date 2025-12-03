package com.pres.pres_server.dto.Comment;

import com.pres.pres_server.domain.Comment;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.service.user.UserService;
import lombok.Builder;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
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

    public static CommentResponseDTO from(Comment comment, User currentUser,UserService userService) {
        return CommentResponseDTO.builder()
                .commentId(comment.getCommentId())
                .authorUserId(comment.getAuthorUser().getId())
                .authorName(comment.getAuthorUser().getUsername())
                .authorProfileImageUrl(userService.resolveProfileUrl(comment.getAuthorUser()))
                .content(comment.getContent())
                .location(comment.getLocation())
                .editable(comment.getAuthorUser().getId().equals(currentUser.getId()))
                .createdAt(comment.getCreatedAt())
                .replies(comment.getReplies() != null ?
                        comment.getReplies().stream()
                                .map(reply -> ReplyDTO.from(reply, currentUser, userService)) // UserService 전달
                                .collect(Collectors.toList())
                        : new ArrayList<>())
                .build();
    }

}
package com.pres.pres_server.dto.CueCard;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class CommentDetailDTO {
    private Long commentId;
    private Long authorUserId;
    private String authorName;
    private String authorProfileImageUrl;
    private String content;
    private String location;
    private LocalDateTime createdAt;
}
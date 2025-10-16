package com.pres.pres_server.dto.Comment;

import lombok.Builder;
import lombok.*;

@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponseDTO {
    private Long commentId;
    private String message;
}

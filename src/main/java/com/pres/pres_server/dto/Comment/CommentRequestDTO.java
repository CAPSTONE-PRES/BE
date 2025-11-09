package com.pres.pres_server.dto.Comment;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommentRequestDTO {
    private String content;
    private String location;
}

package com.pres.pres_server.dto.Comment;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommentRequestDTO {
    private Long userId;
    private Long cueId;
    private String location;
    private String content;
}

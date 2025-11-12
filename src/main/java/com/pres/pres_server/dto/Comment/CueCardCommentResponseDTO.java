package com.pres.pres_server.dto.Comment;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CueCardCommentResponseDTO {
    private Long cueId;
    private List<CommentResponseDTO> comments;
}
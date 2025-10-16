package com.pres.pres_server.dto.CueCard;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class CueCardCommentDTO {
    private Long cueId;
    private List<CommentDetailDTO> comments;
}

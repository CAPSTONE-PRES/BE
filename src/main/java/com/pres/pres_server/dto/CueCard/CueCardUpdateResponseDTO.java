package com.pres.pres_server.dto.CueCard;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CueCardUpdateResponseDTO {
    private Long cueId;
    private String updatedContent;
    private String message;
}
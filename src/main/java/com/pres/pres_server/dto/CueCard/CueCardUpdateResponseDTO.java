package com.pres.pres_server.dto.CueCard;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class CueCardUpdateResponseDTO {
    private Long fileId;
    private int slideNumber;
    private String message;
}
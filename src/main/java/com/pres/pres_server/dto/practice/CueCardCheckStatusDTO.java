package com.pres.pres_server.dto.practice;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CueCardCheckStatusDTO {
    private Long cueId;
    private boolean checked;
}

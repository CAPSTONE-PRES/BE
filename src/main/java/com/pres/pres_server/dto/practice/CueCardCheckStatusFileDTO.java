package com.pres.pres_server.dto.practice;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class CueCardCheckStatusFileDTO {
    private Long fileId;
    private List<Long> checkedCueIds;
}
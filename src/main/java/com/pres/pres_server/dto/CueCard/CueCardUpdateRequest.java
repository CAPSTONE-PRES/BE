package com.pres.pres_server.dto.CueCard;

import lombok.*;

import java.util.List;

@Getter
@Setter
public class CueCardUpdateRequest {
    private List<CueCardContentDTO> cueCards; // 업데이트할 큐카드 목록
}

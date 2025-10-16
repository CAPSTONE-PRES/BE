package com.pres.pres_server.dto.CueCard;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class CueCardCreateResponseDTO {
    private Long fileId;
    private int slideNumber;
    private List<CueCardContentDTO> cueCards; // 조회/수정 후 큐카드 내용
}
package com.pres.pres_server.dto.file;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CueCardDto {
    private Long fileId;
    private List<CueSlideDto> slides; //슬라이드별 큐카드
    private Map<Integer, String> errors; //실패한 슬라이드 ->
}

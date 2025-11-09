package com.pres.pres_server.dto.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Data @NoArgsConstructor
@AllArgsConstructor
public class CueSlideDto {
    private int slideNumber;
    private List<CueBasicDto> basic; // BASIC 섹션들
    private List<CueAdvancedDto> advanced;             // ADVANCED 섹션들
    private String qrSlug; //NULLABLE
    private String qrUrl; //NULLABLE
}

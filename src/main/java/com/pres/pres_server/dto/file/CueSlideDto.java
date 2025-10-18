package com.pres.pres_server.dto.file;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class CueSlideDto {

    private int slideNumber;
    private List<CueBasicDto> basic; // BASIC 섹션들
    private String advanced;             // ADVANCED 텍스트

    private String qrSlug;
    private String qrUrl;
}

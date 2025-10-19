package com.pres.pres_server.dto.file;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CueBasicDto {
    private int section;
    private String keyword;
    private String text;
}

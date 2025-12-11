package com.pres.pres_server.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsufficientSlidePreviewDto {
    private Integer slideNumber;
    private String url;
}

package com.pres.pres_server.service.analyse.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Whisper segment DTO (start, end, text)
 */
@Data
@Builder
public class WhisperSegment {
    private double start;
    private double end;
    private String text;
}

package com.pres.pres_server.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CueCardDto {
    private Long fileId;
    private String extractedText;
    private List<String> cueCards;
}

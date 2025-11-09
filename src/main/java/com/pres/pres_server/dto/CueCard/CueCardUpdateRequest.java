package com.pres.pres_server.dto.CueCard;

import com.pres.pres_server.domain.CueCard;
import lombok.*;

import java.util.List;

@Getter
@Setter
public class CueCardUpdateRequest {
    private CueCard.Mode mode; // BASIC or ADVANCED
    private String content;
}

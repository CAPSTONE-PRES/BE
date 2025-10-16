package com.pres.pres_server.dto.practice;

import com.pres.pres_server.dto.Workspace.WorkspaceMemberDTO;
import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class CueCardUncheckedDTO { // 최종 응답
    private Long fileId;
    private int slideNumber;
    private List<CueCardUncheckedMemberDTO> cueCards;
}
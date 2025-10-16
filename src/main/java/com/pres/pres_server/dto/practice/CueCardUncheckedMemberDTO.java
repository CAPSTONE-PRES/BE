package com.pres.pres_server.dto.practice;

import com.pres.pres_server.dto.Workspace.WorkspaceMemberDTO;
import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class CueCardUncheckedMemberDTO {
    private Long cueId;
    private List<WorkspaceMemberDTO> uncheckedMembers;
}
package com.pres.pres_server.dto.Workspace;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceInfoDTO {
    private String workspaceName;
    private List<String> workspaceTimeList;  // classtime1~3
    private String workspaceOwnerName;
    private String workspaceOwnerProfileUrl;
    private List<WorkspaceMemberDTO> workspaceMemberList;
}
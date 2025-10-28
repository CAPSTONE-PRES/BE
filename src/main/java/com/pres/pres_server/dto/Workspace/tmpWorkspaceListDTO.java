package com.pres.pres_server.dto.Workspace;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class tmpWorkspaceListDTO {
    private Long workspaceId;
    private String workspaceName;
    private boolean IsOwner;
    private List<String> workspaceTimeList;  // classtime1~3
    private Long workspaceOwnerId;
    private String workspaceOwnerName;
    private String workspaceOwnerProfileUrl;
    private List<WorkspaceMemberDTO> workspaceMemberList;
}

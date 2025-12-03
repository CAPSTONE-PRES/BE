package com.pres.pres_server.dto.Workspace;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.domain.WorkSpace;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceInfoDTO {
    private Long workspaceId;
    private String workspaceName;
    private boolean IsOwner;
    private String lastVisited;
    private List<String> workspaceTimeList;  // classtime1~3
    private String workspaceOwnerName;
    private String workspaceOwnerProfileUrl;
    private List<WorkspaceMemberDTO> workspaceMemberList;

}
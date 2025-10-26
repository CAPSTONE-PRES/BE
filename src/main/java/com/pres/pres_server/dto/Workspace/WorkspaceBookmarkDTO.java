package com.pres.pres_server.dto.Workspace;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class WorkspaceBookmarkDTO {
    private Long workspaceId;
    private String workspaceName;
    private List<String> workspaceTimeList;
}
package com.pres.pres_server.dto.Workspace;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class WorkspaceRequest {
    private String workspaceName;
    private List<String> workspaceMemberList;
    private List<String> workspaceTimeList;
}

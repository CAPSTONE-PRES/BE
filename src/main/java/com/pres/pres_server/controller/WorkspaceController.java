package com.pres.pres_server.controller;

import com.pres.pres_server.dto.Workspace.WorkspaceInfoDTO;
import com.pres.pres_server.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workSpaceService;

    @GetMapping("/{workspaceId}/info")
    public WorkspaceInfoDTO getWorkspaceInfo(@PathVariable Long workspaceId) {
        return workSpaceService.getWorkspaceInfo(workspaceId);
    }
}

package com.pres.pres_server.service;

import com.pres.pres_server.domain.TeamMember;
import com.pres.pres_server.domain.WorkSpace;
import com.pres.pres_server.dto.Workspace.WorkspaceInfoDTO;
import com.pres.pres_server.dto.Workspace.WorkspaceMemberDTO;
import com.pres.pres_server.repository.TeamMemberRepository;
import com.pres.pres_server.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workSpaceRepository;
    private final TeamMemberRepository teamMemberRepository;

    public WorkspaceInfoDTO getWorkspaceInfo(Long workspaceId) {
        WorkSpace workspace = workSpaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("워크스페이스 없음"));

        WorkspaceInfoDTO dto = new WorkspaceInfoDTO();
        dto.setWorkspaceName(workspace.getWorkspaceName());
        dto.setWorkspaceOwnerName(workspace.getOwnerUserId().getUsername());
        dto.setWorkspaceOwnerProfileUrl(workspace.getOwnerUserId().getProfileImageUrl());

        // classtime1~3 -> 리스트 변환 (String)
        List<String> timeList = new ArrayList<>();
        if (workspace.getClasstime1() != null) timeList.add(workspace.getClasstime1());
        if (workspace.getClasstime2() != null) timeList.add(workspace.getClasstime2());
        if (workspace.getClasstime3() != null) timeList.add(workspace.getClasstime3());
        dto.setWorkspaceTimeList(timeList);

        // 팀 멤버 조회
        List<TeamMember> teamMembers = teamMemberRepository.findByWorkspace_WorkspaceId(workspaceId);
        List<WorkspaceMemberDTO> members = teamMembers.stream()
                .map(member -> new WorkspaceMemberDTO(
                        member.getMemberId(), // Long
                        member.getUser().getUsername(),
                        member.getUser().getProfileImageUrl()
                ))
                .collect(Collectors.toList());
        dto.setWorkspaceMemberList(members);

        return dto;
    }

}

package com.pres.pres_server.service;

import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.TeamMember;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.domain.WorkSpace;
import com.pres.pres_server.dto.Projects.ProjectListDTO;
import com.pres.pres_server.dto.Workspace.WorkspaceRequest;
import com.pres.pres_server.repository.ProjectRepository;
import com.pres.pres_server.repository.TeamMemberRepository;
import com.pres.pres_server.repository.UserRepository;
import com.pres.pres_server.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private final WorkspaceRepository workSpaceRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public WorkSpace createWorkspace(WorkspaceRequest request, User ownerUser) {
        // 워크스페이스 생성
        WorkSpace workspace = new WorkSpace();
        workspace.setWorkspaceName(request.getWorkspaceName());
        workspace.setOwnerUser(ownerUser);
        workspace.setCreated_at(LocalDateTime.now());
        workSpaceRepository.save(workspace);

        // 팀 멤버가 있다면 TeamMember로 저장
        if (request.getWorkspaceMemberList() != null && !request.getWorkspaceMemberList().isEmpty()) {
            for (String memberEmail : request.getWorkspaceMemberList()) {
                // 이메일로 User 조회
                User memberUser = userRepository.findByEmail(memberEmail)
                        .orElseThrow(() -> new RuntimeException("User not found: " + memberEmail));

                TeamMember teamMember = new TeamMember();
                teamMember.setWorkspace(workspace);
                teamMember.setUser(memberUser);
                teamMember.setRole("MEMBER");
                teamMember.setInvited_at(LocalDateTime.now());
                teamMemberRepository.save(teamMember);
            }
        }

        return workspace;
    }
}
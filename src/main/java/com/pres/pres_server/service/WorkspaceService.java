package com.pres.pres_server.service;

import com.pres.pres_server.domain.TeamMember;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.domain.VisitLog;
import com.pres.pres_server.domain.WorkSpace;
import com.pres.pres_server.dto.Workspace.TeamMemberEditRequest;
import com.pres.pres_server.dto.Workspace.WorkspaceInfoDTO;
import com.pres.pres_server.dto.Workspace.WorkspaceMemberDTO;
import com.pres.pres_server.dto.Workspace.WorkspaceRequest;
import com.pres.pres_server.repository.TeamMemberRepository;
import com.pres.pres_server.repository.UserRepository;
import com.pres.pres_server.repository.VisitLogRepository;
import com.pres.pres_server.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final VisitLogService visitLogService;
    private final VisitLogRepository visitLogRepository;

    @Transactional
    public Long createWorkspace(WorkspaceRequest request, User ownerUser) {
        WorkSpace workspace = new WorkSpace();
        workspace.setWorkspaceName(request.getWorkspaceName());
        workspace.setOwnerUserId(ownerUser);
        workspace.setCreatedAt(LocalDateTime.now());
        workspaceRepository.save(workspace);

        // 팀 멤버 이메일 리스트 등록
        if (request.getWorkspaceMemberList() != null && !request.getWorkspaceMemberList().isEmpty()) {
            for (String memberEmail : request.getWorkspaceMemberList()) {
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

        return workspace.getWorkspaceId();
    }

    // 워크스페이스 팀 멤버 수정
    @Transactional
    public void editTeamMembers(Long workspaceId, TeamMemberEditRequest request, User currentUser) {

        WorkSpace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스 없음"));

        // 소유자 체크
        if (!workspace.getOwnerUserId().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("워크스페이스 소유자가 아닙니다");
        }

        // 이메일로 유저 조회
        List<User> users = userRepository.findByEmailIn(request.getEmails());

        if (users.size() != request.getEmails().size()) {
            throw new IllegalArgumentException("존재하지 않는 이메일이 포함되어 있습니다");
        }

        // 기존 팀 멤버 삭제
        teamMemberRepository.deleteByWorkspace(workspace);

        // 새 팀 멤버 생성
        List<TeamMember> newMembers = users.stream()
                .map(u -> {
                    TeamMember tm = new TeamMember();
                    tm.setWorkspace(workspace);
                    tm.setUser(u);
                    tm.setRole("MEMBER"); // 기본 역할 설정, 필요시 변경 가능
                    tm.setInvited_at(LocalDateTime.now());
                    return tm;
                })
                .collect(Collectors.toList());

        teamMemberRepository.saveAll(newMembers);
    }

    // 워크스페이스 정보 수정
    @Transactional
    public void editWorkspace(Long workspaceId, WorkspaceRequest request, User user) {
        WorkSpace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스가 존재하지 않습니다."));

        if (!workspace.getOwnerUserId().getId().equals(user.getId())) {
            throw new RuntimeException("권한이 없습니다.");
        }

        // 워크스페이스 이름 업데이트
        workspace.setWorkspaceName(request.getWorkspaceName());

        // 기존 팀 멤버 삭제
        teamMemberRepository.deleteByWorkspace(workspace);

        // 새로운 팀 멤버 등록
        if (request.getWorkspaceMemberList() != null) {
            for (String memberEmail : request.getWorkspaceMemberList()) {
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

        // 필요 시 워크스페이스 시간 업데이트 가능
        // workspace.setWorkspaceTimeList(request.getWorkspaceTimeList()); // List<String>로 관리 시
    }

    // 특정 워크스페이스 삭제 서비스
    @Transactional
    public void deleteWorkspace(Long workspaceId, User user) {
        WorkSpace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스가 존재하지 않습니다."));

        if (!workspace.getOwnerUserId().getId().equals(user.getId())) {
            throw new RuntimeException("권한이 없습니다.");
        }

        workspaceRepository.delete(workspace);
    }


    public WorkspaceInfoDTO getWorkspaceInfo(User user,Long workspaceId) {
        WorkSpace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("워크스페이스 없음"));

        // 방문 로그 upsert
        visitLogService.upsertVisitLog(user, workspace, null);

        WorkspaceInfoDTO dto = new WorkspaceInfoDTO();
        dto.setWorkspaceName(workspace.getWorkspaceName());
        dto.setWorkspaceOwnerName(workspace.getOwnerUserId().getUsername());
        dto.setWorkspaceOwnerProfileUrl(workspace.getOwnerUserId().getProfileImageUrl());

        // classtime1~3 -> 리스트 변환 (String)
        List<String> timeList = new ArrayList<>();
        if (workspace.getClasstime1() != null)
            timeList.add(workspace.getClasstime1());
        if (workspace.getClasstime2() != null)
            timeList.add(workspace.getClasstime2());
        if (workspace.getClasstime3() != null)
            timeList.add(workspace.getClasstime3());
        dto.setWorkspaceTimeList(timeList);

        // 팀 멤버 조회
        List<TeamMember> teamMembers = teamMemberRepository.findByWorkspace_WorkspaceId(workspaceId);
        List<WorkspaceMemberDTO> members = teamMembers.stream()
                .map(member -> new WorkspaceMemberDTO(
                        member.getMemberId(), // Long
                        member.getUser().getUsername(),
                        member.getUser().getProfileImageUrl()))
                .collect(Collectors.toList());
        dto.setWorkspaceMemberList(members);

        return dto;
    }

    public List<WorkspaceInfoDTO> getWorkspaceList(User user, int type) {

        List<WorkSpace> workspaces;

        if (type == 2) {
            // 제목순 정렬
            workspaces = workspaceRepository.findAllByOrderByWorkspaceNameAsc();
        } else if (type == 1) {
            // 최근 방문순
            List<VisitLog> logs = visitLogRepository.findByUserOrderByVisitedAtDesc(user);

            // VisitLog 기준으로 WorkSpace 추출
            workspaces = logs.stream()
                    .map(VisitLog::getWorkspace)
                    .distinct()
                    .collect(Collectors.toList());

            List<WorkSpace> notVisited = workspaceRepository.findAll();
            notVisited.removeAll(workspaces);
            workspaces.addAll(notVisited);
        } else {
            throw new IllegalArgumentException("Invalid type: " + type);
        }

        return workspaces.stream()
                .map(ws -> {
                    WorkspaceInfoDTO dto = new WorkspaceInfoDTO();
                    dto.setWorkspaceName(ws.getWorkspaceName());
                    dto.setWorkspaceOwnerName(ws.getOwnerUserId().getUsername());
                    dto.setWorkspaceOwnerProfileUrl(ws.getOwnerUserId().getProfileImageUrl());

                    List<String> timeList = new ArrayList<>();
                    if (ws.getClasstime1() != null) timeList.add(ws.getClasstime1());
                    if (ws.getClasstime2() != null) timeList.add(ws.getClasstime2());
                    if (ws.getClasstime3() != null) timeList.add(ws.getClasstime3());
                    dto.setWorkspaceTimeList(timeList);

                    return dto;
                })
                .collect(Collectors.toList());
    }


}
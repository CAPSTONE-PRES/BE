package com.pres.pres_server.service;

import com.pres.pres_server.domain.*;
import com.pres.pres_server.dto.Workspace.*;
import com.pres.pres_server.dto.file.FileInfoDto;
import com.pres.pres_server.repository.*;
import com.pres.pres_server.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final WorkspaceBookmarkRepository workspaceBookmarkRepository;
    private final UserRepository userRepository;
    private final VisitLogService visitLogService;
    private final VisitLogRepository visitLogRepository;
    private final ProjectRepository projectRepository;


    private String buildProfileUrl(User user) {
        String baseCdnUrl = "https://d53mjm0l7jtco.cloudfront.net";
        String defaultProfile = "default-profiles/user1.svg";

        if (user.getProfileImageKey() != null && !user.getProfileImageKey().isEmpty()) {
            return baseCdnUrl + "/" + user.getProfileImageKey();
        } else {
            return baseCdnUrl + "/" + defaultProfile;
        }
    }

    @Transactional
    public Long createWorkspace(WorkspaceRequest request, User ownerUser) {
        WorkSpace workspace = new WorkSpace();
        workspace.setWorkspaceName(request.getWorkspaceName());
        workspace.setOwnerUserId(ownerUser);
        workspace.setCreatedAt(LocalDateTime.now());

        // 워크스페이스 시간 리스트 (최대 3개)
        List<String> timeList = request.getWorkspaceTimeList();
        if (timeList != null && !timeList.isEmpty()) {
            if (timeList.size() > 0) workspace.setClasstime1(timeList.get(0));
            if (timeList.size() > 1) workspace.setClasstime2(timeList.get(1));
            if (timeList.size() > 2) workspace.setClasstime3(timeList.get(2));
        }

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

        // OWNER도 팀멤버로 추가
        TeamMember ownerMember = new TeamMember();
        ownerMember.setWorkspace(workspace);
        ownerMember.setUser(ownerUser);
        ownerMember.setRole("OWNER");
        ownerMember.setInvited_at(LocalDateTime.now());
        teamMemberRepository.save(ownerMember);

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

        // 수업 시간 업데이트
        List<String> times = request.getWorkspaceTimeList();
        workspace.setClasstime1(times.size() > 0 ? times.get(0) : null);
        workspace.setClasstime2(times.size() > 1 ? times.get(1) : null);
        workspace.setClasstime3(times.size() > 2 ? times.get(2) : null);

        workspaceRepository.save(workspace);
    }

    // 특정 워크스페이스 삭제 서비스
    @Transactional
    public void deleteWorkspace(Long workspaceId, User user) {
        WorkSpace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스가 존재하지 않습니다."));

        // owner 체크
        if (!workspace.getOwnerUserId().getId().equals(user.getId())) {
            throw new RuntimeException("권한이 없습니다. 워크스페이스 소유자만 삭제 가능합니다.");
        }

        // 1. 팀 멤버 삭제
        teamMemberRepository.deleteByWorkspace(workspace);

        // 2. 방문 로그 삭제
        visitLogRepository.deleteByWorkspace(workspace);

        // 3. 프로젝트 삭제 (연관 엔티티가 있다면)
        List<Project> projects = projectRepository.findByWorkspaceId_WorkspaceId(workspaceId);
        if (!projects.isEmpty()) {
            projectRepository.deleteAll(projects);
        }

        // 4. 워크스페이스 삭제
        workspaceRepository.delete(workspace);
    }


    // 워크스페이스 정보 가져오기
    public WorkspaceInfoDTO getWorkspaceInfo(User user,Long workspaceId) {
        WorkSpace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("워크스페이스 없음"));

        // 방문 로그 upsert
        visitLogService.upsertVisitLog(user, workspace, null);

        WorkspaceInfoDTO dto = new WorkspaceInfoDTO();
        dto.setWorkspaceId(workspace.getWorkspaceId());
        dto.setWorkspaceName(workspace.getWorkspaceName());
        dto.setIsOwner(workspace.getOwnerUserId().getId().equals(user.getId()));
        // dto.setIsOwner(workspace.getOwnerUserId().getId().equals(user.getId()));
        dto.setWorkspaceOwnerName(workspace.getOwnerUserId().getUsername());
        dto.setWorkspaceOwnerProfileUrl(buildProfileUrl(workspace.getOwnerUserId()));

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
                        member.getUser().getId(),
                        member.getUser().getEmail(),
                        member.getUser().getUsername(),
                        buildProfileUrl(member.getUser())  // S3 key -> CloudFront URL 변환
                ))
                .collect(Collectors.toList());
        dto.setWorkspaceMemberList(members);

        // 가장 가까운 발표 날짜(upComingDate)
        List<Project> projects = projectRepository.findByWorkspaceId_WorkspaceId(workspaceId);
        Optional<LocalDate> nextDateOpt = projects.stream()
                .map(Project::getDueDate)
                .filter(Objects::nonNull)
                .filter(date -> date.isAfter(LocalDate.now()))
                .min(LocalDate::compareTo);
        dto.setUpComingDate(nextDateOpt.map(LocalDate::toString).orElse(null));

        // thumbnailList (각 프로젝트 파일 첫 페이지 URL 최대 4개)
        List<String> thumbnailList = new ArrayList<>();
        for (Project project : projects) {
            List<PresentationFile> files = project.getFiles(); // 여기가 key!
            for (PresentationFile file : files) {
                List<PresentationImage> images = file.getImages();
                if (!images.isEmpty()) {
                    thumbnailList.add(images.get(0).getUrl()); // 첫 페이지 URL
                } else {
                    thumbnailList.add(null);
                }
                if (thumbnailList.size() >= 4) break; // 최대 4개
            }
            if (thumbnailList.size() >= 4) break;
        }

        // 4개 미만이면 null 채우기
        while (thumbnailList.size() < 4) thumbnailList.add(null);
        dto.setThumbnailList(thumbnailList);


        return dto;
    }

    // 워크스페이스 리스트로 전부 받기
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
                    dto.setWorkspaceId(ws.getWorkspaceId());
                    dto.setWorkspaceName(ws.getWorkspaceName());
                    dto.setWorkspaceOwnerName(ws.getOwnerUserId().getUsername());
                    dto.setWorkspaceOwnerProfileUrl(ws.getOwnerUserId().getProfileImageUrl());


                    List<String> timeList = new ArrayList<>();
                    if (ws.getClasstime1() != null) timeList.add(ws.getClasstime1());
                    if (ws.getClasstime2() != null) timeList.add(ws.getClasstime2());
                    if (ws.getClasstime3() != null) timeList.add(ws.getClasstime3());
                    dto.setWorkspaceTimeList(timeList);

                    dto.setIsOwner(ws.getOwnerUserId().getId().equals(user.getId()));

                    VisitLog lastVisit = visitLogRepository.findTopByUserAndWorkspaceOrderByVisitedAtDesc(user, ws).orElse(null);
                    dto.setLastVisited(lastVisit != null ? lastVisit.getVisitedAt().toString() : null);

                    // 팀 멤버 리스트
                    List<TeamMember> teamMembers = teamMemberRepository.findByWorkspace_WorkspaceId(ws.getWorkspaceId());
                    List<WorkspaceMemberDTO> members = teamMembers.stream()
                            .map(member -> new WorkspaceMemberDTO(
                                    member.getMemberId(),
                                    member.getUser().getId(),
                                    member.getUser().getEmail(),
                                    member.getUser().getUsername(),
                                    member.getUser().getProfileImageUrl()))
                            .collect(Collectors.toList());
                    dto.setWorkspaceMemberList(members);

                    // 가장 가까운 발표 날짜
                    List<Project> projects = projectRepository.findByWorkspaceId_WorkspaceId(ws.getWorkspaceId());
                    Optional<LocalDate> nextDateOpt = projects.stream()
                            .map(Project::getDueDate)
                            .filter(Objects::nonNull)
                            .filter(date -> date.isAfter(LocalDate.now()))
                            .min(LocalDate::compareTo);
                    dto.setUpComingDate(nextDateOpt.map(LocalDate::toString).orElse(null));

                    // 썸네일 리스트 (최대 4개)
                    List<String> thumbnailList = new ArrayList<>();
                    for (Project project : projects) {
                        List<PresentationFile> files = project.getFiles();
                        if (files != null && !files.isEmpty()) {
                            // 첫 번째 파일 기준
                            Long fileId = files.get(0).getFileId();
                            thumbnailList.add("https://d53mjm0l7jtco.cloudfront.net/" + fileId + "/page/1/image");
                        } else {
                            thumbnailList.add(null);
                        }

                        if (thumbnailList.size() >= 4) break; // 최대 4개
                    }

                    // 4개 미만이면 null 채우기
                    while (thumbnailList.size() < 4) thumbnailList.add(null);

                    dto.setThumbnailList(thumbnailList);

                    return dto;
                })
                .collect(Collectors.toList());
    }


    // 워크스페이스 즐겨찾기
    @Transactional
    public Map<String, Object> toggleWorkspaceBookmark(Long workspaceId, User user, boolean status) {
        WorkSpace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("해당 워크스페이스가 존재하지 않습니다."));

        boolean isMember = teamMemberRepository.existsByWorkspace_WorkspaceIdAndUser_Id(workspaceId, user.getId());
        if (!isMember) {
            throw new RuntimeException("해당 워크스페이스 멤버가 아니므로 접근 권한이 없습니다.");
        }

        if (status) {
            // 즐겨찾기 등록
            if (!workspaceBookmarkRepository.existsByUserAndWorkspace(user, workspace)) {
                WorkspaceBookmark bookmark = WorkspaceBookmark.builder()
                        .user(user)
                        .workspace(workspace)
                        .build();
                workspaceBookmarkRepository.save(bookmark);
            }
        } else {
            // 즐겨찾기 취소
            workspaceBookmarkRepository.deleteByUserAndWorkspace(user, workspace);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("workspaceId", workspaceId);
        response.put("status", status ? "즐겨찾기 등록 완료" : "즐겨찾기 해제 완료");
        return response;
    }

    // 즐겨찾기 목록 불러오기
    @Transactional(readOnly = true)
    public List<WorkspaceBookmarkDTO> getBookmarkedWorkspaces(Long userId) {

        // userId로 즐겨찾기된 워크스페이스 조회
        List<WorkspaceBookmark> bookmarks = workspaceBookmarkRepository.findByUserId(userId);

        return bookmarks.stream().map(b -> {
            WorkSpace ws = b.getWorkspace();

            List<String> times = new ArrayList<>();
            if (ws.getClasstime1() != null) times.add(ws.getClasstime1());
            if (ws.getClasstime2() != null) times.add(ws.getClasstime2());
            if (ws.getClasstime3() != null) times.add(ws.getClasstime3());

            return new WorkspaceBookmarkDTO(
                    ws.getWorkspaceId(),
                    ws.getWorkspaceName(),
                    times
            );
        }).collect(Collectors.toList());
    }

    // 워크스페이스 검색
    @Transactional(readOnly = true)
    public List<WorkspaceInfoDTO> searchWorkspaces(String keyword, User user) {

        List<WorkSpace> workspaces = workspaceRepository.findByWorkspaceNameContainingIgnoreCase(keyword);

        return workspaces.stream().map(ws -> {
            WorkspaceInfoDTO dto = new WorkspaceInfoDTO();
            dto.setWorkspaceId(ws.getWorkspaceId());
            dto.setWorkspaceName(ws.getWorkspaceName());
            dto.setWorkspaceOwnerName(ws.getOwnerUserId().getUsername());
            dto.setWorkspaceOwnerProfileUrl(ws.getOwnerUserId().getProfileImageUrl());
            dto.setIsOwner(ws.getOwnerUserId().getId().equals(user.getId()));

            // 방문 로그 (최근 방문 시간)
            VisitLog lastVisit = visitLogRepository
                    .findTopByUserAndWorkspaceOrderByVisitedAtDesc(user, ws)
                    .orElse(null);
            dto.setLastVisited(lastVisit != null ? lastVisit.getVisitedAt().toString() : null);

            // 수업 시간 리스트
            List<String> timeList = new ArrayList<>();
            if (ws.getClasstime1() != null) timeList.add(ws.getClasstime1());
            if (ws.getClasstime2() != null) timeList.add(ws.getClasstime2());
            if (ws.getClasstime3() != null) timeList.add(ws.getClasstime3());
            dto.setWorkspaceTimeList(timeList);

            // 팀 멤버 리스트
            List<TeamMember> members = teamMemberRepository.findByWorkspace_WorkspaceId(ws.getWorkspaceId());
            dto.setWorkspaceMemberList(
                    members.stream()
                            .map(member -> new WorkspaceMemberDTO(
                                    member.getMemberId(),
                                    member.getUser().getId(),
                                    member.getUser().getEmail(),
                                    member.getUser().getUsername(),
                                    member.getUser().getProfileImageUrl()
                            ))
                            .collect(Collectors.toList())
            );

            // 가장 가까운 발표 날짜
            List<Project> projects = projectRepository.findByWorkspaceId_WorkspaceId(ws.getWorkspaceId());
            Optional<LocalDate> nextDateOpt = projects.stream()
                    .map(Project::getDueDate)
                    .filter(Objects::nonNull)
                    .filter(date -> date.isAfter(LocalDate.now()))
                    .min(LocalDate::compareTo);
            dto.setUpComingDate(nextDateOpt.map(LocalDate::toString).orElse(null));

            // 썸네일 리스트 (최대 4개)
            List<String> thumbnailList = new ArrayList<>();
            for (Project project : projects) {
                List<PresentationFile> files = project.getFiles();
                if (files != null && !files.isEmpty()) {
                    // 첫 번째 파일 기준
                    Long fileId = files.get(0).getFileId();
                    thumbnailList.add("https://d53mjm0l7jtco.cloudfront.net/" + fileId + "/page/1/image");
                } else {
                    thumbnailList.add(null);
                }

                if (thumbnailList.size() >= 4) break; // 최대 4개
            }

            // 4개 미만이면 null 채우기
            while (thumbnailList.size() < 4) thumbnailList.add(null);

            dto.setThumbnailList(thumbnailList);

            return dto;
        }).collect(Collectors.toList());
    }

    // 시연용 워크스페이스 리스트 반환 (tmp DTO)
    public List<tmpWorkspaceListDTO> getWorkspaceListTmp(User user, int type) {

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
                    tmpWorkspaceListDTO dto = new tmpWorkspaceListDTO();
                    dto.setWorkspaceId(ws.getWorkspaceId());
                    dto.setWorkspaceName(ws.getWorkspaceName());
                    dto.setWorkspaceOwnerName(ws.getOwnerUserId().getUsername());
                    dto.setWorkspaceOwnerProfileUrl(ws.getOwnerUserId().getProfileImageUrl());
                    dto.setWorkspaceOwnerId(ws.getOwnerUserId().getId());
                    dto.setIsOwner(ws.getOwnerUserId().getId().equals(user.getId()));

                    // 수업 시간 리스트
                    List<String> timeList = new ArrayList<>();
                    if (ws.getClasstime1() != null) timeList.add(ws.getClasstime1());
                    if (ws.getClasstime2() != null) timeList.add(ws.getClasstime2());
                    if (ws.getClasstime3() != null) timeList.add(ws.getClasstime3());
                    dto.setWorkspaceTimeList(timeList);

                    // 팀 멤버 리스트
                    List<TeamMember> teamMembers = teamMemberRepository.findByWorkspace_WorkspaceId(ws.getWorkspaceId());
                    List<WorkspaceMemberDTO> members = teamMembers.stream()
                            .map(member -> new WorkspaceMemberDTO(
                                    member.getMemberId(),
                                    member.getUser().getId(),
                                    member.getUser().getEmail(),
                                    member.getUser().getUsername(),
                                    member.getUser().getProfileImageUrl()))
                            .collect(Collectors.toList());
                    dto.setWorkspaceMemberList(members);

                    return dto;
                })
                .collect(Collectors.toList());
    }


}
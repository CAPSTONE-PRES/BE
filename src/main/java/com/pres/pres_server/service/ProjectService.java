package com.pres.pres_server.service;

import com.pres.pres_server.domain.*;
import com.pres.pres_server.dto.Projects.*;
import com.pres.pres_server.repository.*;
import com.pres.pres_server.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {
        private final ProjectRepository projectRepository;
        private final TeamMemberRepository teamMemberRepository;
        private final VisitLogRepository visitLogRepository;
        private final WorkspaceRepository workspaceRepository;
        private final UserRepository userRepository;
        private final VisitLogService visitLogService;
        private final PresentationFileRepository presentationFileRepository;

        public List<ProjectCalenderListDTO> getProjectsByUserId(Long userId) {
                // 1. 사용자가 속한 workspace 조회
                List<TeamMember> members = teamMemberRepository.findByUser_Id(userId);
                List<Long> workspaceIds = members.stream()
                                .map(tm -> tm.getWorkspace().getWorkspaceId())
                                .toList();

                // 2. workspace에 속한 프로젝트 조회
                List<Project> projects = projectRepository
                                .findByWorkspaceId_WorkspaceIdInOrderByDueDateAsc(workspaceIds);

                // 3. DTO 변환
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                return projects.stream()
                                .map(p -> new ProjectCalenderListDTO(
                                                p.getDueDate() != null ? p.getDueDate().format(formatter) : "",
                                                p.getProjectId(),
                                                p.getTitle(),
                                                p.getWorkspaceId().getWorkspaceName()))
                                .toList();
        }

        public List<ProjectCalenderListDTO> getProjectsByUserIdAndDate(Long userId, LocalDate targetDate) {
                // 1. user가 속한 workspace 조회
                List<TeamMember> members = teamMemberRepository.findByUser_Id(userId);
                List<Long> workspaceIds = members.stream()
                                .map(tm -> tm.getWorkspace().getWorkspaceId())
                                .toList();

                // 2. workspace에 속한 프로젝트 조회
                List<Project> projects = projectRepository
                                .findByWorkspaceId_WorkspaceIdInOrderByDueDateAsc(workspaceIds);

                // 3. targetDate 기준 필터링
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                return projects.stream()
                                .filter(p -> p.getDueDate() != null && p.getDueDate().equals(targetDate))
                                .map(p -> new ProjectCalenderListDTO(
                                                p.getDueDate().format(formatter),
                                                p.getProjectId(),
                                                p.getTitle(),
                                                p.getWorkspaceId().getWorkspaceName()))
                                .toList();
        }


        public List<ProjectSearchListDTO> searchProjectsByTitle(Long userId, String title) {
                // workspace 조회
                List<TeamMember> members = teamMemberRepository.findByUser_Id(userId);
                List<Long> workspaceIds = members.stream()
                        .map(tm -> tm.getWorkspace().getWorkspaceId())
                        .toList();

                // workspace에 속한 프로젝트 조회
                List<Project> projects = projectRepository
                        .findByWorkspaceId_WorkspaceIdInOrderByDueDateAsc(workspaceIds);

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                return projects.stream()
                        .filter(p -> p.getTitle().toLowerCase().contains(title.toLowerCase()))
                        .map(p -> {
                                String thumbnailUrl = p.getFiles() != null && !p.getFiles().isEmpty()
                                        ? p.getFiles().get(0).getFileUrl()  // 첫 번째 파일 URL
                                        : null;
                                return new ProjectSearchListDTO(
                                        p.getDueDate() != null ? p.getDueDate().format(formatter) : "",
                                        p.getProjectId(),
                                        p.getTitle(),
                                        p.getWorkspaceId().getWorkspaceName(),
                                        thumbnailUrl
                                );
                        })
                        .toList();
        }


        /*public List<ProjectCalenderListDTO> getProjectList(User user, int type) {

                List<Project> projects;

                if (type == 2) {
                        // 제목순 정렬
                        projects = projectRepository.findAllByOrderByTitleAsc();
                } else if (type == 1) {
                        // 최근 방문순
                        List<VisitLog> logs = visitLogRepository.findByUserAndProjectIsNotNullOrderByVisitedAtDesc(user);
                        projects = logs.stream()
                                .map(VisitLog::getProject)
                                .distinct()
                                .collect(Collectors.toList());

                        List<Project> allProjects = projectRepository.findAll();
                        allProjects.removeAll(projects);
                        projects.addAll(allProjects);
                } else {
                        throw new IllegalArgumentException("Invalid type: " + type);
                }

                return projects.stream()
                        .map(project -> new ProjectCalenderListDTO(
                                project.getCreatedAt() != null ? project.getCreatedAt().toLocalDate().toString() : "",
                                project.getProjectId(),
                                project.getTitle(),
                                project.getWorkspaceId().getWorkspaceName()
                        ))
                        .collect(Collectors.toList());
        }*/
        public List<ProjectListDTO> getProjectList(User user, int type) {

                List<Project> projects;

                if (type == 2) {
                        projects = projectRepository.findAllByOrderByTitleAsc();
                } else if (type == 1) {
                        List<VisitLog> logs = visitLogRepository.findByUserAndProjectIsNotNullOrderByVisitedAtDesc(user);
                        projects = logs.stream()
                                .map(VisitLog::getProject)
                                .distinct()
                                .collect(Collectors.toList());

                        List<Project> allProjects = projectRepository.findAll();
                        allProjects.removeAll(projects);
                        projects.addAll(allProjects);
                } else {
                        throw new IllegalArgumentException("Invalid type: " + type);
                }

                return projects.stream()
                        .map(project -> {
                                // 마지막 방문 로그 조회
                                VisitLog lastVisit = visitLogRepository
                                        .findTopByUserAndProjectOrderByVisitedAtDesc(user, project)
                                        .orElse(null);

                                ProjectListDTO dto = ProjectListDTO.from(project);
                                dto.setLastVisited(lastVisit != null ? lastVisit.getVisitedAt().toString() : null);

                                return dto;
                        })
                        .collect(Collectors.toList());
        }


        // 프로젝트 생성 서비스
        @Transactional
        public Project createProject(Long workspaceId, ProjectCreateRequest request, User currentUser) {
                WorkSpace workspace = workspaceRepository.findById(workspaceId)
                        .orElseThrow(() -> new RuntimeException("워크스페이스가 존재하지 않습니다."));

                Project project = new Project();
                project.setWorkspaceId(workspace);
                project.setTitle(request.getTitle());
                project.setCreatedAt(LocalDateTime.now());
                project.setDueDate(request.getDueDate());

                // 발표자 처리
                if (request.getPresenterId() != null) {
                        User presenter = userRepository.findById(request.getPresenterId())
                                .orElseThrow(() -> new RuntimeException("Presenter가 존재하지 않습니다."));
                        project.setPresenter(presenter);
                } else {
                        project.setPresenter(currentUser);
                }

                // 발표 제한 시간 처리
                if (request.getLimitedTime() != null) {
                        LimitedTimeDTO lt = request.getLimitedTime();
                        project.setLimitedTime(Duration.ofMinutes(lt.getMinute()).plusSeconds(lt.getSecond()));
                }

                return projectRepository.save(project);
        }

        // 프로젝트 수정 서비스
        @Transactional
        public void updateProject(Long projectId, ProjectUpdateRequest request, User user) {
                Project project = projectRepository.findById(projectId)
                        .orElseThrow(() -> new IllegalArgumentException("프로젝트가 존재하지 않습니다."));

                // 권한 체크: 프로젝트 워크스페이스의 소유자 혹은 발표자만 수정 가능
                if (!project.getWorkspaceId().getOwnerUserId().getId().equals(user.getId())
                        && !project.getPresenter().getId().equals(user.getId())) {
                        throw new RuntimeException("권한이 없습니다.");
                }

                // 수정 가능한 항목만 업데이트
                if (request.getTitle() != null) project.setTitle(request.getTitle());
                if (request.getDueDate() != null) project.setDueDate(request.getDueDate()); // LocalDate
                if (request.getLimitedTime() != null) project.setLimitedTime(request.getLimitedTime());
                if (request.getPresenterId() != null) {
                        User newPresenter = userRepository.findById(request.getPresenterId())
                                .orElseThrow(() -> new IllegalArgumentException("발표자가 존재하지 않습니다."));
                        project.setPresenter(newPresenter);
                }

                projectRepository.save(project);
        }

        // 프로젝트 삭제 서비스
//        @Transactional
//        public void deleteProject(Long projectId, User user) {
//                Project project = projectRepository.findById(projectId)
//                        .orElseThrow(() -> new IllegalArgumentException("프로젝트가 존재하지 않습니다."));
//
//                // 권한 체크: 프로젝트 워크스페이스의 소유자 혹은 발표자만 삭제 가능
//                if (!project.getWorkspaceId().getOwnerUserId().getId().equals(user.getId())
//                        && !project.getPresenter().getId().equals(user.getId())) {
//                        throw new RuntimeException("권한이 없습니다.");
//                }
//
//                projectRepository.delete(project);
//        }
        @Transactional
        public void deleteProject(Long projectId, User user) {
                Project project = projectRepository.findById(projectId)
                        .orElseThrow(() -> new IllegalArgumentException("프로젝트가 존재하지 않습니다."));

                // 삭제 권한 : 워크스페이스의 소유자 또는 발표자만 삭제 가능
                boolean isOwner = project.getWorkspaceId().getOwnerUserId().getId().equals(user.getId());
                boolean isPresenter = project.getPresenter() != null
                        && project.getPresenter().getId().equals(user.getId());

                if (!isOwner && !isPresenter) {
                        throw new RuntimeException("권한이 없습니다.");
                }

                // VisitLog 수동 삭제
                visitLogRepository.deleteByProject(project);

                // 프로젝트 삭제
                projectRepository.delete(project);
        }


        // 특정 프로젝트 정보 반환
        public ProjectInfoDTO getProjectInfo(User user, Long projectId) {
                Project project = projectRepository.findById(projectId)
                        .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));

                // 방문 로그 upsert
                visitLogService.upsertVisitLog(user, project);

                WorkSpace workspace = project.getWorkspaceId();

                return ProjectInfoDTO.builder()
                        .projectId(project.getProjectId())
                        .projectTitle(project.getTitle())
                        .workspaceId(workspace != null ? workspace.getWorkspaceId() : null)
                        .workspaceName(workspace != null ? workspace.getWorkspaceName() : null)
                        .dueDate(project.getDueDate())
                        .limitedTime(project.getLimitedTime())
                        .fileIds(project.getFiles() != null
                                ? project.getFiles().stream()
                                .map(PresentationFile::getFileId)
                                .collect(Collectors.toList())
                                : null)
                        .build();
        }

        // 프로젝트 정렬 3가지
        @Transactional(readOnly = true)
        public List<Project> getProjectsByWorkspace(Long workspaceId, int type, User user) {
                WorkSpace workspace = workspaceRepository.findById(workspaceId)
                        .orElseThrow(() -> new IllegalArgumentException("워크스페이스가 존재하지 않습니다."));

                List<Project> projects;

                // 정렬
                switch (type) {
                        case 1: // 최근 방문순
                                projects = visitLogRepository.findByWorkspaceAndUserOrderByVisitedAtDesc(workspace, user)
                                        .stream()
                                        .map(VisitLog::getProject)
                                        .filter(p -> p != null) // null(워크스페이스만 방문) 제거
                                        .distinct()             // 같은 프로젝트 여러 번 방문했을 수 있으니 중복 제거
                                        .toList();
                                break;

                        case 3: // 발표일자순 (dueDate 오름차순)
                                projects = projectRepository.findByWorkspaceId_WorkspaceId(workspaceId)
                                        .stream()
                                        .sorted(Comparator.comparing(Project::getDueDate))
                                        .toList();
                                break;

                        case 2: // 제목순 (String 오름차순)
                                projects = projectRepository.findByWorkspaceId_WorkspaceId(workspaceId)
                                        .stream()
                                        .sorted(Comparator.comparing(Project::getTitle, String.CASE_INSENSITIVE_ORDER))
                                        .toList();
                                break;

                        default:
                                throw new IllegalArgumentException("유효하지 않은 type 값입니다. (1: 방문순, 2: 발표일자, 3: 제목)");
                }

                return projects;
        }

}
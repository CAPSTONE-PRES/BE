
package com.pres.pres_server.controller;

import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.domain.VisitLog;
import com.pres.pres_server.dto.Projects.*;
import com.pres.pres_server.repository.ProjectRepository;
import com.pres.pres_server.repository.VisitLogRepository;
import com.pres.pres_server.service.ProjectService;
import com.pres.pres_server.service.user.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Projects Controller", description = "프로젝트 관련 API")
@RestController
//@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectsController {
    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final VisitLogRepository visitLogRepository;

    @Operation(summary = "프로젝트 생성", description = "워크스페이스에 새로운 프로젝트 추가")
    @PostMapping("/workspace/{workspaceId}/projects/create")
    public ResponseEntity<Map<String, Object>> createProject(
            @PathVariable Long workspaceId,
            @RequestBody ProjectCreateRequest request,
            @AuthenticationPrincipal User user) {

        Project project = projectService.createProject(workspaceId, request, user);

        Map<String, Object> response = new HashMap<>();
        response.put("projectId", project.getProjectId());
        response.put("message", "프로젝트가 성공적으로 생성되었습니다.");

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "프로젝트 수정", description = "프로젝트 정보를 수정합니다")
    @PatchMapping("/projects/{projectId}/update")
    public ResponseEntity<String> updateProject(
            @PathVariable Long projectId,
            @RequestBody ProjectUpdateRequest request,
            @AuthenticationPrincipal User user) {

        projectService.updateProject(projectId, request, user);
        return ResponseEntity.ok("프로젝트 정보가 성공적으로 수정되었습니다.");
    }

    @Operation(summary = "프로젝트 삭제", description = "특정 프로젝트를 삭제합니다")
    @DeleteMapping("/projects/{projectId}/delete")
    public ResponseEntity<String> deleteProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User user) {

        projectService.deleteProject(projectId, user);
        return ResponseEntity.ok("프로젝트가 성공적으로 삭제되었습니다.");
    }

    @Operation(summary = "특정 프로젝트 정보 반환", description = "해당 프로젝트에 대한 정보를 반환")
    @GetMapping("/{projectId}/info")
    public ProjectInfoDTO getProjectInfo(@AuthenticationPrincipal User user, @PathVariable Long projectId) {
        return projectService.getProjectInfo(user, projectId);
    }

    @Operation(summary = "프로젝트 전체 리스트 반환", description = "달력에 표기할 프로젝트 리스트 반환")
    @GetMapping("/projects/list/all")
    public List<ProjectCalenderListDTO> getMyProjects(
            @AuthenticationPrincipal User user) {

        return projectService.getProjectsByUserId(user.getId());
    }

    @GetMapping("/projects/next")
    @Operation(summary = "가장 가까운 발표 하나 반환", description = "현재 시간 이후의 가장 가까운 발표 반환")
    public ResponseEntity<ProjectCalenderDdayListDTO> getNextProject(@AuthenticationPrincipal User user) {
        ProjectCalenderDdayListDTO dto = projectService.getNextProject(user.getId());

        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "예정된 발표가 없습니다.");
        }

        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "특정 날짜에 해당하는 프로젝트 반환", description = "특정 날짜에 해당하는 프로젝트 정보를 반환, 달력에 사용")
    @GetMapping("/projects/list/date")
    public List<ProjectCalenderListDTO> getMyProjectsByDate(
            @AuthenticationPrincipal User user,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return projectService.getProjectsByUserIdAndDate(user.getId(), date);
    }

    /*@Operation(summary = "프로젝트 좋아요 상태 저장 및 취소", description = "status 값 true : 좋아요, false : 좋아요 취소")
    @PatchMapping("/projects/{projectId}/heart")
    public ProjectCalenderListDTO toggleBookmark(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable Long projectId,
            @RequestParam("status") boolean status) {

        User realUser = userService.findByEmail(principal.getUsername());
        return projectService.toggleBookmark(realUser.getId(), projectId, status);
    }

    @Operation(summary = "좋아요 표시한 프로젝트 리스트 반환", description = "해당 유저가 좋아요 표기한 리스트를 반환합니다.")
    @GetMapping("/projects/heartlist")
    public List<ProjectCalenderListDTO> getBookmarkedProjects(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        User realUser = userService.findByEmail(principal.getUsername());
        return projectService.getBookmarkedProjects(realUser.getId());
    }*/

    @Operation(summary = "프로젝트 검색", description = "이름에 해당 키워드를 갖고 있는 프로젝트를 반환합니다.")
    @GetMapping("/projects/search")
    public List<ProjectSearchListDTO> searchProjects(
            @AuthenticationPrincipal User user,
            @RequestParam("title") String title) {
        return projectService.searchProjectsByTitle(user.getId(), title);
    }

    @Operation(summary = "프로젝트 리스트 필터링", description = "모든 프로젝트 불러오기 (type값 1은 최근 방문 순, 2는 제목순)")
    @GetMapping("/projects/list")
    public List<ProjectListDTO> getProjectList(
            @RequestParam int type,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        return projectService.getProjectList(user, type);
    }

    // 임시 시연용 api
    @Operation(summary = "시연용 - 프로젝트 리스트 반환", description = "모든 프로젝트 불러오기 (type값 1은 최근 방문 순, 2는 제목순)")
    @GetMapping("/list/tmp")
    public List<tmpProjectListDTO> getTmpProjectList(
            @RequestParam int type,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

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
                    VisitLog lastVisit = visitLogRepository
                            .findTopByUserAndProjectOrderByVisitedAtDesc(user, project)
                            .orElse(null);

                    return tmpProjectListDTO.builder()
                            .projectId(project.getProjectId())
                            .projectTitle(project.getTitle())
                            .workspaceId(project.getWorkspaceId().getWorkspaceId())
                            .workspaceName(project.getWorkspaceId().getWorkspaceName())
                            .date(project.getDueDate())
                            .presenterName(project.getPresenter() != null ? project.getPresenter().getUsername() : null)
                            .presenterProfileUrl(project.getPresenter() != null ? project.getPresenter().getProfileImageUrl() : null)
                            .lastVisited(lastVisit != null ? lastVisit.getVisitedAt().toLocalDate().toString() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }
}

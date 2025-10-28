
package com.pres.pres_server.controller;

import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.Projects.*;
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

@Tag(name = "Projects Controller", description = "프로젝트 관련 API")
@RestController
//@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectsController {
    private final ProjectService projectService;

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

    @GetMapping("/{projectId}/info")
    public ProjectInfoDTO getProjectInfo(@PathVariable Long projectId) {
        return projectService.getProjectInfo(projectId);
    }

    @Operation(summary = "프로젝트 전체 리스트 반환", description = "달력에 표기할 프로젝트 리스트 반환")
    @GetMapping("/projects/list/all")
    public List<ProjectCalenderListDTO> getMyProjects(
            @AuthenticationPrincipal User user) {

        return projectService.getProjectsByUserId(user.getId());
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
    public List<ProjectCalenderListDTO> searchProjects(
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
    @Operation(summary = "시연용 - 프로젝트 리스트 반환")
    @GetMapping("/list/tmp")
    public ResponseEntity<List<tmpProjectListDTO>> getTmpProjectList() {

        List<tmpProjectListDTO> tmpList = new ArrayList<>();

        tmpList.add(tmpProjectListDTO.builder()
                .projectId(0L)
                .projectTitle("string")
                .workspaceId(0L)
                .date(LocalDate.now())
                .workspaceName("string")
                .presenterName("string")
                .presenterProfileUrl("string")
                .lastVisited("string")
                .build());

        return ResponseEntity.ok(tmpList);
    }
}

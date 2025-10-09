
package com.pres.pres_server.controller;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.Projects.ProjectListDTO;
import com.pres.pres_server.service.ProjectService;
import com.pres.pres_server.service.user.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Projects Controller", description = "프로젝트 관련 API")
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectsController {
    private final ProjectService projectService;
    private final UserService userService;

    @Operation(summary = "프로젝트 전체 리스트 반환", description = "달력에 표기할 프로젝트 리스트 반환")
    @GetMapping("/list/all")
    public List<ProjectListDTO> getMyProjects(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        User realUser = userService.findByEmail(principal.getUsername());
        return projectService.getProjectsByUserId(realUser.getId());
    }

    @Operation(summary = "특정 날짜에 해당하는 프로젝트 반환", description = "특정 날짜에 해당하는 프로젝트 정보를 반환, 달력에 사용")
    @GetMapping("/list/date")
    public List<ProjectListDTO> getMyProjectsByDate(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        User realUser = userService.findByEmail(principal.getUsername());
        return projectService.getProjectsByUserIdAndDate(realUser.getId(), date);
    }

    @Operation(summary = "프로젝트 좋아요 상태 저장 및 취소", description = "status 값 true : 좋아요, false : 좋아요 취소")
    @PatchMapping("/{projectId}/heart")
    public ProjectListDTO toggleBookmark(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable Long projectId,
            @RequestParam("status") boolean status) {

        User realUser = userService.findByEmail(principal.getUsername());
        return projectService.toggleBookmark(realUser.getId(), projectId, status);
    }

    @Operation(summary = "좋아요 표시한 프로젝트 리스트 반환", description = "해당 유저가 좋아요 표기한 리스트를 반환합니다.")
    @GetMapping("/heartlist")
    public List<ProjectListDTO> getBookmarkedProjects(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        User realUser = userService.findByEmail(principal.getUsername());
        return projectService.getBookmarkedProjects(realUser.getId());
    }

    @Operation(summary = "프로젝트 검색", description = "이름에 해당 키워드를 갖고 있는 프로젝트를 반환합니다.")
    @GetMapping("/search")
    public List<ProjectListDTO> searchProjects(
            @AuthenticationPrincipal User user,
            @RequestParam("title") String title) {
        return projectService.searchProjectsByTitle(user.getId(), title);
    }

    @Operation(summary = "프로젝트 리스트 필터링", description = "모든 프로젝트 불러오기 (type값 1은 최근 방문 순, 2는 제목순)")
    @GetMapping("/list")
    public List<ProjectListDTO> getProjectList(
            @RequestParam int type,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        return projectService.getProjectList(user, type);
    }
}

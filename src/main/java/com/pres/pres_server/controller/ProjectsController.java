
package com.pres.pres_server.controller;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.Projects.ProjectListDTO;
import com.pres.pres_server.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectsController {
    private final ProjectService projectService;

    @GetMapping("/list")
    public List<ProjectListDTO> getMyProjects(@AuthenticationPrincipal User user) {
        return projectService.getProjectsByUserId(user.getId());
    }

    @GetMapping("/list/date")
    public List<ProjectListDTO> getMyProjectsByDate(
            @AuthenticationPrincipal User user,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return projectService.getProjectsByUserIdAndDate(user.getId(), date);
    }

    @PatchMapping("/{projectId}/heart")
    public ProjectListDTO toggleBookmark(
            @AuthenticationPrincipal User user,
            @PathVariable Long projectId,
            @RequestParam("status") boolean status) {

        return projectService.toggleBookmark(user.getId(), projectId, status);
    }

    @GetMapping("/heartlist")
    public List<ProjectListDTO> getBookmarkedProjects(@AuthenticationPrincipal User user) {
        return projectService.getBookmarkedProjects(user.getId());
    }

    @GetMapping("/search")
    public List<ProjectListDTO> searchProjects(
            @AuthenticationPrincipal User user,
            @RequestParam("title") String title) {
        return projectService.searchProjectsByTitle(user.getId(), title);
    }
}

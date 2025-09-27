
package com.pres.pres_server.controller;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.Projects.ProjectListDTO;
import com.pres.pres_server.service.ProjectService;
import com.pres.pres_server.service.user.UserService;
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
    private final UserService userService;

    @GetMapping("/list")
    public List<ProjectListDTO> getMyProjects(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        User realUser = userService.findByEmail(principal.getUsername());
        return projectService.getProjectsByUserId(realUser.getId());
    }

    @GetMapping("/list/date")
    public List<ProjectListDTO> getMyProjectsByDate(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        User realUser = userService.findByEmail(principal.getUsername());
        return projectService.getProjectsByUserIdAndDate(realUser.getId(), date);
    }

    @PatchMapping("/{projectId}/heart")
    public ProjectListDTO toggleBookmark(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable Long projectId,
            @RequestParam("status") boolean status) {

        User realUser = userService.findByEmail(principal.getUsername());
        return projectService.toggleBookmark(realUser.getId(), projectId, status);
    }

    @GetMapping("/heartlist")
    public List<ProjectListDTO> getBookmarkedProjects(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        User realUser = userService.findByEmail(principal.getUsername());
        return projectService.getBookmarkedProjects(realUser.getId());
    }

    @GetMapping("/search")
    public List<ProjectListDTO> searchProjects(
            @AuthenticationPrincipal User user,
            @RequestParam("title") String title) {
        return projectService.searchProjectsByTitle(user.getId(), title);
    }
}

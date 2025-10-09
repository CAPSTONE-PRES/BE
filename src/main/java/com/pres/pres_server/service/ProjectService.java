package com.pres.pres_server.service;

import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.TeamMember;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.domain.VisitLog;
import com.pres.pres_server.dto.Projects.ProjectListDTO;
import com.pres.pres_server.repository.ProjectRepository;
import com.pres.pres_server.repository.TeamMemberRepository;
import com.pres.pres_server.repository.VisitLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {
        private final ProjectRepository projectRepository;
        private final TeamMemberRepository teamMemberRepository;
        private final VisitLogRepository visitLogRepository;

        public List<ProjectListDTO> getProjectsByUserId(Long userId) {
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
                                .map(p -> new ProjectListDTO(
                                                p.getDueDate() != null ? p.getDueDate().format(formatter) : "",
                                                p.getTitle(),
                                                p.getWorkspaceId().getWorkspaceName()))
                                .toList();
        }

        public List<ProjectListDTO> getProjectsByUserIdAndDate(Long userId, LocalDate targetDate) {
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
                                .filter(p -> p.getDueDate() != null && p.getDueDate().toLocalDate().equals(targetDate))
                                .map(p -> new ProjectListDTO(
                                                p.getDueDate().format(formatter),
                                                p.getTitle(),
                                                p.getWorkspaceId().getWorkspaceName()))
                                .toList();
        }

        public ProjectListDTO toggleBookmark(Long userId, Long projectId, boolean status) {
                Project project = projectRepository.findById(projectId)
                                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

                // user가 해당 프로젝트 접근 권한 있는지 확인 (TeamMember 확인)
                boolean isMember = teamMemberRepository.findByUser_Id(userId).stream()
                                .anyMatch(tm -> tm.getWorkspace().getWorkspaceId()
                                                .equals(project.getWorkspaceId().getWorkspaceId()));

                if (!isMember) {
                        throw new IllegalArgumentException("User does not have access to this project");
                }

                project.setBookmarked(status);
                projectRepository.save(project);

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                return new ProjectListDTO(
                                project.getDueDate() != null ? project.getDueDate().format(formatter) : "",
                                project.getTitle(),
                                project.getWorkspaceId().getWorkspaceName());
        }

        public List<ProjectListDTO> getBookmarkedProjects(Long userId) {
                // 1. 사용자가 속한 workspace 조회
                List<TeamMember> members = teamMemberRepository.findByUser_Id(userId);
                List<Long> workspaceIds = members.stream()
                                .map(tm -> tm.getWorkspace().getWorkspaceId())
                                .toList();

                // 2. workspace에 속한 프로젝트 조회 + isBookmarked = true 필터
                List<Project> projects = projectRepository
                                .findByWorkspaceId_WorkspaceIdInOrderByDueDateAsc(workspaceIds);

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                return projects.stream()
                                .filter(Project::isBookmarked)
                                .map(p -> new ProjectListDTO(
                                                p.getDueDate() != null ? p.getDueDate().format(formatter) : "",
                                                p.getTitle(),
                                                p.getWorkspaceId().getWorkspaceName()))
                                .toList();
        }

        public List<ProjectListDTO> searchProjectsByTitle(Long userId, String title) {
                // 1. 사용자가 속한 workspace 조회
                List<TeamMember> members = teamMemberRepository.findByUser_Id(userId);
                List<Long> workspaceIds = members.stream()
                                .map(tm -> tm.getWorkspace().getWorkspaceId())
                                .toList();

                // 2. workspace에 속한 프로젝트 조회
                List<Project> projects = projectRepository
                                .findByWorkspaceId_WorkspaceIdInOrderByDueDateAsc(workspaceIds);

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                return projects.stream()
                                .filter(p -> p.getTitle().toLowerCase().contains(title.toLowerCase())) // 제목 포함 검색
                                .map(p -> new ProjectListDTO(
                                                p.getDueDate() != null ? p.getDueDate().format(formatter) : "",
                                                p.getTitle(),
                                                p.getWorkspaceId().getWorkspaceName()))
                                .toList();
        }

        public List<ProjectListDTO> getProjectList(User user, int type) {

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
                        .map(project -> new ProjectListDTO(
                                project.getCreatedAt() != null ? project.getCreatedAt().toLocalDate().toString() : "",
                                project.getTitle(),
                                project.getWorkspaceId().getWorkspaceName()
                        ))
                        .collect(Collectors.toList());
        }



}
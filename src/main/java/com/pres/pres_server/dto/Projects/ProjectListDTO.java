package com.pres.pres_server.dto.Projects;

import com.pres.pres_server.domain.Project;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectListDTO {
    private Long projectId;
    private String title;
    private LocalDate dueDate;
    private String presenterName;
    private String presenterProfileUrl;
    private LocalDate visitedAt;

    public static ProjectListDTO from(Project project) {
        return ProjectListDTO.builder()
                .projectId(project.getProjectId())
                .title(project.getTitle())
                .dueDate(project.getDueDate())
                .presenterName(project.getPresenter() != null ? project.getPresenter().getUsername() : null)
                .presenterProfileUrl(project.getPresenter() != null ? project.getPresenter().getProfileImageUrl() : null)
                // visitedAt은 최근 방문순에서만 의미가 있으므로 별도 매핑 필요 시 VisitLog에서 추가 가능
                .build();
    }
}

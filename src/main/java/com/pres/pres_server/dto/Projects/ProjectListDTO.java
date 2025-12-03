package com.pres.pres_server.dto.Projects;

import com.pres.pres_server.domain.PresentationFile;
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
    private String projectTitle;
    private String projectThumbnail;
    private LocalDate Date;
    private String workspaceName;
    private String presenterName;
    private String presenterProfileUrl;
    private String lastVisited;

    public static ProjectListDTO from(Project project) {
        String thumbnailUrl = null;

        if (project.getFiles() != null && !project.getFiles().isEmpty()) {
            PresentationFile firstFile = project.getFiles().get(0);
            // 규칙 기반 URL 매핑
            thumbnailUrl = "https://15.164.97.26.nip.io/api/files/" + firstFile.getFileId() + "/page/1/image";
        }

        return ProjectListDTO.builder()
                .projectId(project.getProjectId())
                .projectTitle(project.getTitle())
                .projectThumbnail(thumbnailUrl)
                .Date(project.getDueDate())
                .workspaceName(project.getWorkspaceId().getWorkspaceName())
                .presenterName(project.getPresenter() != null ? project.getPresenter().getUsername() : null)
                .presenterProfileUrl(project.getPresenter() != null ? project.getPresenter().getProfileImageUrl() : null)
                .build();
    }
}

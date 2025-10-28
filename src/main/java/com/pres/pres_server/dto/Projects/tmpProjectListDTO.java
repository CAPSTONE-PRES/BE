package com.pres.pres_server.dto.Projects;

import com.pres.pres_server.domain.Project;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class tmpProjectListDTO {
    private Long projectId;
    private String projectTitle;
    private Long workspaceId;
    private LocalDate date;
    private String workspaceName;
    private String presenterName;
    private String presenterProfileUrl;
    private String lastVisited;
}
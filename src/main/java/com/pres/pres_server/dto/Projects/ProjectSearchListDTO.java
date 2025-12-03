package com.pres.pres_server.dto.Projects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSearchListDTO {
    private String date;
    private Long projectId;
    private String projectTitle;
    private String workspaceName;
    private String projectThumbnail;
}
package com.pres.pres_server.dto.Projects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectListDTO {
    private String date;
    private String projectTitle;
    private String workspaceName;
}
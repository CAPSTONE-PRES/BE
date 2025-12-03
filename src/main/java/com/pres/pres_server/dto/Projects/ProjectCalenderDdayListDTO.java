package com.pres.pres_server.dto.Projects;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCalenderDdayListDTO {
    private String date;
    private Long projectId;
    private String projectTitle;
    private String workspaceName;
    private String thumbnail; // 첫 페이지 썸네일
}
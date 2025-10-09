package com.pres.pres_server.dto.Projects;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
public class ProjectCreateRequest {
    private String title;
    private LocalDateTime dueDate;
    private LocalDateTime limitedTime;
    private Long presenterId; // 선택 사항
}
package com.pres.pres_server.dto.Projects;

import lombok.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class ProjectUpdateRequest {
    private String title;
    private LocalDate dueDate;
    private Duration limitedTime;
    private Long presenterId; // 선택
}
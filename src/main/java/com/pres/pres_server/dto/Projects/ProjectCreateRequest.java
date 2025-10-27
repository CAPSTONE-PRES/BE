package com.pres.pres_server.dto.Projects;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pres.pres_server.dto.PresentationFileRequest;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
public class ProjectCreateRequest {
    private String title;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate; // 프로젝트 마감일

    private LimitedTimeDTO limitedTime; // 발표 제한 시간

    private Long presenterId; // 선택
    private List<Long> fileIds; // 선택
}
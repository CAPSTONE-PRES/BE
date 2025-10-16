package com.pres.pres_server.dto.Projects;

import com.pres.pres_server.dto.PresentationFileRequest;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
public class ProjectCreateRequest {
    private String title;
    private LocalDateTime dueDate;
    private LocalDateTime limitedTime;
    private Long presenterId; // 선택 사항
    private List<Long> fileIds; // 기존 파일 ID만
}
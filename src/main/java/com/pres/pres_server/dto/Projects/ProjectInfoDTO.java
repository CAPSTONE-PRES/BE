package com.pres.pres_server.dto.Projects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectInfoDTO {
    private Long projectId;
    private String projectTitle;
    private Long workspaceId;
    private String workspaceName;
    private LocalDate dueDate;

    @JsonIgnore
    private Duration limitedTime;

    private List<Long> fileIds;

    // Duration을 { "minute": x, "second": y } 형태
    public DurationInfo getLimitTime() {
        if (limitedTime == null) return null;
        return new DurationInfo(limitedTime.toMinutesPart(), limitedTime.toSecondsPart());
    }

    @Getter
    @AllArgsConstructor
    public static class DurationInfo {
        private int minute;
        private int second;
    }
}


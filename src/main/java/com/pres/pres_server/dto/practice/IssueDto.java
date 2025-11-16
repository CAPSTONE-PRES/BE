package com.pres.pres_server.dto.practice;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
@Data
@Builder
public class IssueDto {
    private String issueType;
    private Integer fillerCount;
    private Map<String, Integer> fillerDetail;
    private Integer repeatCount;
    private Map<String, Integer> repeatDetail;
    private Integer errorCount;
    private Integer spmUser;
    private Integer spmAverage;
    private String comment;

}

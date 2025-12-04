package com.pres.pres_server.dto.practice;

import java.util.Map;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// null이면 json에 표시되지 않음
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class IssueDto {
    private String issueType;
    private Integer fillerCount;
    private Map<String, Integer> fillerDetail;
    private Integer silenceCount;
    private Integer repeatCount;
    private Map<String, Integer> repeatDetail;
    private Integer errorCount;
    private Integer spmUser;
    private String comment;
    // 하이라이트용 오프셋 리스트 (begin/end: 슬라이드 텍스트 기준 인덱스)
    private List<OffsetDto> offsets;
    // 정확도 유사도 값 (0.0 - 1.0). null이면 제공되지 않음
    private Double similarity;

}

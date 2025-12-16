package com.pres.pres_server.dto.practice;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 슬라이드별 피드백 DTO (메타 정보 + 이슈 리스트)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlideFeedbackDto {

    private Integer slideNumber; // 슬라이드 번호 (프론트 제공 라벨, 0-based)
    private Integer visitIndex; // 동일 슬라이드의 방문 순서 (0-based, 재방문 구분용)
    private Double timestampSeconds; // 슬라이드 시작 시각 (초)
    private String slideText; // 해당 슬라이드 STT 텍스트

    // 여러 이슈(배열) - issueType 및 관련 데이터는 IssueDto에 포함
    private List<IssueDto> issues;

    // 썸네일 URL (프론트가 바로 불러올 수 있는 이미지 URL)
    private String thumbnailUrl;
    // 필요시 base64 인라인 이미지(크기가 작을 때만 사용 권장)
    private String thumbnailBase64;
}

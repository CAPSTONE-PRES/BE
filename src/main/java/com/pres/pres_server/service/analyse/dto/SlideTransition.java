package com.pres.pres_server.service.analyse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

//프론트에서 백으로 전달해주는 SLIDE 전환 정보 DTO
@Getter
@Builder
public class SlideTransition {
    @JsonProperty("slide")
    private final int slideNumber; // 0-based
    private final double startSec; // inclusive, 슬라이드 시작 시점
    private final double endSec; // exclusive, 슬라이드 종료 시점

    /**
     * 하위 호환성을 위한 메서드
     * 기존 코드에서 getTimestamp() 호출을 지원하기 위해 startSec 반환
     */
    public double getTimestamp() {
        return startSec;
    }
}

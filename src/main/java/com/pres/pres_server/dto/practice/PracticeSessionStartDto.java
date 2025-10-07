package com.pres.pres_server.dto.practice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeSessionStartDto {

    private Long sessionId;
    private Long projectId;
    private List<SlideInfo> slides;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlideInfo {

        private Integer pageNumber;
        private String slideText;
        private String imageUrl;
        private String cueCard;
        private String qrUrl;
    }
}

package com.pres.pres_server.dto.file;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PPTX 슬라이드 콘텐츠 정보를 담는 DTO
 */
@Data
@NoArgsConstructor
public class SlideContentInfo {
    private int slideNumber;
    private String extractedText;
    private int textLength;
    private int textShapeCount;
    private int imageCount;
    private int tableCount;
    private int chartCount;
    private boolean isOcrFailed;
    private boolean isImageDominant;
    private boolean isInsufficientText;
    private boolean hasContent;

    @Override
    public String toString() {
        return String.format(
                "Slide %d: textLen=%d, shapes=%d, images=%d, tables=%d, charts=%d, valid=%s",
                slideNumber, textLength, textShapeCount, imageCount, tableCount, chartCount, hasContent);
    }
}
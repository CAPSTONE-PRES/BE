package com.pres.pres_server.dto.file;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PDF 페이지 콘텐츠 정보를 담는 DTO
 */
@Data
@NoArgsConstructor
public class PdfPageInfo {
    private int pageNumber;
    private String extractedText;
    private int textLength;
    private boolean hasContent;

    @Override
    public String toString() {
        return String.format("Page %d: textLen=%d, valid=%s", pageNumber, textLength, hasContent);
    }
}
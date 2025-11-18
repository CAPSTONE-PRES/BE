package com.pres.pres_server.service.analyse;

/**
 * Domain-level text offset used within analysis services.
 * This is NOT the API DTO. API layer will convert domain offsets to
 * `OffsetDto`.
 */
public class TextOffset {
    private final int begin;
    private final int end;
    private final int slideIndex; // 1-based
    private final String text;

    public TextOffset(int begin, int end, int slideIndex, String text) {
        this.begin = begin;
        this.end = end;
        this.slideIndex = slideIndex;
        this.text = text;
    }

    public int getBegin() {
        return begin;
    }

    public int getEnd() {
        return end;
    }

    public int getSlideIndex() {
        return slideIndex;
    }

    public String getText() {
        return text;
    }
}

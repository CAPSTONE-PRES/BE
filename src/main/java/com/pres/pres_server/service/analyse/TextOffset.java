package com.pres.pres_server.service.analyse;

/**
 * Domain-level text offset used within analysis services.
 * This is NOT the API DTO. API layer will convert domain offsets to
 * `OffsetDto`.
 */
public class TextOffset {
    private final int begin;
    private final int end;
    private final int slideIndex; // slideNumber (프론트 제공 라벨, 0-based)
    private final int visitIndex; // 동일 슬라이드의 방문 순서 (0-based)
    private final String text;

    public TextOffset(int begin, int end, int slideIndex, int visitIndex, String text) {
        this.begin = begin;
        this.end = end;
        this.slideIndex = slideIndex;
        this.visitIndex = visitIndex;
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

    public int getVisitIndex() {
        return visitIndex;
    }

    public String getText() {
        return text;
    }
}

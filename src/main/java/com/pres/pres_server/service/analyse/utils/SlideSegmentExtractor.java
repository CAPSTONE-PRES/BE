package com.pres.pres_server.service.analyse.utils;

import com.pres.pres_server.service.analyse.TextAnalysisUtils;
import com.pres.pres_server.service.analyse.dto.SlideTransition;
import com.pres.pres_server.service.analyse.dto.WhisperSegment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import com.pres.pres_server.dto.practice.OffsetDto;
import com.pres.pres_server.service.analyse.RepetitiveTextAnalysisService;

/**
 * 슬라이드 구간 추출 및 segment 분할을 담당하는 유틸리티
 * - 기존 SlideSentenceMapper의 기능을 포함하여 확장
 */
@Component
public class SlideSegmentExtractor {

    /**
     * 슬라이드별 구간 정보
     */
    @Getter
    @AllArgsConstructor
    public static class SlideInterval {
        private int slideNumber; // 실제 슬라이드 번호 (1-based)
        private int slideIndex; // 배열 인덱스 (0-based)
        private double startTime;
        private double endTime;

        public boolean contains(double timestamp) {
            return timestamp >= startTime && timestamp < endTime;
        }

        public boolean containsSegment(WhisperSegment segment) {
            double segMid = (segment.getStart() + segment.getEnd()) / 2.0;
            return contains(segMid);
        }
    }

    /**
     * SlideTransition을 SlideInterval로 변환
     *
     * @param transitions   슬라이드 전환 정보
     * @param totalDuration 전체 오디오 길이 (초)
     * @return 슬라이드 구간 리스트
     */
    public List<SlideInterval> createSlideIntervals(
            List<SlideTransition> transitions,
            double totalDuration) {

        if (transitions == null || transitions.isEmpty()) {
            return Collections.emptyList();
        }

        // timestamp 기준 정렬
        List<SlideTransition> sorted = new ArrayList<>(transitions);
        sorted.sort(Comparator.comparingDouble(SlideTransition::getTimestamp));

        List<SlideInterval> intervals = new ArrayList<>();

        for (int i = 0; i < sorted.size(); i++) {
            SlideTransition current = sorted.get(i);
            double start = current.getTimestamp();
            double end = (i + 1 < sorted.size())
                    ? sorted.get(i + 1).getTimestamp()
                    : totalDuration;

            intervals.add(new SlideInterval(
                    current.getSlideNumber(), // 실제 슬라이드 번호
                    i, // 인덱스
                    start,
                    end));
        }

        return intervals;
    }

    /**
     * 슬라이드별로 WhisperSegment 분할
     *
     * @param segments  전체 Whisper segment 리스트
     * @param intervals 슬라이드 구간 리스트
     * @return 슬라이드별 segment 리스트
     */
    public List<List<WhisperSegment>> splitSegmentsBySlides(
            List<WhisperSegment> segments,
            List<SlideInterval> intervals) {

        if (segments == null || segments.isEmpty() || intervals.isEmpty()) {
            return Collections.emptyList();
        }

        // 슬라이드별 segment를 담을 리스트 초기화
        List<List<WhisperSegment>> result = new ArrayList<>();
        for (int i = 0; i < intervals.size(); i++) {
            result.add(new ArrayList<>());
        }

        // 각 segment를 해당 슬라이드에 할당
        for (WhisperSegment segment : segments) {
            for (int i = 0; i < intervals.size(); i++) {
                if (intervals.get(i).containsSegment(segment)) {
                    result.get(i).add(segment);
                    break;
                }
            }
        }

        return result;
    }

    /**
     * 슬라이드별 STT 텍스트 추출
     *
     * @param segments  전체 Whisper segment 리스트
     * @param intervals 슬라이드 구간 리스트
     * @return 슬라이드별 텍스트 리스트
     */
    public List<String> extractSlideSttTexts(
            List<WhisperSegment> segments,
            List<SlideInterval> intervals) {

        List<List<WhisperSegment>> slideSegments = splitSegmentsBySlides(segments, intervals);

        return slideSegments.stream()
                .map(segs -> segs.stream()
                        .map(WhisperSegment::getText)
                        .filter(text -> text != null && !text.trim().isEmpty())
                        .collect(Collectors.joining(" ")))
                .collect(Collectors.toList());
    }

    /**
     * 슬라이드 텍스트에서 주어진 패턴(또는 단어) 목록의 모든 출현 위치를 찾아 OffsetDto 리스트로 반환
     * slideNumber는 1-based 인덱스로 채움
     */
    public List<OffsetDto> collectOffsetsForSlide(String slideText, List<String> patterns, int slideNumber) {
        List<OffsetDto> out = new ArrayList<>();
        if (slideText == null || slideText.isEmpty() || patterns == null || patterns.isEmpty())
            return out;

        String text = slideText;
        for (String p : patterns) {
            if (p == null || p.isBlank())
                continue;
            int idx = text.indexOf(p);
            while (idx >= 0) {
                int end = Math.min(text.length(), idx + p.length());
                out.add(OffsetDto.builder().begin(idx).end(end).slideIndex(slideNumber).text(text.substring(idx, end))
                        .build());
                idx = text.indexOf(p, idx + Math.max(1, p.length()));
            }
        }

        // 중복 제거 (begin:end 기준)
        Map<String, OffsetDto> uniq = new LinkedHashMap<>();
        for (OffsetDto o : out) {
            String key = String.valueOf(o.getBegin()) + ":" + String.valueOf(o.getEnd());
            uniq.putIfAbsent(key, o);
        }
        return new ArrayList<>(uniq.values());
    }

    /**
     * 각 슬라이드 텍스트의 시작 global index를 전체 STT에서 찾아 반환 (없으면 -1)
     */
    public List<Integer> computeSlideStartOffsets(String fullText, List<String> slideTexts) {
        List<Integer> starts = new ArrayList<>();
        if (slideTexts == null || slideTexts.isEmpty() || fullText == null) {
            for (int i = 0; slideTexts != null && i < slideTexts.size(); i++)
                starts.add(-1);
            return starts;
        }

        int searchFrom = 0;
        for (String s : slideTexts) {
            if (s == null || s.isEmpty()) {
                starts.add(-1);
                continue;
            }
            int idx = fullText.indexOf(s, searchFrom);
            if (idx < 0)
                idx = fullText.indexOf(s.trim(), searchFrom);
            if (idx < 0) {
                starts.add(-1);
            } else {
                starts.add(idx);
                searchFrom = idx + s.length();
            }
        }
        return starts;
    }

    /**
     * global Offsets -> slide-local OffsetDto 변환 시도
     */
    public Optional<OffsetDto> convertGlobalOffsetToSlideOffset(
            RepetitiveTextAnalysisService.Offset globalOffset,
            List<Integer> slideStartIndices,
            List<String> slideTexts) {
        if (globalOffset == null || slideStartIndices == null || slideTexts == null)
            return Optional.empty();

        int gBegin = globalOffset.getBegin();
        int gEnd = globalOffset.getEnd();

        for (int si = 0; si < slideStartIndices.size(); si++) {
            Integer start = slideStartIndices.get(si);
            String slideText = slideTexts.get(si);
            if (start == null || start < 0 || slideText == null)
                continue;
            int slideLen = slideText.length();
            int slideEndGlobal = start + slideLen;
            if (gBegin >= start && gBegin < slideEndGlobal) {
                int localBegin = gBegin - start;
                int localEnd = Math.min(slideLen, Math.max(localBegin + 1, gEnd - start));
                String excerpt = "";
                try {
                    excerpt = slideText.substring(Math.max(0, localBegin), Math.min(slideLen, localEnd));
                } catch (Exception ex) {
                    excerpt = globalOffset.getText();
                }
                return Optional.of(OffsetDto.builder()
                        .begin(localBegin)
                        .end(localEnd)
                        .slideIndex(si + 1)
                        .text(excerpt)
                        .build());
            }
        }

        return Optional.empty();
    }

    /**
     * 문장을 슬라이드에 매핑 (기존 SlideSentenceMapper 기능)
     *
     * @param sentences     매핑할 문장 리스트
     * @param segments      Whisper segment 리스트
     * @param intervals     슬라이드 구간 리스트
     * @param slideContents 슬라이드별 대본 (선택적)
     * @return 각 문장이 속한 슬라이드 번호 리스트 (-1: 매칭 실패)
     */
    public List<Integer> mapSentencesToSlides(
            List<String> sentences,
            List<WhisperSegment> segments,
            List<SlideInterval> intervals,
            List<String> slideContents) {

        if (sentences == null || sentences.isEmpty()) {
            return Collections.emptyList();
        }

        if (intervals == null || intervals.isEmpty()) {
            return new ArrayList<>(Collections.nCopies(sentences.size(), -1));
        }

        // 슬라이드별 텍스트 구성
        Map<Integer, String> slideTexts = buildSlideTexts(
                segments, intervals, slideContents);

        // 문장 매핑
        return matchSentencesToSlides(sentences, slideTexts);
    }

    /**
     * 슬라이드별 텍스트 맵 생성
     */
    private Map<Integer, String> buildSlideTexts(
            List<WhisperSegment> segments,
            List<SlideInterval> intervals,
            List<String> slideContents) {

        Map<Integer, String> slideTexts = new LinkedHashMap<>();

        // 1순위: 명시적으로 제공된 슬라이드 대본
        if (slideContents != null && !slideContents.isEmpty()) {
            int size = Math.min(intervals.size(), slideContents.size());
            for (int i = 0; i < size; i++) {
                slideTexts.put(
                        intervals.get(i).getSlideNumber(),
                        slideContents.get(i));
            }
        }
        // 2순위: Whisper segment 기반 추출
        else if (segments != null && !segments.isEmpty()) {
            List<String> extractedTexts = extractSlideSttTexts(segments, intervals);
            for (int i = 0; i < intervals.size(); i++) {
                if (i < extractedTexts.size()) {
                    slideTexts.put(
                            intervals.get(i).getSlideNumber(),
                            extractedTexts.get(i));
                }
            }
        }

        return slideTexts;
    }

    /**
     * 문장을 슬라이드 텍스트와 매칭
     */
    private List<Integer> matchSentencesToSlides(
            List<String> sentences,
            Map<Integer, String> slideTexts) {

        List<Integer> mapping = new ArrayList<>(
                Collections.nCopies(sentences.size(), -1));

        // 문장 정규화
        List<String> normSentences = sentences.stream()
                .map(s -> TextAnalysisUtils.normalizeText(s).trim())
                .collect(Collectors.toList());

        for (Map.Entry<Integer, String> entry : slideTexts.entrySet()) {
            int slideNum = entry.getKey();
            String slideText = entry.getValue();

            if (slideText == null || slideText.isEmpty()) {
                continue;
            }

            // 슬라이드 텍스트를 문장으로 분할 및 정규화
            List<String> slideSentences = TextAnalysisUtils
                    .tokenizeSentences(slideText);
            List<String> normSlideSents = slideSentences.stream()
                    .map(s -> TextAnalysisUtils.normalizeText(s).trim())
                    .collect(Collectors.toList());

            // 토큰 집합 미리 계산
            List<Set<String>> slideTokenSets = normSlideSents.stream()
                    .map(s -> new HashSet<>(
                            TextAnalysisUtils.tokenizeKomoran(s)))
                    .collect(Collectors.toList());

            // 각 입력 문장을 슬라이드 문장과 매칭
            for (int i = 0; i < normSentences.size(); i++) {
                if (mapping.get(i) != -1)
                    continue; // 이미 매칭됨

                String sentence = normSentences.get(i);
                if (sentence.isEmpty())
                    continue;

                // 1단계: 정확한 매칭 (equals/contains)
                if (tryExactMatch(sentence, normSlideSents)) {
                    mapping.set(i, slideNum);
                    continue;
                }

                // 2단계: Jaccard 유사도 매칭
                if (tryJaccardMatch(sentence, slideTokenSets, 0.2)) {
                    mapping.set(i, slideNum);
                }
            }
        }

        return mapping;
    }

    /**
     * 정확한 문자열 매칭 시도
     */
    private boolean tryExactMatch(String sentence, List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate.isEmpty())
                continue;
            if (candidate.equals(sentence) ||
                    candidate.contains(sentence) ||
                    sentence.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Jaccard 유사도 기반 매칭 시도
     */
    private boolean tryJaccardMatch(
            String sentence,
            List<Set<String>> candidateTokenSets,
            double threshold) {

        Set<String> sentenceTokens = new HashSet<>(
                TextAnalysisUtils.tokenizeKomoran(sentence));

        for (Set<String> candidateTokens : candidateTokenSets) {
            double similarity = TextAnalysisUtils.jaccardSimilarity(
                    sentenceTokens, candidateTokens);
            if (similarity >= threshold) {
                return true;
            }
        }
        return false;
    }
}
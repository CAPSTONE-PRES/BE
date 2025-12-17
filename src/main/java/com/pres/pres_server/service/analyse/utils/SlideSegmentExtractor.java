package com.pres.pres_server.service.analyse.utils;

import com.pres.pres_server.service.analyse.TextAnalysisUtils;
import com.pres.pres_server.service.analyse.dto.SlideTransition;
import com.pres.pres_server.service.analyse.dto.WhisperSegment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import com.pres.pres_server.dto.practice.OffsetDto;
import com.pres.pres_server.service.analyse.RepetitiveTextAnalysisService;

@Component
public class SlideSegmentExtractor {
    private static final Logger log = LoggerFactory.getLogger(SlideSegmentExtractor.class);

    @Getter
    @AllArgsConstructor
    public static class SlideInterval {
        private int slideNumber;
        private int visitIndex;
        private int internalSlideIndex;
        private double startTime;
        private double endTime;

        public int getSlideIndex() {
            return internalSlideIndex;
        }

        public boolean contains(double timestamp) {
            return timestamp >= startTime && timestamp < endTime;
        }

        public boolean containsSegment(WhisperSegment segment) {
            return segment.getStart() < endTime && segment.getEnd() > startTime;
        }

        // 세그먼트와의 겹침 시간 계산
        public double getOverlapDuration(WhisperSegment segment) {
            double overlapStart = Math.max(startTime, segment.getStart());
            double overlapEnd = Math.min(endTime, segment.getEnd());
            return Math.max(0, overlapEnd - overlapStart);
        }
    }

    public List<SlideInterval> createSlideIntervals(
            List<SlideTransition> transitions,
            double totalDuration) {

        if (transitions == null || transitions.isEmpty()) {
            return Collections.emptyList();
        }

        List<SlideTransition> sorted = new ArrayList<>(transitions);
        sorted.sort(Comparator.comparingDouble(SlideTransition::getTimestamp));

        Map<Integer, Integer> visitCounters = new HashMap<>();
        List<SlideInterval> intervals = new ArrayList<>();

        for (int i = 0; i < sorted.size(); i++) {
            SlideTransition current = sorted.get(i);

            int slideNumber = current.getSlideNumber();
            int visitIndex = visitCounters.getOrDefault(slideNumber, 0);
            visitCounters.put(slideNumber, visitIndex + 1);

            double start = current.getTimestamp();
            double end;
            double endSec = current.getEndSec();

            if (endSec > start) {
                end = endSec;
            } else if (i + 1 < sorted.size()) {
                end = sorted.get(i + 1).getTimestamp();
            } else {
                end = totalDuration;
            }

            intervals.add(new SlideInterval(
                    slideNumber,
                    visitIndex,
                    i,
                    start,
                    end));
        }

        return intervals;
    }

    /**
     * 슬라이드별 STT 텍스트 추출 (개선된 버전)
     * 세그먼트가 여러 슬라이드에 걸쳐있을 경우 시간 비율에 따라 텍스트 분할
     */
    public List<String> extractSlideSttTexts(
            List<WhisperSegment> segments,
            List<SlideInterval> intervals) {

        if (segments == null || segments.isEmpty() || intervals.isEmpty()) {
            return Collections.nCopies(intervals.size(), "");
        }

        List<StringBuilder> slideTexts = new ArrayList<>();
        for (int i = 0; i < intervals.size(); i++) {
            slideTexts.add(new StringBuilder());
        }

        for (WhisperSegment segment : segments) {
            String segmentText = segment.getText();
            if (segmentText == null || segmentText.trim().isEmpty()) {
                continue;
            }

            // 이 세그먼트와 겹치는 모든 슬라이드 찾기
            List<Integer> overlappingSlides = new ArrayList<>();
            List<Double> overlapDurations = new ArrayList<>();
            double totalOverlap = 0.0;

            for (int i = 0; i < intervals.size(); i++) {
                SlideInterval interval = intervals.get(i);
                if (interval.containsSegment(segment)) {
                    double overlap = interval.getOverlapDuration(segment);
                    if (overlap > 0) {
                        overlappingSlides.add(i);
                        overlapDurations.add(overlap);
                        totalOverlap += overlap;
                    }
                }
            }

            if (overlappingSlides.isEmpty()) {
                continue;
            }

            // 1개 슬라이드에만 속하는 경우: 전체 텍스트 할당
            if (overlappingSlides.size() == 1) {
                int slideIdx = overlappingSlides.get(0);
                if (slideTexts.get(slideIdx).length() > 0) {
                    slideTexts.get(slideIdx).append(" ");
                }
                slideTexts.get(slideIdx).append(segmentText.trim());
                continue;
            }

            // 여러 슬라이드에 걸쳐있는 경우: 시간 비율에 따라 분할
            log.debug("Segment ({}-{}s) overlaps {} slides, splitting text proportionally",
                    segment.getStart(), segment.getEnd(), overlappingSlides.size());

            String[] words = segmentText.trim().split("\\s+");
            int wordsAssigned = 0;

            for (int i = 0; i < overlappingSlides.size(); i++) {
                int slideIdx = overlappingSlides.get(i);
                double ratio = overlapDurations.get(i) / totalOverlap;

                // 마지막 슬라이드는 남은 모든 단어 할당
                int wordsForThisSlide;
                if (i == overlappingSlides.size() - 1) {
                    wordsForThisSlide = words.length - wordsAssigned;
                } else {
                    wordsForThisSlide = (int) Math.round(words.length * ratio);
                }

                if (wordsForThisSlide > 0) {
                    int endIdx = Math.min(wordsAssigned + wordsForThisSlide, words.length);
                    String partialText = String.join(" ",
                            Arrays.copyOfRange(words, wordsAssigned, endIdx));

                    if (slideTexts.get(slideIdx).length() > 0) {
                        slideTexts.get(slideIdx).append(" ");
                    }
                    slideTexts.get(slideIdx).append(partialText);

                    log.debug("  Slide {} ({}): assigned {} words (ratio={:.2f})",
                            intervals.get(slideIdx).getSlideNumber(),
                            slideIdx,
                            wordsForThisSlide,
                            ratio);

                    wordsAssigned = endIdx;
                }
            }
        }

        return slideTexts.stream()
                .map(sb -> sb.toString().trim())
                .collect(Collectors.toList());
    }

    /**
     * 슬라이드별로 WhisperSegment 분할 (레거시 호환용)
     * 주의: 이 메서드는 세그먼트를 첫 번째 매칭 슬라이드에만 할당합니다.
     * 텍스트 추출에는 extractSlideSttTexts() 사용을 권장합니다.
     */
    public List<List<WhisperSegment>> splitSegmentsBySlides(
            List<WhisperSegment> segments,
            List<SlideInterval> intervals) {

        if (segments == null || segments.isEmpty() || intervals.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<WhisperSegment>> result = new ArrayList<>();
        for (int i = 0; i < intervals.size(); i++) {
            result.add(new ArrayList<>());
        }

        for (WhisperSegment segment : segments) {
            // 가장 많이 겹치는 슬라이드 찾기
            int bestSlideIdx = -1;
            double maxOverlap = 0.0;

            for (int i = 0; i < intervals.size(); i++) {
                if (intervals.get(i).containsSegment(segment)) {
                    double overlap = intervals.get(i).getOverlapDuration(segment);
                    if (overlap > maxOverlap) {
                        maxOverlap = overlap;
                        bestSlideIdx = i;
                    }
                }
            }

            if (bestSlideIdx >= 0) {
                result.get(bestSlideIdx).add(segment);
            }
        }

        return result;
    }

    public List<OffsetDto> collectOffsetsForSlide(String slideText, List<String> patterns, int slideNumber,
                                                  int visitIndex) {
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
                out.add(OffsetDto.builder().begin(idx).end(end).slideIndex(slideNumber)
                        .visitIndex(visitIndex)
                        .text(text.substring(idx, end))
                        .build());
                idx = text.indexOf(p, idx + Math.max(1, p.length()));
            }
        }

        Map<String, OffsetDto> uniq = new LinkedHashMap<>();
        for (OffsetDto o : out) {
            String key = String.valueOf(o.getBegin()) + ":" + String.valueOf(o.getEnd());
            uniq.putIfAbsent(key, o);
        }
        return new ArrayList<>(uniq.values());
    }

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

    @Deprecated
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
                        .slideIndex(si)
                        .visitIndex(globalOffset.getVisitIndex())
                        .text(excerpt)
                        .build());
            }
        }

        return Optional.empty();
    }

    @Deprecated
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

        Map<Integer, String> slideTexts = buildSlideTexts(
                segments, intervals, slideContents);

        return matchSentencesToSlides(sentences, slideTexts);
    }

    private Map<Integer, String> buildSlideTexts(
            List<WhisperSegment> segments,
            List<SlideInterval> intervals,
            List<String> slideContents) {

        Map<Integer, String> slideTexts = new LinkedHashMap<>();

        if (slideContents != null && !slideContents.isEmpty()) {
            int size = Math.min(intervals.size(), slideContents.size());
            for (int i = 0; i < size; i++) {
                slideTexts.put(
                        intervals.get(i).getSlideNumber(),
                        slideContents.get(i));
            }
        } else if (segments != null && !segments.isEmpty()) {
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

    private List<Integer> matchSentencesToSlides(
            List<String> sentences,
            Map<Integer, String> slideTexts) {

        List<Integer> mapping = new ArrayList<>(
                Collections.nCopies(sentences.size(), -1));

        List<String> normSentences = sentences.stream()
                .map(s -> TextAnalysisUtils.normalizeText(s).trim())
                .collect(Collectors.toList());

        for (Map.Entry<Integer, String> entry : slideTexts.entrySet()) {
            int slideNum = entry.getKey();
            String slideText = entry.getValue();

            if (slideText == null || slideText.isEmpty()) {
                continue;
            }

            List<String> slideSentences = TextAnalysisUtils
                    .tokenizeSentences(slideText);
            List<String> normSlideSents = slideSentences.stream()
                    .map(s -> TextAnalysisUtils.normalizeText(s).trim())
                    .collect(Collectors.toList());

            List<Set<String>> slideTokenSets = normSlideSents.stream()
                    .map(s -> new HashSet<>(
                            TextAnalysisUtils.tokenizeKomoran(s)))
                    .collect(Collectors.toList());

            for (int i = 0; i < normSentences.size(); i++) {
                if (mapping.get(i) != -1)
                    continue;

                String sentence = normSentences.get(i);
                if (sentence.isEmpty())
                    continue;

                if (tryExactMatch(sentence, normSlideSents)) {
                    mapping.set(i, slideNum);
                    continue;
                }

                if (tryJaccardMatch(sentence, slideTokenSets, 0.2)) {
                    mapping.set(i, slideNum);
                }
            }
        }

        return mapping;
    }

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
package com.pres.pres_server.service.analyse;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 슬라이드와 문장을 매핑하는 유틸리티
 * - 기존 `RepetitiveTextAnalysisService.mapSentencesToSlides` 내용에서 추출됨
 */
import com.pres.pres_server.service.analyse.dto.SlideTransition;
import com.pres.pres_server.service.analyse.dto.WhisperSegment;
import lombok.Data;

@Deprecated
// TODO: Using SlideSegmentExtractor instead
public class SlideSentenceMapper {

    /**
     * 기존 동작을 유지하는 메서드(호출 호환성 유지)
     */
    public static List<Integer> mapSentencesToSlides(String sttText, List<String> sentences,
            List<SlideTransition> transitions,
            List<WhisperSegment> segments) {
        return mapSentencesToSlides(sttText, sentences, transitions, segments, null);
    }

    /**
     * 새 오버로드: 발표자료의 슬라이드 텍스트(`slideContents`)가 제공되면 이를 우선적으로 사용하여
     * STT 문장들을 슬라이드에 매핑합니다. 실패 시 기존 세그먼트/타임스탬프 기반 매핑을 수행합니다.
     *
     * Matching strategy:
     * 1) exact equals / contains 체크
     * 2) 토큰 기반 Jaccard fallback (threshold 0.2)
     */
    public static List<Integer> mapSentencesToSlides(String sttText, List<String> sentences,
            List<SlideTransition> transitions,
            List<com.pres.pres_server.service.analyse.dto.WhisperSegment> segments,
            List<String> slideContents) {
        Map<Integer, String> slideTexts = new LinkedHashMap<>();

        if (transitions == null || transitions.isEmpty()) {
            return new ArrayList<>(Collections.nCopies(sentences.size(), -1));
        }

        // 1) 발표자료(명시된 slideContents)가 있으면 우선으로 사용
        if (slideContents != null && !slideContents.isEmpty()) {
            // Assume slideContents are in the same order as transitions; map by index up to
            // min size
            int m = Math.min(transitions.size(), slideContents.size());
            for (int i = 0; i < m; i++) {
                int slideNum = transitions.get(i).getSlideNumber();
                slideTexts.put(slideNum, slideContents.get(i));
            }
        } else if (segments != null && !segments.isEmpty()) {
            List<SlideTransition> sorted = new ArrayList<>(transitions);
            sorted.sort(Comparator.comparingDouble(SlideTransition::getTimestamp));

            Map<Integer, StringBuilder> builders = new HashMap<>();
            for (SlideTransition t : sorted)
                builders.put(t.getSlideNumber(), new StringBuilder());

            for (WhisperSegment seg : segments) {
                double segMid = (seg.getStart() + seg.getEnd()) / 2.0;
                int targetSlide = -1;
                for (int i = 0; i < sorted.size(); i++) {
                    double ts = sorted.get(i).getTimestamp();
                    double nextTs = (i + 1 < sorted.size()) ? sorted.get(i + 1).getTimestamp()
                            : Double.POSITIVE_INFINITY;
                    if (segMid >= ts && segMid < nextTs) {
                        targetSlide = sorted.get(i).getSlideNumber();
                        break;
                    }
                }

                if (targetSlide >= 0) {
                    StringBuilder sb = builders.get(targetSlide);
                    if (sb != null) {
                        if (sb.length() > 0)
                            sb.append(" ");
                        sb.append(seg.getText());
                    }
                }
            }

            for (Map.Entry<Integer, StringBuilder> e : builders.entrySet()) {
                slideTexts.put(e.getKey(), e.getValue().toString());
            }
        } else {
            // fallback: sttText 기반 단순 분배
            List<String> slides = TestRepetitiveService.splitTextByTimestampsFallbackStatic(sttText,
                    transitions);
            for (int i = 0; i < slides.size(); i++) {
                int slideNum = transitions.get(i).getSlideNumber();
                slideTexts.put(slideNum, slides.get(i));
            }
        }

        List<Integer> mapping = new ArrayList<>(Collections.nCopies(sentences.size(), -1));

        // Prepare normalized input sentences once
        List<String> normSentences = sentences.stream()
                .map(s -> TextAnalysisUtils.normalizeText(s).trim())
                .collect(Collectors.toList());

        for (Map.Entry<Integer, String> e : slideTexts.entrySet()) {
            int slideNum = e.getKey();
            String text = e.getValue();
            if (text == null || text.isEmpty())
                continue;

            List<String> slideSentences = TextAnalysisUtils.tokenizeSentences(text);
            List<String> normSlideSents = slideSentences.stream()
                    .map(s -> TextAnalysisUtils.normalizeText(s).trim())
                    .collect(Collectors.toList());

            // precompute token sets for slide sentences
            List<Set<String>> slideTokenSets = normSlideSents.stream()
                    .map(s -> new HashSet<>(TextAnalysisUtils.tokenizeKomoran(s)))
                    .collect(Collectors.toList());

            for (int i = 0; i < normSentences.size(); i++) {
                if (mapping.get(i) != -1)
                    continue;

                String s = normSentences.get(i);
                if (s.isEmpty())
                    continue;

                boolean matched = false;
                // 1) exact / contains
                for (int k = 0; k < normSlideSents.size(); k++) {
                    String ss = normSlideSents.get(k);
                    if (ss.isEmpty())
                        continue;
                    if (ss.equals(s) || ss.contains(s) || s.contains(ss)) {
                        mapping.set(i, slideNum);
                        matched = true;
                        break;
                    }
                }

                if (matched)
                    continue;

                // 2) token Jaccard fallback
                Set<String> tokS = new HashSet<>(TextAnalysisUtils.tokenizeKomoran(s));
                for (int k = 0; k < slideTokenSets.size(); k++) {
                    double j = TextAnalysisUtils.jaccardSimilarity(tokS, slideTokenSets.get(k));
                    if (j >= 0.2) { // threshold
                        mapping.set(i, slideNum);
                        matched = true;
                        break;
                    }
                }

            }
        }

        return mapping;
    }
}

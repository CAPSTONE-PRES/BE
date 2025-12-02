package com.pres.pres_server.service.analyse;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.*;
import java.util.stream.Collectors;
import com.pres.pres_server.service.analyse.dto.SlideTransition;
import com.pres.pres_server.service.analyse.dto.WhisperSegment;
import com.pres.pres_server.service.analyse.utils.KomoranAnalyzer;

//반복 어휘 분석 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class RepetitiveTextAnalysisService {

    private final com.pres.pres_server.service.analyse.utils.SlideSegmentExtractor slideSegmentExtractor;

    @Getter
    @Builder
    public static class SlideRepetitionAnalysisResult {
        private final int slideIndex;
        private final List<WordRepetition> wordRepetitions;
        private final List<RepetitivePattern> nGramPatterns;
        private final List<RepetitiveSentencePair> similarSentencePairs;
        private final int totalSentences;
        private final boolean success;

        public static SlideRepetitionAnalysisResult failed(int slideIndex) {
            return SlideRepetitionAnalysisResult.builder()
                    .slideIndex(slideIndex)
                    .wordRepetitions(new ArrayList<>())
                    .nGramPatterns(new ArrayList<>())
                    .similarSentencePairs(new ArrayList<>())
                    .totalSentences(0)
                    .success(false)
                    .build();
        }
    }

    // ========== 설정 상수 ==========

    private static final Set<String> FILLER_WORDS = Set.of(
            "음", "어", "아", "뭐지",
            "그러니까", "그 다음에", "약간", "되게", "엄청", "진짜",
            "막", "이제", "그냥", "좀", "완전", "정말");

    private static final int MIN_REPETITION_COUNT = 3;
    private static final int MIN_KEYWORD_FREQUENCY = 2;
    private static final double SIMILARITY_THRESHOLD = 0.7;
    // n-gram size constant removed (unused here); keep per-method usage when needed
    private static final int MIN_NGRAM_REPETITION = 3;
    private static final int MAX_SIMILAR_SENTENCES = 10;
    private static final int SIMILAR_SENTENCES_WINDOW_LIMIT = 200;
    private static final double SIMILAR_SENTENCES_JACCARD_THRESHOLD = 0.15;

    // 🎯 슬라이드 기반 분석 시 인접 슬라이드 범위
    private static final int SLIDE_NEIGHBORS = 1;

    // ========== Public API ==========

    // 슬라이드 전환 정보 포함 호출용
    public RepetitionAnalysisResult analyzeRepetition(String sttText, List<SlideTransition> slideTransitions) {
        return analyzeRepetition(sttText, slideTransitions, null, null, null);
    }

    // 편의 오버로드: segments 없이 전체 텍스트만 전달하는 호출을 위한 메서드
    public RepetitionAnalysisResult analyzeRepetition(String sttText) {
        return analyzeRepetition(sttText, null, null, null, null);
    }

    // 기존 호환성 유지를 위한 오버로드
    public RepetitionAnalysisResult analyzeRepetition(
            String sttText,
            List<SlideTransition> slideTransitions,
            List<WhisperSegment> segments) {
        return analyzeRepetition(sttText, slideTransitions, segments, null, null);
    }

    /**
     * 전체 반복 분석 수행
     * 
     * @param sttText          STT 전체 텍스트
     * @param slideTransitions 슬라이드 전환 타임스탬프 (optional)
     * @param segments         Whisper 세그먼트 (optional, 정밀 매핑용)
     * @param slideSttTexts    슬라이드별 STT 텍스트 목록 (optional, L2 분석용)
     * @return 분석 결과
     */
    public RepetitionAnalysisResult analyzeRepetition(
            String sttText,
            List<SlideTransition> slideTransitions,
            List<WhisperSegment> segments,
            Set<String> presentationKeywords,
            List<String> slideSttTexts) {
        // 기존 호출 호환성 유지: slideScripts를 제공하지 않은 경우 null 전달
        return analyzeRepetition(sttText, slideTransitions, segments, null, presentationKeywords, slideSttTexts);
    }

    /**
     * 확장된 analyzeRepetition: 대본(slideScripts)을 함께 전달하면
     * 내부에서 대본 기반으로 presentationKeywords를 추출하여 사용합니다.
     */
    public RepetitionAnalysisResult analyzeRepetition(
            String sttText,
            List<SlideTransition> slideTransitions,
            List<WhisperSegment> segments,
            List<String> slideScripts,
            Set<String> presentationKeywords,
            List<String> slideSttTexts) {

        log.info("▶ Repetition analysis started - Text: {}chars, Slides: {}, Segments: {}",
                sttText != null ? sttText.length() : 0,
                slideTransitions != null ? slideTransitions.size() : 0,
                segments != null ? segments.size() : 0);

        // 전처리 (segments 기반 매핑 포함)
        // presentationKeywords는 외부에서 주입되거나, slideScripts로부터 추출됨
        PreprocessResult pre = preprocessStt(sttText, segments, presentationKeywords, slideScripts);
        if (!pre.isValid()) {
            return RepetitionAnalysisResult.failed("STT 전처리 실패: 분석할 문장이 없습니다.");
        }

        // 단어 반복 분석 실행 (원문 기준 위치 정보 포함)
        List<WordRepetition> wordRepetitions = analyzeWordRepetition(pre, sttText, slideTransitions, segments,
                slideSttTexts);

        // 슬라이드 전환 정보가 있을 때만 L2 실행
        List<SlideRepetition> slideRepetitions = Collections.emptyList();
        if (slideTransitions != null && !slideTransitions.isEmpty()) {
            slideRepetitions = analyzeIntraSlideNgramRepetition(sttText, slideTransitions, segments, slideSttTexts,
                    pre.presentationKeywords);
        }

        List<RepetitivePattern> ngramRepetitions = analyzeNgramRepetition(pre.normalizedText, sttText, slideTransitions,
                segments, slideSttTexts);

        List<RepetitiveSentencePair> sentenceSimilarities = analyzeSentenceSimilarity(
                sttText, pre.sentences, pre.presentationKeywords, slideTransitions, segments);

        // 3️⃣ 점수 계산
        int score = calculateScore(wordRepetitions, slideRepetitions, ngramRepetitions, sentenceSimilarities);

        log.info("✅ Analysis completed - Score: {}, L1: {}, L2: {}, L3-N: {}, L3-S: {}",
                score, wordRepetitions.size(), slideRepetitions.size(), ngramRepetitions.size(),
                sentenceSimilarities.size());

        RepetitionAnalysisResult result = RepetitionAnalysisResult.builder()
                .repetitionScore(score)
                .wordRepetitions(wordRepetitions)
                .slideRepetitions(slideRepetitions)
                .nGramPatterns(ngramRepetitions)
                .similarSentencePairs(sentenceSimilarities)
                .totalSentences(pre.sentences.size())
                .presentationKeywords(pre.presentationKeywords)
                .success(true)
                .build();

        try {
            int l1OffsetsWithSlide = 0;
            if (result.getWordRepetitions() != null) {
                for (WordRepetition wr : result.getWordRepetitions()) {
                    if (wr.getOffsets() != null) {
                        for (Offset o : wr.getOffsets()) {
                            if (o != null && o.getSlideIndex() != null)
                                l1OffsetsWithSlide++;
                        }
                    }
                }
            }

            int l3OffsetsWithSlide = 0;
            if (result.getNGramPatterns() != null) {
                for (RepetitivePattern rp : result.getNGramPatterns()) {
                    if (rp.getOffsets() != null) {
                        for (Offset o : rp.getOffsets()) {
                            if (o != null && o.getSlideIndex() != null)
                                l3OffsetsWithSlide++;
                        }
                    }
                }
            }

            log.info("  • L1 offsets with slideIndex: {}, L3 offsets with slideIndex: {}", l1OffsetsWithSlide,
                    l3OffsetsWithSlide);
        } catch (Exception ex) {
            log.debug("Failed to compute offset slideIndex summary: {}", ex.getMessage());
        }

        return result;
    }

    // ========== Level 1: 단어 반복(Word Repetition) ==========

    private List<WordRepetition> analyzeWordRepetition(PreprocessResult pre, String sttText,
            List<SlideTransition> slideTransitions,
            List<WhisperSegment> segments,
            List<String> slideSttTexts) {
        // 토큰화(문장 기준)로 빈도 집계
        List<String> tokens = new ArrayList<>();
        for (String sentence : pre.sentences) {
            tokens.addAll(TextAnalysisUtils.tokenizeKomoranNorms(sentence));
        }
        Map<String, Long> wordFreq = tokens.stream()
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));

        // KomoranAnalyzer로 원문 기준 위치(Offsets) 추출
        List<com.pres.pres_server.service.analyse.utils.KomoranAnalyzer.NormToken> normTokens = com.pres.pres_server.service.analyse.utils.KomoranAnalyzer
                .tokenizeForRepeatWithSpans(sttText);

        // normalize token list -> map of norm -> list of Offsets
        Map<String, List<Offset>> OffsetsMap = new HashMap<>();
        // If slideSttTexts not provided but slideTransitions+segments are available,
        // attempt to map segments->slides to build slideSttTexts for L1 offset mapping.
        if ((slideSttTexts == null || slideSttTexts.isEmpty()) && segments != null
                && slideTransitions != null && !slideTransitions.isEmpty()) {
            try {
                Map<Integer, String> mapped = mapTextToSlides(sttText, slideTransitions, segments);
                if (mapped != null && !mapped.isEmpty()) {
                    // build list ordered by slide number 1..N (fill missing with empty)
                    int max = mapped.keySet().stream().max(Integer::compareTo).orElse(0);
                    List<String> list = new ArrayList<>(Collections.nCopies(max, ""));
                    for (Map.Entry<Integer, String> me : mapped.entrySet()) {
                        int idx = me.getKey() - 1;
                        if (idx >= 0 && idx < list.size())
                            list.set(idx, me.getValue());
                    }
                    slideSttTexts = list;
                    log.debug("Computed slideSttTexts from segments/transitions: size={}", slideSttTexts.size());
                }
            } catch (Exception ex) {
                log.debug("Failed to compute slideSttTexts from segments: {}", ex.getMessage());
            }
        }

        // compute slide start indices if slideSttTexts provided
        List<Integer> slideStartIndices = null;
        if (slideSttTexts != null && !slideSttTexts.isEmpty()) {
            try {
                slideStartIndices = slideSegmentExtractor.computeSlideStartOffsets(sttText, slideSttTexts);
            } catch (Exception ex) {
                log.debug("Failed to compute slide start indices: {}", ex.getMessage());
                slideStartIndices = null;
            }
        }

        // Fallback: if we couldn't compute slideStartIndices but have
        // segments+transitions
        // attempt to build slideSttTexts from segments and recompute start indices.
        if ((slideStartIndices == null || slideStartIndices.isEmpty())
                && (slideSttTexts == null || slideSttTexts.isEmpty())
                && segments != null && slideTransitions != null && !slideTransitions.isEmpty()) {
            try {
                Map<Integer, String> mapped = mapTextToSlides(sttText, slideTransitions, segments);
                if (mapped != null && !mapped.isEmpty()) {
                    int max = mapped.keySet().stream().max(Integer::compareTo).orElse(0);
                    List<String> list = new ArrayList<>(Collections.nCopies(max, ""));
                    for (Map.Entry<Integer, String> me : mapped.entrySet()) {
                        int idx = me.getKey() - 1;
                        if (idx >= 0 && idx < list.size())
                            list.set(idx, me.getValue());
                    }
                    slideSttTexts = list;
                    try {
                        slideStartIndices = slideSegmentExtractor.computeSlideStartOffsets(sttText, slideSttTexts);
                        log.debug("Fallback computed slideSttTexts and slideStartIndices: slides={}, startIndices={}",
                                slideSttTexts.size(), slideStartIndices != null ? slideStartIndices.size() : 0);
                    } catch (Exception ex) {
                        log.debug("Fallback compute slide start indices failed: {}", ex.getMessage());
                        slideStartIndices = null;
                    }
                }
            } catch (Exception ex) {
                log.debug("Failed to compute slideSttTexts from segments in fallback: {}", ex.getMessage());
            }
        }

        for (com.pres.pres_server.service.analyse.utils.KomoranAnalyzer.NormToken nt : normTokens) {
            Integer mappedSlide = null;
            if (slideStartIndices != null && slideSttTexts != null) {
                // find slide containing nt.begin
                for (int si = 0; si < slideStartIndices.size(); si++) {
                    Integer start = slideStartIndices.get(si);
                    if (start == null || start < 0)
                        continue;
                    String slideText = slideSttTexts.get(si);
                    int slideLen = slideText != null ? slideText.length() : 0;
                    int slideEndGlobal = start + slideLen;
                    if (nt.begin >= start && nt.begin < slideEndGlobal) {
                        mappedSlide = si + 1; // 1-based
                        break;
                    }
                }
            }
            OffsetsMap.computeIfAbsent(nt.norm, k -> new ArrayList<>())
                    .add(Offset.builder().begin(nt.begin).end(nt.end)
                            .text(sttText.substring(nt.begin, Math.min(nt.end, sttText.length())))
                            .slideIndex(mappedSlide)
                            .build());
        }

        // Debug logging
        log.info("  • Total tokens: {}, Unique tokens: {}", tokens.size(), wordFreq.size());
        log.info("  • Presentation keywords: {}", pre.presentationKeywords);
        log.info("  • Top 10 frequent words: {}",
                wordFreq.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                        .limit(10)
                        .collect(Collectors.toList()));

        // 키워드로 제외된 반복 단어들 추출
        List<String> excludedByKeywords = wordFreq.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_REPETITION_COUNT)
                .filter(e -> pre.presentationKeywords.contains(e.getKey()))
                .filter(e -> !FILLER_WORDS.contains(e.getKey()))
                .map(e -> e.getKey() + "(" + e.getValue() + "회)")
                .sorted()
                .collect(Collectors.toList());

        // 필러 워드로 제외된 반복 단어들 추출 (중복 점수 차감 방지)
        List<String> excludedByFillers = wordFreq.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_REPETITION_COUNT)
                .filter(e -> FILLER_WORDS.contains(e.getKey()))
                .map(e -> e.getKey() + "(" + e.getValue() + "회)")
                .sorted()
                .collect(Collectors.toList());

        List<WordRepetition> result = wordFreq.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_REPETITION_COUNT)
                .filter(e -> !pre.presentationKeywords.contains(e.getKey()))
                .filter(e -> !FILLER_WORDS.contains(e.getKey())) // 중복 점수 차감 방지
                .map(e -> {
                    List<Offset> occ = OffsetsMap.getOrDefault(e.getKey(), Collections.emptyList());
                    return WordRepetition.builder()
                            .word(e.getKey())
                            .count(e.getValue().intValue())
                            .Offsets(occ)
                            .build();
                })
                .sorted(Comparator.comparingInt(WordRepetition::getCount).reversed())
                .collect(Collectors.toList());

        log.info("  • Word repetitions found: {}", result);
        if (!excludedByKeywords.isEmpty()) {
            log.info("  • Excluded by keywords: {}", excludedByKeywords);
        }
        if (!excludedByFillers.isEmpty()) {
            log.info("  • Excluded by fillers (avoid double penalty): {}", excludedByFillers);
        }
        return result;
    }

    // ========== Level 2: 슬라이드 내 2-gram 반복 (In-Slide 2-gram Repetition) ==========
    private List<SlideRepetition> analyzeIntraSlideNgramRepetition(
            String sttText,
            List<SlideTransition> transitions,
            List<WhisperSegment> segments,
            List<String> slideSttTexts,
            Set<String> presentationKeywords) {

        if (transitions == null || transitions.isEmpty()) {
            log.info("  • L2: No transitions provided, skipping in-slide analysis");
            return Collections.emptyList();
        }

        // slideSttTexts가 제공되면 우선 사용, 없으면 mapTextToSlides 사용
        Map<Integer, String> slideTextMap;
        if (slideSttTexts != null && !slideSttTexts.isEmpty()) {
            slideTextMap = new HashMap<>();
            for (int i = 0; i < slideSttTexts.size(); i++) {
                slideTextMap.put(i + 1, slideSttTexts.get(i)); // 1-based index
            }
            log.info("L2 using provided slideSttTexts: {} slides", slideTextMap.size());
            // 각 슬라이드 텍스트 샘플 출력
            for (Map.Entry<Integer, String> entry : slideTextMap.entrySet()) {
                String preview = entry.getValue();
                if (preview != null && preview.length() > 50) {
                    preview = preview.substring(0, 50) + "...";
                }
                log.info("  - Slide {}: {} chars, preview: '{}'", entry.getKey(),
                        entry.getValue() != null ? entry.getValue().length() : 0, preview);
            }
        } else {
            // 슬라이드별 텍스트 매핑
            slideTextMap = mapTextToSlides(sttText, transitions, segments);
            log.info("L2 mapTextToSlides keys: {}", slideTextMap.keySet());
        }
        if (slideTextMap.isEmpty())
            return Collections.emptyList();

        List<SlideRepetition> out = new ArrayList<>();
        Set<String> keywords = (presentationKeywords != null) ? presentationKeywords : Collections.emptySet();

        // 필터링 카운터
        int filteredByKeywords = 0;
        int filteredByFillers = 0;

        for (Map.Entry<Integer, String> e : slideTextMap.entrySet()) {
            int slideNum = e.getKey();
            String slideText = e.getValue();
            if (slideText == null || slideText.isBlank())
                continue;

            // Komoran 기반 의미 있는 2-gram 추출 및 빈도 계산
            List<String> grams = KomoranAnalyzer.komoranMeaningfulNGrams(slideText, 2);
            if (grams.isEmpty())
                continue;
            List<KomoranAnalyzer.NormToken> normTokens = KomoranAnalyzer.tokenizeForRepeatWithSpans(slideText);

            Map<String, Long> freq = grams.stream()
                    .collect(Collectors.groupingBy(g -> g, Collectors.counting()));

            for (Map.Entry<String, Long> ge : freq.entrySet()) {
                if (ge.getValue() < MIN_NGRAM_REPETITION)
                    continue;

                // 🎯 필터링: 2-gram의 두 단어 추출
                String[] words = ge.getKey().split("\\s+");
                if (words.length != 2)
                    continue;

                String word1 = words[0];
                String word2 = words[1];

                // 필터 1: 두 단어 모두 presentation keywords면 제외
                if (keywords.contains(word1) && keywords.contains(word2)) {
                    filteredByKeywords++;
                    continue;
                }

                // 필터 2: 하나라도 filler word면 제외
                if (FILLER_WORDS.contains(word1) || FILLER_WORDS.contains(word2)) {
                    filteredByFillers++;
                    continue;
                }

                // collect Offsets spans for this slide
                List<Offset> occs = new ArrayList<>();

                for (int i = 0; i <= normTokens.size() - 2; i++) {
                    List<KomoranAnalyzer.NormToken> window = normTokens.subList(i, i + 2);
                    String key = window.stream().map(t -> t.norm).collect(Collectors.joining(" "));

                    if (key.equals(ge.getKey())) {
                        int begin = window.get(0).begin;
                        int end = window.get(1).end;
                        String text = slideText.substring(Math.max(0, begin), Math.min(slideText.length(), end));
                        occs.add(Offset.builder().begin(begin).end(end).slideIndex(slideNum).text(text).build());
                    }
                }

                // 디버그: 생성되는 SlideRepetition 정보 로그
                log.info("Created SlideRepetition: slideNum={}, pattern='{}', count={}, occs={}", slideNum, ge.getKey(),
                        ge.getValue(), occs.size());
                out.add(SlideRepetition.builder()
                        .pattern(ge.getKey())
                        .slideIndex(slideNum)
                        .slideIndices(List.of(slideNum))
                        .scope("INTRA_SLIDE")
                        .count(ge.getValue().intValue())
                        .offsets(occs) // offset 정보 포함
                        .build());
            }
        }

        // 많이 나온 순으로 전체 정렬
        out.sort(Comparator.comparingInt(SlideRepetition::getCount).reversed());

        // 필터링 통계 로그
        log.info("  • L2 filtering stats - Excluded by keywords: {}, Excluded by fillers: {}",
                filteredByKeywords, filteredByFillers);
        log.info("  • L2 final results: {} patterns", out.size());

        return out;
    }

    private Map<Integer, String> mapTextToSlides(
            String sttText,
            List<SlideTransition> transitions,
            List<WhisperSegment> segments) {

        // segments 기반 매핑 우선 시도
        Map<Integer, String> result = mapWithSegments(transitions, segments);
        if (!result.isEmpty())
            return result;

        // segments 기반 매핑 실패 시, fallback: 균등 분할
        return mapWithFallback(sttText, transitions);
    }

    // segments 기반 정밀 매핑
    private Map<Integer, String> mapWithSegments(
            List<SlideTransition> transitions,
            List<WhisperSegment> segments) {

        List<SlideTransition> sorted = new ArrayList<>(transitions);
        sorted.sort(Comparator.comparingDouble(SlideTransition::getStartSec));

        Map<Integer, StringBuilder> builders = new HashMap<>();
        for (SlideTransition st : sorted) {
            // SlideTransition.slideNumber는 0-based이므로, 분석 결과의 일관성을 위해
            // 반환되는 맵의 키는 1-based로 정규화합니다.
            builders.put(st.getSlideNumber() + 1, new StringBuilder());
        }

        for (WhisperSegment seg : segments) {
            double mid = (seg.getStart() + seg.getEnd()) / 2.0;
            int slideNum = findSlideNumber(mid, sorted);

            if (slideNum > 0) {
                // slideNum은 이제 1-based임
                StringBuilder sb = builders.get(slideNum);
                if (sb != null) {
                    if (sb.length() > 0)
                        sb.append(" ");
                    sb.append(seg.getText());
                }
            }
        }

        Map<Integer, String> result = new HashMap<>();
        for (Map.Entry<Integer, StringBuilder> e : builders.entrySet()) {
            result.put(e.getKey(), e.getValue().toString());
        }
        return result;
    }

    private int findSlideNumber(double ts, List<SlideTransition> sorted) {
        for (SlideTransition st : sorted) {
            double start = st.getStartSec();
            double end = st.getEndSec();
            // end==0 이거나 start==end 인 경우는 skip
            if (end > start && ts >= start && ts < end) {
                // SlideTransition.slideNumber는 0-based, 반환은 1-based
                return st.getSlideNumber() + 1;
            }
        }
        return -1;
    }

    /**
     * segments가 없을 때 사용되는 window 기반 매핑.
     * transitions가 없으면 전체 텍스트를 slide 0으로 매핑함.
     */
    private Map<Integer, String> mapWithFallback(String sttText, List<SlideTransition> transitions) {
        if (sttText == null)
            sttText = "";

        if (transitions == null || transitions.isEmpty()) {
            Map<Integer, String> single = new HashMap<>();
            single.put(0, sttText.trim());
            return single;
        }

        List<String> slideTexts = splitTextByTimestampsFallbackStatic(sttText, transitions);
        Map<Integer, String> result = new HashMap<>();
        int limit = Math.min(slideTexts.size(), transitions.size());
        for (int i = 0; i < limit; i++) {
            // normalize to 1-based slide number
            result.put(transitions.get(i).getSlideNumber() + 1, slideTexts.get(i));
        }
        return result;
    }

    /**
     * window 기반 균등 분할 유틸 (정적으로도 사용 가능)
     */
    private static List<String> splitTextByTimestampsFallbackStatic(String sttText, List<SlideTransition> transitions) {
        List<String> sentences = TextAnalysisUtils.tokenizeKoreanSentences(sttText);
        if (sentences.isEmpty()) {
            return Collections.emptyList();
        }

        if (transitions == null || transitions.isEmpty()) {
            return Collections.singletonList(String.join(". ", sentences));
        }

        int slides = transitions.size();
        int sentencesPerSlide = Math.max(1, sentences.size() / slides);
        List<String> slideTexts = new ArrayList<>(slides);

        for (int i = 0; i < slides; i++) {
            int start = i * sentencesPerSlide;
            int end = (i == slides - 1) ? sentences.size() : Math.min((i + 1) * sentencesPerSlide, sentences.size());
            if (start < sentences.size()) {
                slideTexts.add(String.join(". ", sentences.subList(start, end)));
            } else {
                slideTexts.add("");
            }
        }
        return slideTexts;
    }

    // ========== Level 3: N-gram 반복 ==========

    private List<RepetitivePattern> analyzeNgramRepetition(String normalizedText, String sttText,
            List<SlideTransition> slideTransitions,
            List<WhisperSegment> segments,
            List<String> slideSttTexts) {
        // 2-gram과 3-gram 모두 생성
        List<String> ngrams2 = TextAnalysisUtils.komoranMeaningfulNGrams(normalizedText, 2);
        List<String> ngrams3 = TextAnalysisUtils.komoranMeaningfulNGrams(normalizedText, 3);

        // 빈도 계산
        Map<String, Long> freq2 = new HashMap<>();
        Map<String, Long> freq3 = new HashMap<>();

        for (String ng : ngrams3) {
            freq3.put(ng, freq3.getOrDefault(ng, 0L) + 1);
        }

        for (String ng : ngrams2) {
            freq2.put(ng, freq2.getOrDefault(ng, 0L) + 1);
        }

        // 개선된 중복 제거: 2-gram이 3-gram의 prefix일 때, 3-gram 빈도가 2-gram 빈도 이상이면 2-gram 숨김
        Set<String> hide2 = new HashSet<>();
        for (Map.Entry<String, Long> entry : freq2.entrySet()) {
            String g2 = entry.getKey();
            long c2 = entry.getValue();

            // g2가 prefix로 쓰인 3-gram들의 최대 빈도
            long maxC3 = freq3.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(g2 + " "))
                    .mapToLong(Map.Entry::getValue)
                    .max()
                    .orElse(0L);

            if (maxC3 >= c2) {
                hide2.add(g2);
            }
        }

        // 모든 n-gram 병합 (숨기지 않은 2-gram + 모든 3-gram)
        Map<String, Long> ngramFreq = new HashMap<>();
        ngramFreq.putAll(freq3);
        freq2.entrySet().stream()
                .filter(e -> !hide2.contains(e.getKey()))
                .forEach(e -> ngramFreq.put(e.getKey(), e.getValue()));

        // 키워드 추출
        List<String> norms = TextAnalysisUtils.tokenizeKomoranNorms(normalizedText);
        Set<String> keywords = TextAnalysisUtils.extractKeywordsKomoran(norms, MIN_KEYWORD_FREQUENCY);

        // 추가: 원문 스팬 수집을 위해 KomoranAnalyzer의 NormToken 사용 (원문 sttText 기준)
        List<KomoranAnalyzer.NormToken> normTokensForOffsets = KomoranAnalyzer
                .tokenizeForRepeatWithSpans(sttText == null ? normalizedText : sttText);

        // If slideSttTexts not provided but slideTransitions+segments available, try to
        // compute
        if ((slideSttTexts == null || slideSttTexts.isEmpty()) && segments != null
                && slideTransitions != null && !slideTransitions.isEmpty()) {
            try {
                Map<Integer, String> mapped = mapTextToSlides(sttText == null ? normalizedText : sttText,
                        slideTransitions, segments);
                if (mapped != null && !mapped.isEmpty()) {
                    int max = mapped.keySet().stream().max(Integer::compareTo).orElse(0);
                    List<String> list = new ArrayList<>(Collections.nCopies(max, ""));
                    for (Map.Entry<Integer, String> me : mapped.entrySet()) {
                        int idx = me.getKey() - 1;
                        if (idx >= 0 && idx < list.size())
                            list.set(idx, me.getValue());
                    }
                    slideSttTexts = list;
                    log.debug("Computed slideSttTexts for ngram mapping from segments/transitions: size={}",
                            slideSttTexts.size());
                }
            } catch (Exception ex) {
                log.debug("Failed to compute slideSttTexts for ngram mapping: {}", ex.getMessage());
            }
        }

        // compute slideStartIndices if slideSttTexts provided
        List<Integer> slideStartIndices = null;
        if (slideSttTexts != null && !slideSttTexts.isEmpty()) {
            try {
                slideStartIndices = slideSegmentExtractor
                        .computeSlideStartOffsets(sttText == null ? normalizedText : sttText, slideSttTexts);
            } catch (Exception ex) {
                log.debug("Failed to compute slide start indices for ngram mapping: {}", ex.getMessage());
                slideStartIndices = null;
            }
        }

        // ngram -> Offsets mapping
        Map<String, List<Offset>> ngramOffsets = new HashMap<>();

        // collect n-gram Offsets (2~3) using sttText-based norm tokens
        for (int n = 2; n <= 3; n++) {
            if (normTokensForOffsets.size() < n)
                continue;
            for (int i = 0; i <= normTokensForOffsets.size() - n; i++) {
                List<KomoranAnalyzer.NormToken> window = normTokensForOffsets.subList(i, i + n);
                String key = window.stream().map(t -> t.norm).collect(Collectors.joining(" "));

                // skip self-dup for 2-gram
                if (n == 2) {
                    String[] parts = key.split(" ");
                    if (parts.length == 2 && parts[0].equals(parts[1]))
                        continue;
                }

                int begin = window.get(0).begin;
                int end = window.get(window.size() - 1).end;
                String textExcerpt = "";
                try {
                    String base = sttText == null ? normalizedText : sttText;
                    textExcerpt = base.substring(Math.max(0, begin), Math.min(base.length(), end));
                } catch (Exception ex) {
                    textExcerpt = key;
                }

                Integer mappedSlide = null;
                if (slideStartIndices != null && slideSttTexts != null) {
                    for (int si = 0; si < slideStartIndices.size(); si++) {
                        Integer start = slideStartIndices.get(si);
                        if (start == null || start < 0)
                            continue;
                        String slideText = slideSttTexts.get(si);
                        int slideLen = slideText != null ? slideText.length() : 0;
                        int slideEndGlobal = start + slideLen;
                        if (begin >= start && begin < slideEndGlobal) {
                            mappedSlide = si + 1;
                            break;
                        }
                    }
                }

                Offset occ = Offset.builder()
                        .begin(begin)
                        .end(end)
                        .slideIndex(mappedSlide)
                        .text(textExcerpt)
                        .build();

                ngramOffsets.computeIfAbsent(key, k -> new ArrayList<>()).add(occ);
            }
        }

        // build final list filtering by frequency and keywords
        return ngramFreq.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_NGRAM_REPETITION)
                .filter(e -> {
                    String[] tokens = e.getKey().split(" ");
                    for (String token : tokens) {
                        if (!keywords.contains(token)) {
                            return true;
                        }
                    }
                    return false;
                })
                .map(e -> RepetitivePattern.builder()
                        .pattern(e.getKey())
                        .count(e.getValue().intValue())
                        .type(e.getKey().split(" ").length + "-GRAM")
                        .Offsets(ngramOffsets.getOrDefault(e.getKey(), Collections.emptyList()))
                        .build())
                .sorted(Comparator.comparingInt(RepetitivePattern::getCount).reversed())
                .collect(Collectors.toList());
    }

    // ========== Level 3: 문장 유사도 ==========

    /**
     * 🎯 슬라이드 정보 활용한 적응형 유사도 분석
     * - 슬라이드 있음: 같은/인접 슬라이드만 비교
     * - 슬라이드 없음: Window 기반 비교
     */
    private List<RepetitiveSentencePair> analyzeSentenceSimilarity(
            String sttText,
            List<String> sentences,
            Set<String> keywords,
            List<SlideTransition> transitions,
            List<WhisperSegment> segments) {

        if (transitions != null && !transitions.isEmpty()) {
            return detectSimilarWithSlides(sttText, sentences, keywords, transitions, segments);
        } else {
            // transition 없으면 L3 문장 유사도 분석 스킵 (프로덕션 정책)
            log.info("  • L3: No transitions provided, skipping sentence similarity analysis");
            return Collections.emptyList();
        }
    }

    /**
     * 슬라이드 기반 유사도 분석 (인접 슬라이드만 비교)
     */
    private List<RepetitiveSentencePair> detectSimilarWithSlides(
            String sttText,
            List<String> sentences,
            Set<String> keywords,
            List<SlideTransition> transitions,
            List<WhisperSegment> segments) {

        // 각 문장 → 슬라이드 번호 매핑
        List<Integer> sentenceToSlide = SlideSentenceMapper.mapSentencesToSlides(
                sttText, sentences, transitions, segments);

        PrecomputedSentences pre = precomputeSentenceTokens(sentences, keywords);
        List<RepetitiveSentencePair> pairs = new ArrayList<>();

        for (int i = 0; i < sentences.size(); i++) {
            for (int j = i + 1; j < sentences.size(); j++) {

                // 🎯 슬라이드 필터: 같은 슬라이드 or 인접 슬라이드만
                int slideI = sentenceToSlide.get(i);
                int slideJ = sentenceToSlide.get(j);
                if (slideI >= 0 && slideJ >= 0 && Math.abs(slideI - slideJ) > SLIDE_NEIGHBORS) {
                    continue;
                }

                // 기존 필터 로직
                if (Math.abs(i - j) > SIMILAR_SENTENCES_WINDOW_LIMIT)
                    continue;

                String s1 = pre.cleaned[i];
                String s2 = pre.cleaned[j];

                if (s1.split("\\s+").length < 3 || s2.split("\\s+").length < 3)
                    continue;

                double jaccard = TextAnalysisUtils.jaccardSimilarity(
                        pre.tokenSets.get(i), pre.tokenSets.get(j));
                if (jaccard < SIMILARITY_THRESHOLD)
                    continue;

                double similarity = TextAnalysisUtils.calculateSemanticSimilarity(s1, s2);
                if (similarity >= SIMILARITY_THRESHOLD) {
                    pairs.add(RepetitiveSentencePair.builder()
                            .sentence1(sentences.get(i))
                            .sentence2(sentences.get(j))
                            .similarity(similarity)
                            .build());
                }
            }
        }

        return pairs.stream()
                .sorted(Comparator.comparingDouble(RepetitiveSentencePair::getSimilarity).reversed())
                .limit(MAX_SIMILAR_SENTENCES)
                .collect(Collectors.toList());
    }

    // ========== 헬퍼 메서드 ==========

    private PreprocessResult preprocessStt(String sttText, List<WhisperSegment> segments,
            Set<String> presentationKeywords, List<String> slideScripts) {
        if (sttText == null || sttText.trim().isEmpty()) {
            log.warn("STT text is empty");
            return new PreprocessResult(null, Collections.emptyList(), Collections.emptySet());
        }

        List<String> sentences;
        // 1. segments가 있으면 각 segment를 문장으로 사용
        if (segments != null && !segments.isEmpty()) {
            sentences = segments.stream()
                    .map(WhisperSegment::getText)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } else {
            // 2. fallback: 종결어미/접속사 기반 문장 분리 (유틸리티로 이동)
            sentences = TextAnalysisUtils.tokenizeKoreanSentences(sttText);
        }
        // 3. 각 문장별 정규화
        List<String> normalizedSentences = sentences.stream()
                .map(TextAnalysisUtils::normalizeText)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        // 4. 전체 정규화 텍스트 (분석용)
        String normalized = String.join(" ", normalizedSentences);

        // Debug logging
        log.info("  • Original sentences: {}, Normalized sentences: {}", sentences.size(), normalizedSentences.size());

        if (normalizedSentences.size() < 2) {
            log.info("Less than 2 sentences: analysis not possible");
            return new PreprocessResult(normalized, Collections.emptyList(), Collections.emptySet());
        }

        // 외부에서 받은 키워드 사용 우선
        Set<String> keywords;
        if (slideScripts != null && !slideScripts.isEmpty()) {
            // slideScripts가 주어지면 대본 기반으로 top-N 키워드 추출
            int topN = 10; // ScriptAccuracyService.TOP_SCRIPT_KEYWORDS와 동일한 값
            List<String> allWords = new ArrayList<>();
            for (String s : slideScripts) {
                if (s == null)
                    continue;
                String norm = TextAnalysisUtils.normalizeText(s);
                List<String> toks = TextAnalysisUtils.tokenizeKomoran(norm);
                if (toks != null && !toks.isEmpty())
                    allWords.addAll(toks);
            }
            List<String> topKeywords = TextAnalysisUtils.extractTopKeywordsKomoran(allWords, topN);
            keywords = new LinkedHashSet<>(topKeywords);
            log.info("  • Derived presentation keywords from slideScripts (top {}): {}", topN, keywords);
        } else {
            keywords = Collections.emptySet();
            log.warn(
                    "  • No slideScripts provided: presentation keywords set to empty. Provide slideScripts to derive presentation keywords.");
        }

        return new PreprocessResult(normalized, normalizedSentences, keywords);
    }

    private PrecomputedSentences precomputeSentenceTokens(List<String> sentences, Set<String> keywords) {
        int n = sentences.size();
        String[] cleaned = new String[n];
        List<Set<String>> tokenSets = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            String noKeywords = removeKeywords(sentences.get(i), keywords);
            cleaned[i] = noKeywords;

            List<String> tokens = TextAnalysisUtils.tokenizeKomoranNorms(
                    TextAnalysisUtils.normalizeText(noKeywords));
            tokenSets.add(new HashSet<>(tokens));
        }

        return new PrecomputedSentences(cleaned, tokenSets);
    }

    private String removeKeywords(String sentence, Set<String> keywords) {
        String normalized = TextAnalysisUtils.normalizeText(sentence);
        List<String> words = TextAnalysisUtils.tokenizeKomoranNorms(normalized);

        return words.stream()
                .filter(w -> !keywords.contains(w))
                .collect(Collectors.joining(" "));
    }

    private int calculateScore(
            List<WordRepetition> level1,
            List<SlideRepetition> level2,
            List<RepetitivePattern> level3Ngram,
            List<RepetitiveSentencePair> level3Similar) {

        int score = 100;
        score -= level1.size() * 3;
        score -= level2.size() * 4;
        score -= level3Ngram.size() * 5;
        score -= level3Similar.size() * 10;
        return Math.max(0, score);
    }

    // ========== 내부 클래스 ==========

    private static class PreprocessResult {
        final String normalizedText;
        final List<String> sentences;
        final Set<String> presentationKeywords;

        PreprocessResult(String normalizedText, List<String> sentences, Set<String> presentationKeywords) {
            this.normalizedText = normalizedText;
            this.sentences = sentences;
            this.presentationKeywords = presentationKeywords;
        }

        boolean isValid() {
            return sentences != null && sentences.size() >= 2;
        }
    }

    private static class PrecomputedSentences {
        final String[] cleaned;
        final List<Set<String>> tokenSets;

        PrecomputedSentences(String[] cleaned, List<Set<String>> tokenSets) {
            this.cleaned = cleaned;
            this.tokenSets = tokenSets;
        }
    }

    // ========== DTO ==========

    @Getter
    @Builder
    @Schema(description = "Level 1: 단어 반복 분석 결과")
    public static class WordRepetition {
        @Schema(description = "반복된 단어", example = "그러니까")
        private final String word;

        @Schema(description = "반복 횟수", example = "5")
        private final int count;

        @Schema(description = "단어 발생 위치 목록(문자 인덱스, 프론트 하이라이트용)")
        private final List<Offset> Offsets;
    }

    @Getter
    @Builder
    @Schema(description = "토큰 발생 위치 정보 (하이라이트용)")
    public static class Offset {
        @Schema(description = "원문 기준 시작 인덱스 (inclusive)", example = "15")
        private final int begin;

        @Schema(description = "원문 기준 끝 인덱스 (exclusive, Komoran endIndex)", example = "18")
        private final int end;

        @Schema(description = "(선택) 슬라이드 인덱스 - 테스트/매핑 시 제공 가능", example = "2")
        private final Integer slideIndex;

        @Schema(description = "해당하는 stt text 원문", example = "그러니까")
        private final String text;
    }

    @Getter
    @Builder
    @Schema(description = "슬라이드 내 반복 패턴 (Level 2 분석 결과)")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SlideRepetition {
        @Schema(description = "반복된 패턴 (2-gram)", example = "발표 준비")
        private final String pattern;

        @Schema(description = "패턴이 발견된 슬라이드 번호 목록", example = "[1, 3, 5]")
        private final List<Integer> slideIndices;

        @Schema(description = "(단일) 패턴이 발견된 슬라이드 번호 (intra-slide 용으로 명시적 제공)", example = "1")
        private final Integer slideIndex;

        @Schema(description = "패턴의 범위: INTRA_SLIDE(슬라이드 내부 반복) 또는 CROSS_SLIDE(슬라이드 간 중복)", example = "INTRA_SLIDE")
        private final String scope;

        @Schema(description = "패턴 반복 횟수", example = "3")
        private final int count;

        @Schema(description = "패턴의 발생 위치 목록 (하이라이트용)")
        private final List<Offset> offsets;
    }

    @Getter
    @Builder
    @Schema(description = "Level 3: N-gram 반복 패턴")
    public static class RepetitivePattern {
        @Schema(description = "반복된 패턴", example = "그래서 제가")
        private final String pattern;

        @Schema(description = "반복 횟수", example = "4")
        private final int count;

        @Schema(description = "패턴 유형", example = "trigram")
        private final String type;

        @Schema(description = "패턴 발생 위치 목록 (원문 기준, 하이라이트용)")
        private final List<Offset> Offsets;
    }

    @Getter
    @Builder
    @Schema(description = "Level 3: 유사한 문장 쌍")
    public static class RepetitiveSentencePair {
        @Schema(description = "첫 번째 문장", example = "오늘 발표를 시작하겠습니다")
        private final String sentence1;

        @Schema(description = "두 번째 문장", example = "지금 발표를 진행하겠습니다")
        private final String sentence2;

        @Schema(description = "유사도 점수 (0.0-1.0)", example = "0.85")
        private final double similarity;
    }

    @Getter
    @Builder
    @Schema(description = "반복 분석 최종 결과")
    public static class RepetitionAnalysisResult {
        @Schema(description = "반복 점수 (100점 만점, 낮을수록 좋음)", example = "85")
        private final int repetitionScore;

        @Schema(description = "Level 1: 단어 반복 목록")
        private final List<WordRepetition> wordRepetitions;

        @Schema(description = "Level 2: 슬라이드 간 반복 패턴 목록")
        private final List<SlideRepetition> slideRepetitions;

        @Schema(description = "Level 3: N-gram 반복 패턴 목록")
        private final List<RepetitivePattern> nGramPatterns;

        @Schema(description = "Level 3: 유사한 문장 쌍 목록")
        private final List<RepetitiveSentencePair> similarSentencePairs;

        @Schema(description = "발표 자료 키워드 (외부 제공, 반복 분석 시 제외용)")
        private final Set<String> presentationKeywords;

        @Schema(description = "총 문장 수", example = "15")
        private final int totalSentences;

        @Schema(description = "분석 성공 여부", example = "true")
        private final boolean success;

        @Schema(description = "오류 메시지 (실패 시)")
        private final String errorMessage;

        public static RepetitionAnalysisResult failed(String errorMessage) {
            return RepetitionAnalysisResult.builder()
                    .repetitionScore(100)
                    .wordRepetitions(new ArrayList<>())
                    .slideRepetitions(new ArrayList<>())
                    .nGramPatterns(new ArrayList<>())
                    .similarSentencePairs(new ArrayList<>())
                    .totalSentences(0)
                    .success(false)
                    .errorMessage(errorMessage)
                    .build();
        }
    }
}
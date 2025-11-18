package com.pres.pres_server.service.analyse;

import com.pres.pres_server.service.analyse.RepetitiveTextAnalysisService.*;
import com.pres.pres_server.service.analyse.dto.SlideTransition;
import com.pres.pres_server.service.analyse.dto.WhisperSegment;
import com.pres.pres_server.service.analyse.utils.KomoranAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


/**
 * Swagger / 개발 테스트 전용 서비스
 * - 프론트엔드 transition 없이 독립적으로 반복 분석 수행
 * - window 기반으로 자체적으로 슬라이드 추정 및 분석
 * - 프로덕션 코드(RepetitiveTextAnalysisService)와 완전 분리
 */
@Slf4j
@Service
public class TestRepetitiveService {

    // ========== 설정 상수 ==========
    private static final Set<String> FILLER_WORDS = Set.of(
            "음", "어", "아", "뭐지",
            "그러니까", "그 다음에", "약간", "되게", "엄청", "진짜",
            "막", "이제", "그냥", "좀", "완전", "정말");

    private static final int MIN_REPETITION_COUNT = 2;
    private static final int MIN_KEYWORD_FREQUENCY = 2;
    private static final int SIMILAR_SENTENCES_WINDOW_LIMIT = 200;
    private static final double SIMILAR_SENTENCES_JACCARD_THRESHOLD = 0.15;
    private static final double SIMILARITY_THRESHOLD = 0.7;
    private static final int MAX_SIMILAR_SENTENCES = 10;
    // NGRAM_SIZE constant intentionally unused here (explicit sizes used in code)
    private static final int MIN_NGRAM_REPETITION = 2;

    // ========== Public API (Swagger 테스트용) ==========

    /**
     * Swagger 테스트용 전체 반복 분석 (transition 없이 독립적으로 동작)
     * - window 기반으로 슬라이드 자동 추정
     * - 프론트엔드 없이 개발/테스트 가능
     */
    public RepetitionAnalysisResult analyzeRepetitionForTest(String sttText) {
        log.info("▶ [TEST] Repetition analysis started (no transitions) - Text: {}chars",
                sttText != null ? sttText.length() : 0);

        if (sttText == null || sttText.trim().isEmpty()) {
            return RepetitionAnalysisResult.failed("STT 텍스트가 비어있습니다.");
        }

        // ⭐ 테스트에서는 키워드 없음 (발표 자료가 없으므로)
        Set<String> keywords = Collections.emptySet();

        // 3. 자체 추정 슬라이드 수 (window 기반) — word Offsets에 슬라이드 인덱스를 채우기 위해 먼저 계산
        int estimatedSlides = 3;
        // 1) 문장 분리/정규화 전부 제거
        // 2) Komoran 정규화 토큰 + 절대 오프셋 한 번에 생성
        List<KomoranAnalyzer.NormToken> nts = KomoranAnalyzer.tokenizeForRepeatWithSpans(sttText);

        // === Level 1: 단어 반복 (절대 오프셋 유지) ===
        Map<String, List<KomoranAnalyzer.NormToken>> byWord = new LinkedHashMap<>();
        for (var nt : nts) {
            // 의미 토큰만: posHead N/V/S
            if (!("N".equals(nt.posHead) || "V".equals(nt.posHead) || "S".equals(nt.posHead))) continue;
            byWord.computeIfAbsent(nt.norm, k -> new ArrayList<>()).add(nt);
        }
        int minWordCount = 3;
        List<WordRepetition> wordRepetitions = byWord.entrySet().stream()
                .filter(e -> e.getValue().size() >= minWordCount)
                .sorted((a,b)->Integer.compare(b.getValue().size(), a.getValue().size()))
                .map(e -> WordRepetition.builder()
                        .word(e.getKey())
                        .count(e.getValue().size())
                        .Offsets(
                                e.getValue().stream().map((nt ->{
                                    int extEnd = extendToWordBoundary(sttText, nt.end);
                                    return RepetitiveTextAnalysisService.Offset.builder()
                                        .begin(nt.begin)
                                        .end(extEnd) // ★ 어절 경계 확장
                                        .slideIndex(null) // 테스트 모드이므로 없음
                                        .text(sttText.substring(nt.begin, Math.min(extEnd, sttText.length())))
                                        .build();}
                                )
                                ).toList()
                        ).build()
                ).toList();


        // === Level 3-N: N-gram (2~3) ===
        record PM(KomoranAnalyzer.NormToken first, KomoranAnalyzer.NormToken last, int n) {}
        Map<String, List<PM>> g2 = new LinkedHashMap<>();
        Map<String, List<PM>> g3 = new LinkedHashMap<>();

        //2gram
        for (int i = 0; i + 1 < nts.size(); i++) {
            var a = nts.get(i); var b = nts.get(i + 1);
            if (!isMeaningful(a) || !isMeaningful(b)) continue;
            String key = a.norm + " " + b.norm;
            g2.computeIfAbsent(key, k -> new ArrayList<>()).add(new PM(a, b, 2));
        }
        //3gram
        for (int i = 0; i + 2 < nts.size(); i++) {
            var a = nts.get(i); var b = nts.get(i + 1); var c = nts.get(i + 2);
            if (!isMeaningful(a) || !isMeaningful(b) || !isMeaningful(c)) continue;
            String key = a.norm + " " + b.norm + " " + c.norm;
            g3.computeIfAbsent(key, k -> new ArrayList<>()).add(new PM(a, c, 3));
        }

        // 빈도 필터 및 2-gram 중복 억제
        int minNgram = 3;
        Map<String, List<PM>> rep2 = g2.entrySet().stream()
                .filter(e -> e.getValue().size() >= minNgram)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        Map<String, List<PM>> rep3 = g3.entrySet().stream()
                .filter(e -> e.getValue().size() >= minNgram)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        Set<String> hide2 = new HashSet<>();
        for (String k2 : rep2.keySet()) {
            int c2 = rep2.get(k2).size();
            int maxC3 = rep3.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(k2 + " "))
                    .mapToInt(e -> e.getValue().size())
                    .max().orElse(0);
            if (maxC3 >= c2) hide2.add(k2);
        }
        List<RepetitivePattern> ngramRepetitions = new ArrayList<>();

        rep3.forEach((key, list) -> {
            var occ = list.stream()
                    .map(pm -> {
                        int extEnd = extendToWordBoundary(sttText, pm.last().end);
                        return RepetitiveTextAnalysisService.Offset.builder()
                            .begin(pm.first().begin)
                            .end(extendToWordBoundary(sttText, pm.last().end))
                            .slideIndex(null)
                            .text(sttText.substring(pm.first().begin, Math.min(extEnd, sttText.length()))) // 필요 시 원문 substring 넣기
                            .build();}
                        )
                    .toList();

            ngramRepetitions.add(
                    RepetitivePattern.builder()
                            .pattern(key)
                            .type("3-GRAM")
                            .count(list.size())
                            .Offsets(occ)
                            .build()
            );
        });


        rep2.entrySet().stream()
                .filter(e -> !hide2.contains(e.getKey()))
                .filter(e -> {
                    String[] p = e.getKey().split(" ");
                    return !(p.length == 2 && p[0].equals(p[1]));
                })
                .forEach(e -> {
                    var occ = e.getValue().stream()
                            .map(pm ->
                            {
                                int extEnd = extendToWordBoundary(sttText, pm.last().end);
                                return RepetitiveTextAnalysisService.Offset.builder()
                                    .begin(pm.first().begin)
                                    .end(extendToWordBoundary(sttText, pm.last().end))
                                    .slideIndex(null)
                                    .text(sttText.substring(pm.first().begin, Math.min(extEnd, sttText.length())))
                                    .build();})
                            .toList();

                    ngramRepetitions.add(
                            RepetitivePattern.builder()
                                    .pattern(e.getKey())
                                    .type("2-GRAM")
                                    .count(e.getValue().size())
                                    .Offsets(occ)
                                    .build()
                    );
                });



//        // 필요하면 count desc, n desc 정렬
//        ngramRepetitions.sort((a,b)->{
//            int c = Integer.compare(b.getCount(), a.getCount());
//            if (c!=0) return c;
//            return Integer.compare(b.getN(), a.getN());
//        });

        // --- Debug data: mapped slide summaries (window-based) ---
//        List<String> mappedSummaries = new ArrayList<>();
//        try {
//            int sentencesPerSlide = Math.max(1, sentences.size() / Math.max(1, estimatedSlides));
//            for (int i = 0; i < estimatedSlides; i++) {
//                int start = i * sentencesPerSlide;
//                int end = (i == estimatedSlides - 1) ? sentences.size()
//                        : Math.min((i + 1) * sentencesPerSlide, sentences.size());
//                if (start < sentences.size()) {
//                    String summary = String.join(" ", sentences.subList(start, end)).trim();
//                    if (summary.length() > 200) {
//                        summary = summary.substring(0, 200) + "...";
//                    }
//                    mappedSummaries.add(summary);
//                } else {
//                    mappedSummaries.add("");
//                }
//            }
//        } catch (Exception ex) {
//            log.warn("Failed to build mappedSummaries for test output", ex);
//        }

        // --- Token stats ---
//        Map<String, Object> tokenStats = new HashMap<>();
//        List<String> allTokens = sentences.stream()
//                .flatMap(s -> TextAnalysisUtils.tokenizeKomoranNorms(s).stream())
//                .collect(Collectors.toList());
//        Map<String, Long> freq = allTokens.stream()
//                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));
//        tokenStats.put("totalTokens", allTokens.size());
//        tokenStats.put("uniqueTokens", freq.size());
//        tokenStats.put("topWords", freq.entrySet().stream()
//                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
//                .limit(10)
//                .map(e -> e.getKey() + "=" + e.getValue())
//                .collect(Collectors.toList()));

        // 점수는 대충 유지
        int score = Math.max(0, 100 - wordRepetitions.size()*3 - ngramRepetitions.size()*5);


        return RepetitionAnalysisResult.builder()
                .repetitionScore(score)
                .wordRepetitions(wordRepetitions)
                .nGramPatterns(ngramRepetitions)
                .totalSentences(0)
                .success(true)
                .build();
    }

    private static int extendToWordBoundary(String text, int fromIdx) {
        int endIdx = fromIdx;
        while (endIdx < text.length()) {
            char c = text.charAt(endIdx);
            if (!Character.isLetterOrDigit(c)) break;
            endIdx++;
        }
        return endIdx;
    }

    private boolean isMeaningful(KomoranAnalyzer.NormToken nt) {
        return "N".equals(nt.posHead) || "V".equals(nt.posHead) || "S".equals(nt.posHead);
    }

    private List<WordRepetition> analyzeWordRepetitionTest(String sttText, List<String> sentences,
            List<String> normalizedSentences, Set<String> keywords, int estimatedSlides) {

        // 1) 프로덕션과 동일: 정규화된 문장들에서 토큰 추출하여 단어 빈도 계산
        List<String> tokens = new ArrayList<>();
        for (String sentence : normalizedSentences) {
            tokens.addAll(TextAnalysisUtils.tokenizeKomoranNorms(sentence));
        }

        Map<String, Long> wordFreq = tokens.stream()
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));

        // 2) 원문 기준 위치(Offsets) 추출 - KomoranAnalyzer 사용
        List<com.pres.pres_server.service.analyse.utils.KomoranAnalyzer.NormToken> normTokens = com.pres.pres_server.service.analyse.utils.KomoranAnalyzer
                .tokenizeForRepeatWithSpans(sttText);

        // 3) 문장별 문자 오프셋 계산 (간단한 indexOf 기반 매핑)
        List<int[]> sentenceRanges = new ArrayList<>();
        int searchFrom = 0;
        for (String s : sentences) {
            if (s == null) {
                sentenceRanges.add(new int[] { -1, -1 });
                continue;
            }
            int idx = sttText.indexOf(s, searchFrom);
            if (idx < 0) {
                // fallback: try trimming and searching again
                idx = sttText.indexOf(s.trim(), searchFrom);
            }
            if (idx < 0) {
                // unknown: mark as not found
                sentenceRanges.add(new int[] { -1, -1 });
            } else {
                sentenceRanges.add(new int[] { idx, idx + s.length() });
                searchFrom = idx + s.length();
            }
        }

        int sentencesPerSlide = Math.max(1, sentences.size() / Math.max(1, estimatedSlides));

        Map<String, List<RepetitiveTextAnalysisService.Offset>> OffsetsMap = new HashMap<>();
        for (com.pres.pres_server.service.analyse.utils.KomoranAnalyzer.NormToken nt : normTokens) {
            int tokenBegin = nt.begin;
            Integer slideIdx = null;
            // find sentence index
            for (int si = 0; si < sentenceRanges.size(); si++) {
                int[] range = sentenceRanges.get(si);
                if (range[0] >= 0 && tokenBegin >= range[0] && tokenBegin < range[1]) {
                    slideIdx = Math.min(estimatedSlides - 1, si / sentencesPerSlide);
                    break;
                }
            }

            String text;
            try {
                text = sttText.substring(nt.begin, Math.min(nt.end, sttText.length()));
            } catch (Exception ex) {
                text = nt.norm;
            }

            OffsetsMap.computeIfAbsent(nt.norm, k -> new ArrayList<>())
                    .add(RepetitiveTextAnalysisService.Offset.builder()
                            .begin(nt.begin)
                            .end(nt.end)
                            .text(text)
                            .slideIndex(slideIdx)
                            .build());
        }

        // 4) 결과 구성: Offsets 포함
        return wordFreq.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_REPETITION_COUNT)
                .filter(e -> !keywords.contains(e.getKey()))
                .filter(e -> !FILLER_WORDS.contains(e.getKey()))
                .map(e -> RepetitiveTextAnalysisService.WordRepetition.builder()
                        .word(e.getKey())
                        .count(e.getValue().intValue())
                        .Offsets(OffsetsMap.getOrDefault(e.getKey(), Collections.emptyList()))
                        .build())
                .sorted(Comparator.comparingInt(RepetitiveTextAnalysisService.WordRepetition::getCount).reversed())
                .collect(Collectors.toList());
    }

    // ========== Level 2: Window 기반 슬라이드 2-gram 반복 ==========

    private List<SlideRepetition> analyzeSlideRepetitionTest(String sttText, List<String> sentences,
            int estimatedSlides) {
        // Window로 문장 분할 (간단 추정)
        int sentencesPerSlide = Math.max(1, sentences.size() / estimatedSlides);
        Map<Integer, String> slideTextMap = new HashMap<>();

        for (int i = 0; i < estimatedSlides; i++) {
            int start = i * sentencesPerSlide;
            int end = (i == estimatedSlides - 1) ? sentences.size()
                    : Math.min((i + 1) * sentencesPerSlide, sentences.size());
            if (start < sentences.size()) {
                slideTextMap.put(i, String.join(" ", sentences.subList(start, end)));
            }
        }

        List<SlideRepetition> out = new ArrayList<>();

        // 각 슬라이드 내부에서 2-gram을 추출하고, 동일 슬라이드 내에서 2회 이상 등장하는 패턴만 추가
        for (Map.Entry<Integer, String> entry : slideTextMap.entrySet()) {
            int slideNum = entry.getKey();
            String slideText = entry.getValue();

            if (slideText == null || slideText.trim().isEmpty())
                continue;

            String normalized = TextAnalysisUtils.normalizeText(slideText);
            List<String> grams = TextAnalysisUtils.komoranMeaningfulNGrams(normalized, 2, "slide:" + slideNum);

            // 자기중복(A A) 제거
            List<String> filtered = grams.stream()
                    .filter(g -> {
                        String[] p = g.split(" ");
                        return p.length == 2 && !p[0].equals(p[1]);
                    })
                    .toList();

            Map<String, Long> freq = filtered.stream()
                    .collect(Collectors.groupingBy(g -> g, Collectors.counting()));

            // 같은 슬라이드 내에서 2회 이상
            freq.entrySet().stream()
                    .filter(en -> en.getValue() >= 2)
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .forEach(en -> out.add(SlideRepetition.builder()
                            .pattern(en.getKey())
                            .slideIndices(Collections.singletonList(slideNum)) // 단일 슬라이드
                            .slideIndex(slideNum)
                            .scope("INTRA_SLIDE")
                            .count(en.getValue().intValue()) // 해당 슬라이드 내 출현 횟수
                            .build()));
        }

        out.sort(Comparator.comparingInt(SlideRepetition::getCount).reversed());
        return out;
    }

    // ========== Level 3-N: N-gram 반복 ==========

    private List<RepetitivePattern> analyzeNgramRepetitionTest(String normalizedText, Set<String> keywords) {

        // 2-gram과 3-gram 모두 생성
        List<String> ngrams2 = TextAnalysisUtils.komoranMeaningfulNGrams(normalizedText, 2);
        List<String> ngrams3 = TextAnalysisUtils.komoranMeaningfulNGrams(normalizedText, 3);

        // ⭐ 빈도 계산 (분리)
        Map<String, Long> freq2 = new HashMap<>();
        Map<String, Long> freq3 = new HashMap<>();

        // 3-gram 먼저 추가 (더 구체적)
        for (String ng : ngrams3) {
            freq3.put(ng, freq3.getOrDefault(ng, 0L) + 1);
        }

        // 2-gram 추가 (자기중복 A A 제거)
        List<String> grams2Filtered = ngrams2.stream()
                .filter(g -> {
                    String[] p = g.split(" ");
                    return p.length == 2 && !p[0].equals(p[1]);
                })
                .toList();

        for (String ng : grams2Filtered) {
            freq2.put(ng, freq2.getOrDefault(ng, 0L) + 1);
        }

        // ⭐ 개선된 중복 제거: 2-gram이 3-gram의 prefix일 때, 3-gram 빈도가 2-gram 빈도 이상이면 2-gram 숨김
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

        // ⭐ 키워드 추출 (테스트에서는 자동 추출, 프로덕션에서는 외부 제공)
        Set<String> extractedKeywords = keywords.isEmpty()
                ? TextAnalysisUtils.extractKeywordsKomoran(
                        TextAnalysisUtils.tokenizeKomoranNorms(normalizedText),
                        MIN_KEYWORD_FREQUENCY)
                : keywords;

    // collect Offsets using Komoran spans
        List<KomoranAnalyzer.NormToken> normTokens = KomoranAnalyzer.tokenizeForRepeatWithSpans(normalizedText);

        // build Offsets map for 2~3 grams
        Map<String, List<RepetitiveTextAnalysisService.Offset>> occMap = new HashMap<>();
        for (int n = 2; n <= 3; n++) {
            if (normTokens.size() < n) continue;
            for (int i = 0; i <= normTokens.size() - n; i++) {
                List<KomoranAnalyzer.NormToken> window = normTokens.subList(i, i + n);
                String key = window.stream().map(t -> t.norm).collect(Collectors.joining(" "));
                if (n == 2) {
                    String[] p = key.split(" ");
                    if (p.length == 2 && p[0].equals(p[1])) continue;
                }
                int begin = window.get(0).begin;
                int end = window.get(window.size() - 1).end;
                String text = normalizedText.substring(Math.max(0, begin), Math.min(normalizedText.length(), end));
                RepetitiveTextAnalysisService.Offset occ = RepetitiveTextAnalysisService.Offset.builder()
                        .begin(begin).end(end).slideIndex(null).text(text).build();
                occMap.computeIfAbsent(key, k -> new ArrayList<>()).add(occ);
            }
        }

        return ngramFreq.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_NGRAM_REPETITION)
                .filter(e -> {
                    String[] tokens = e.getKey().split(" ");
                    for (String token : tokens) {
                        if (!extractedKeywords.contains(token)) {
                            return true; // 키워드가 아닌 단어가 하나라도 있으면 포함
                        }
                    }
                    return false; // 모두 키워드면 제외
                })
                .map(e -> RepetitivePattern.builder()
                        .pattern(e.getKey())
                        .count(e.getValue().intValue())
                        .type(e.getKey().split(" ").length + "-GRAM")
                        .Offsets(occMap.getOrDefault(e.getKey(), Collections.emptyList()))
                        .build())
                .sorted(Comparator.comparingInt(RepetitivePattern::getCount).reversed())
                .collect(Collectors.toList());
    }

    // ========== 기존 헬퍼 메서드들 ==========

    /**
     * segments가 없을 때 사용되는 window 기반 매핑.
     * transitions가 없으면 전체 텍스트를 slide 0으로 매핑함.
     */
    public Map<Integer, String> mapWithFallback(String sttText, List<SlideTransition> transitions) {
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
            result.put(transitions.get(i).getSlideNumber(), slideTexts.get(i));
        }
        return result;
    }

    /**
     * window 기반 균등 분할 유틸 (정적으로도 사용 가능)
     */
    public static List<String> splitTextByTimestampsFallbackStatic(String sttText, List<SlideTransition> transitions) {
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

    /**
     * Window 기반 유사도 분석 (슬라이드 정보 없을 때)
     */
    public List<RepetitiveSentencePair> detectSimilarWithWindow(
            List<String> sentences,
            Set<String> keywords) {

        PrecomputedSentences pre = precomputeSentenceTokens(sentences, keywords);
        List<RepetitiveSentencePair> pairs = new ArrayList<>();

        for (int i = 0; i < sentences.size(); i++) {
            for (int j = i + 1; j < sentences.size(); j++) {

                if (Math.abs(i - j) > SIMILAR_SENTENCES_WINDOW_LIMIT)
                    continue;

                String s1 = pre.cleaned[i];
                String s2 = pre.cleaned[j];

                if (s1.split("\\s+").length < 3 || s2.split("\\s+").length < 3)
                    continue;

                double jaccard = TextAnalysisUtils.jaccardSimilarity(
                        pre.tokenSets.get(i), pre.tokenSets.get(j));
                if (jaccard < SIMILAR_SENTENCES_JACCARD_THRESHOLD)
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

    private static class PrecomputedSentences {
        final String[] cleaned;
        final List<Set<String>> tokenSets;

        PrecomputedSentences(String[] cleaned, List<Set<String>> tokenSets) {
            this.cleaned = cleaned;
            this.tokenSets = tokenSets;
        }
    }

    /**
     * 안전한 fallback 슬라이드 개수 추정
     * - segments가 존재하면 segment 수 기준
     * - 문장 수가 충분하면 문장 기반으로 슬라이드 수 추정
     * - 최종적으로 2 이상이면 L2를 실행할 수 있도록 반환
     */
    public int estimateFallbackSlideCount(String sttText, List<WhisperSegment> segments, int sentenceCount) {
        // 1) segments 기반 추정
        if (segments != null && !segments.isEmpty()) {
            int segs = segments.size();
            // 구간이 많으면 그 중 일부를 슬라이드로 본다 (적당히 나눔)
            return Math.min(Math.max(2, segs / 3), 10);
        }

        // 2) 문장 수 기반 추정: 문장당 3~5개를 묶어 슬라이드로 본다
        if (sentenceCount >= 4) {
            int est = Math.max(2, Math.min(10, sentenceCount / 4));
            return est;
        }

        // 3) 기본: 없음
        return 0;
    }

}

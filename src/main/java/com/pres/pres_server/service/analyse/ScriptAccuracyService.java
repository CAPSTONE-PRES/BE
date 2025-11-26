package com.pres.pres_server.service.analyse;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import com.pres.pres_server.service.analyse.TextOffset;

/**
 * 대본(큐카드)과 실제 발표 내용(STT)의 정확도를 분석하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptAccuracyService {

    private static final int TOP_MISSING_KEYWORDS = 50; // 저장/표시에 사용할 상위 누락 키워드 수
    private static final int TOP_SCRIPT_KEYWORDS = 10; // 스크립트 비교에 사용할 상위 키워드 수 (기본 10)

    /**
     * 슬라이드별 대본/발표 쌍에 대해 정확도 분석 수행
     * 
     * @param slideScripts  슬라이드별 대본 리스트
     * @param slideSttTexts 슬라이드별 STT 텍스트 리스트
     * @return 슬라이드별 정확도 분석 결과 리스트
     */
    public List<AccuracyAnalysisResult> analyzeAccuracyBySlides(List<String> slideScripts, List<String> slideSttTexts) {
        List<AccuracyAnalysisResult> results = new ArrayList<>();
        if (slideScripts == null || slideSttTexts == null || slideScripts.size() != slideSttTexts.size()) {
            log.warn("The number of script/STT pairs per slide does not match. Skipping analysis.");
            return results;
        }
        for (int i = 0; i < slideScripts.size(); i++) {
            String script = slideScripts.get(i);
            String stt = slideSttTexts.get(i);
            AccuracyAnalysisResult result = analyzeAccuracy(script, stt);
            results.add(result);
        }
        return results;
    }

    /**
     * 대본과 실제 발표 내용을 비교하여 정확도 분석
     * 
     * @param scriptContent 큐카드 대본 (전체 슬라이드 대본 합침)
     * @param sttText       실제 발표 STT 결과
     * @return 정확도 분석 결과
     */
    public AccuracyAnalysisResult analyzeAccuracy(String scriptContent, String sttText) {
        log.info("▶ Script accuracy analysis started - Script length: {}, STT length: {}",
                scriptContent.length(), sttText.length());

        if (scriptContent == null || scriptContent.trim().isEmpty()) {
            log.warn("Script is empty. Cannot perform accuracy analysis.");
            return createEmptyResult();
        }

        if (sttText == null || sttText.trim().isEmpty()) {
            log.warn("STT text is empty. Cannot perform accuracy analysis.");
            return createEmptyResult();
        }

        // 1. 텍스트 정규화 (공통 유틸 사용)
        String normalizedScript = TextAnalysisUtils.normalizeText(scriptContent);
        String normalizedStt = TextAnalysisUtils.normalizeText(sttText);

        // 2. 단어 단위로 분리
        List<String> scriptWords = TextAnalysisUtils.tokenizeKomoran(normalizedScript);
        List<String> sttWords = TextAnalysisUtils.tokenizeKomoran(normalizedStt);

        // 3. 주요 키워드 추출 (대본에서)
        final int MIN_KEYWORD_FREQUENCY = 3; // 최소 빈도수 (STT 측 추출에 사용)
        // 기존 scriptKeywords 추출 방식
        // Set<String> scriptKeywords =
        // TextAnalysisUtils.extractKeywordsKomoran(scriptWords, MIN_KEYWORD_FREQUENCY);

        // 스크립트 측은 상위 N개 키워드만 사용하여 점수 안정화 (짧은 문서 대비)
        List<String> topScriptKeywords = TextAnalysisUtils.extractTopKeywordsKomoran(scriptWords, TOP_SCRIPT_KEYWORDS);
        // 상위 키워드 목록을 집합으로 변환하여 매칭 계산에 사용
        Set<String> scriptKeywords = new LinkedHashSet<>(topScriptKeywords);
        Set<String> sttKeywords = TextAnalysisUtils.extractKeywordsKomoran(sttWords, MIN_KEYWORD_FREQUENCY);

        // 디버그: 상위 스크립트 키워드 로깅
        log.debug("Top {} script keywords: {}", TOP_SCRIPT_KEYWORDS, topScriptKeywords);

        // 4. 키워드 매칭률 계산
        double keywordMatchRate = TextAnalysisUtils.calculateKeywordMatchRate(scriptKeywords, sttKeywords);
        Set<String> missingKeywords = TextAnalysisUtils.findMissingKeywords(scriptKeywords, sttKeywords);

        int matchedCount = scriptKeywords.size() - missingKeywords.size();

        // 누락 키워드가 많을 경우 상위 TOP_N개만 저장/표시 (빈도 기준)
        List<String> limitedMissingKeywords = limitMissingKeywordsByFrequency(missingKeywords, scriptWords,
                TOP_MISSING_KEYWORDS);

        // 5. 의미론적 유사도 계산 (AI 우선, 폴백: Levenshtein)
        double semanticSimilarity = TextAnalysisUtils.calculateSemanticSimilarity(normalizedScript, normalizedStt);

        // 6. 최종 유사도 계산 (키워드 50% + 의미론적 유사도 50%) - 실험용 조정
        double finalSimilarity = (keywordMatchRate * 0.5) + (semanticSimilarity * 0.5);

        // 7. 점수 계산 (0~100)
        int accuracyScore = (int) Math.round(finalSimilarity * 100);

        log.info("✅ Accuracy analysis complete - Score: {}, Similarity: {}, Keyword match: {}/{}, AI: {}",
                accuracyScore, String.format("%.2f", finalSimilarity), matchedCount, scriptKeywords.size(),
                TextAnalysisUtils.isAIEnabled() ? "AI enabled" : "AI disabled");

        // compute offsets for the limited missing keywords (to avoid huge offsets
        // lists)
        List<TextOffset> offsets = new ArrayList<>();
        try {
            String normalizedScriptForOffsets = TextAnalysisUtils.normalizeText(scriptContent);
            for (String kw : limitedMissingKeywords) {
                if (kw == null || kw.isBlank())
                    continue;
                int idx = normalizedScriptForOffsets.indexOf(kw);
                while (idx >= 0) {
                    int end = Math.min(normalizedScriptForOffsets.length(), idx + kw.length());
                    offsets.add(new TextOffset(idx, end, 0, normalizedScriptForOffsets.substring(idx, end)));
                    idx = normalizedScriptForOffsets.indexOf(kw, idx + Math.max(1, kw.length()));
                }
            }
        } catch (Exception ignore) {
        }

        return AccuracyAnalysisResult.builder()
                .accuracyScore(accuracyScore)
                .scriptSimilarity(finalSimilarity)
                .keywordMatchRate(keywordMatchRate)
                .matchedKeywordCount(matchedCount)
                .totalKeywordCount(scriptKeywords.size())
                .missingKeywords(new ArrayList<>(limitedMissingKeywords))
                .offsets(offsets)
                .success(true)
                .build();
    }

    /**
     * 빈 결과 객체 생성 (분석 실패 시)
     */
    private AccuracyAnalysisResult createEmptyResult() {
        return AccuracyAnalysisResult.builder()
                .accuracyScore(0)
                .scriptSimilarity(0.0)
                .keywordMatchRate(0.0)
                .matchedKeywordCount(0)
                .totalKeywordCount(0)
                .missingKeywords(new ArrayList<>())
                .success(false)
                .build();
    }

    /**
     * 정확도 분석 결과 DTO
     */
    @Getter
    @Builder
    public static class AccuracyAnalysisResult {
        private final int accuracyScore; // 0~100 점수
        private final double scriptSimilarity; // 0.0~1.0 유사도
        private final double keywordMatchRate; // 키워드 매칭률
        private final int matchedKeywordCount; // 매칭된 키워드 개수
        private final int totalKeywordCount; // 전체 키워드 개수
        private final List<String> missingKeywords; // 누락된 키워드 목록
        // missing keyword offsets within the script text (begin/end indices relative to
        // scriptContent)
        private final List<TextOffset> offsets;
        private final boolean success; // 분석 성공 여부
        private final String errorMessage; // 실패 사유

        /**
         * 분석 실패/생략 시 기본값 반환
         */
        public static AccuracyAnalysisResult defaultResult(String errorMessage) {
            return AccuracyAnalysisResult.builder()
                    .accuracyScore(100) // 기본값: 만점 (불이익 없음)
                    .scriptSimilarity(1.0)
                    .keywordMatchRate(1.0)
                    .matchedKeywordCount(0)
                    .totalKeywordCount(0)
                    .missingKeywords(new ArrayList<>())
                    .success(false)
                    .errorMessage(errorMessage)
                    .build();
        }
    }

    /**
     * missingKeywords 집합을 스크립트 단어 빈도 기준으로 정렬해 상위 N개를 반환
     */
    private List<String> limitMissingKeywordsByFrequency(Set<String> missingKeywords, List<String> scriptWords,
            int topN) {
        if (missingKeywords == null || missingKeywords.isEmpty())
            return Collections.emptyList();
        if (scriptWords == null || scriptWords.isEmpty())
            return new ArrayList<>(missingKeywords).subList(0, Math.min(topN, missingKeywords.size()));

        Map<String, Integer> freq = new HashMap<>();
        for (String w : scriptWords)
            freq.put(w, freq.getOrDefault(w, 0) + 1);

        return missingKeywords.stream()
                .sorted((a, b) -> Integer.compare(freq.getOrDefault(b, 0), freq.getOrDefault(a, 0)))
                .limit(topN)
                .toList();
    }

    // Note: OffsetDto (com.pres.pres_server.dto.practice.OffsetDto) is used to
    // represent
    // missing-keyword offsets so other services can consume a single shared DTO.
}

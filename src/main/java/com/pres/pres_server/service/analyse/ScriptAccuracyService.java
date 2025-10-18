package com.pres.pres_server.service.analyse;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 대본(큐카드)과 실제 발표 내용(STT)의 정확도를 분석하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptAccuracyService {

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
        final int MIN_KEYWORD_FREQUENCY = 3; // 최소 빈도수
        Set<String> scriptKeywords = TextAnalysisUtils.extractKeywordsKomoran(scriptWords, MIN_KEYWORD_FREQUENCY);
        Set<String> sttKeywords = TextAnalysisUtils.extractKeywordsKomoran(sttWords, MIN_KEYWORD_FREQUENCY);

        // 4. 키워드 매칭률 계산
        double keywordMatchRate = TextAnalysisUtils.calculateKeywordMatchRate(scriptKeywords, sttKeywords);
        Set<String> missingKeywords = TextAnalysisUtils.findMissingKeywords(scriptKeywords, sttKeywords);

        int matchedCount = scriptKeywords.size() - missingKeywords.size();

        // 5. 의미론적 유사도 계산 (AI 우선, 폴백: Levenshtein)
        double semanticSimilarity = TextAnalysisUtils.calculateSemanticSimilarity(normalizedScript, normalizedStt);

        // 6. 최종 유사도 계산 (키워드 70% + 의미론적 유사도 30%)
        double finalSimilarity = (keywordMatchRate * 0.7) + (semanticSimilarity * 0.3);                                                                                                                     

        

        // 7. 점수 계산 (0~100)
        int accuracyScore = (int) Math.round(finalSimilarity * 100);

        log.info("✅ Accuracy analysis complete - Score: {}, Similarity: {}, Keyword match: {}/{}, AI: {}",
                accuracyScore, String.format("%.2f", finalSimilarity), matchedCount, scriptKeywords.size(),
                TextAnalysisUtils.isAIEnabled() ? "AI enabled" : "AI disabled");

        return AccuracyAnalysisResult.builder()
                .accuracyScore(accuracyScore)
                .scriptSimilarity(finalSimilarity)
                .keywordMatchRate(keywordMatchRate)
                .matchedKeywordCount(matchedCount)
                .totalKeywordCount(scriptKeywords.size())
                .missingKeywords(new ArrayList<>(missingKeywords))
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
}

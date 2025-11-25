package com.pres.pres_server.service.analyse;

import com.pres.pres_server.service.ai.OpenAIEmbeddingService;
import com.pres.pres_server.service.analyse.utils.*;

import java.util.*;

/**
 * 텍스트 분석 통합 파사드 (Facade Pattern)
 * 
 * 각 세부 기능은 다음 클래스로 분리되었습니다:
 * - TextNormalizer: 정규화, 불용어 처리
 * - TextTokenizer: 토큰화, 문장 분리
 * - KeywordExtractor: 키워드 추출
 * - SimilarityCalculator: 유사도 계산
 * - KoreanMorphologyAnalyzer: 형태소 분석 (Komoran)
 * 
 * 이 클래스는 하위 호환성을 위한 래퍼(wrapper) 역할을 합니다.
 */
public class TextAnalysisUtils {

    // ========== 설정 ==========

    public static void setEmbeddingService(OpenAIEmbeddingService service) {
        SimilarityCalculator.setEmbeddingService(service);
    }

    // ========== 정규화 (TextNormalizer) ==========

    public static String normalizeText(String text) {
        return TextNormalizer.normalize(text);
    }

    public static boolean isStopword(String word) {
        return TextNormalizer.isStopword(word);
    }

    public static Map<String, Long> calculateWordFrequency(List<String> words) {
        return TextNormalizer.calculateFrequency(words);
    }

    // ========== 토큰화 (TextTokenizer) ==========

    public static List<String> tokenizeWords(String text) {
        return TextTokenizer.tokenizeWords(text);
    }

    public static List<String> tokenizeSentences(String text) {
        return TextTokenizer.tokenizeSentences(text);
    }

    public static List<String> tokenizeKoreanSentences(String text) {
        return TextTokenizer.tokenizeKoreanSentences(text);
    }

    public static List<String> generateNGrams(List<String> words, int n) {
        return TextTokenizer.generateNGrams(words, n);
    }

    // ========== 키워드 추출 (KeywordExtractor) ==========

    // qna comparison 호출
    public static Set<String> extractKeywords(List<String> words) {
        return KeywordExtractor.extractKeywords(words);
    }

    // 미사용
    public static Set<String> extractKeywords(List<String> words, int minFrequency) {
        return KeywordExtractor.extractKeywords(words, minFrequency);
    }

    // qna comparison, accuracy 호출
    public static double calculateKeywordMatchRate(Set<String> keywords1, Set<String> keywords2) {
        return KeywordExtractor.calculateKeywordMatchRate(keywords1, keywords2);
    }

    // qna comparison, accuracy 호출
    public static Set<String> findMissingKeywords(Set<String> keywords1, Set<String> keywords2) {
        return KeywordExtractor.findMissingKeywords(keywords1, keywords2);
    }

    // ========== 유사도 계산 (SimilarityCalculator) ==========

    // 미사용
    public static int levenshteinDistance(String s1, String s2) {
        return SimilarityCalculator.levenshteinDistance(s1, s2);
    }

    // open ai embedding
    public static double calculateSimilarity(String s1, String s2) {
        return SimilarityCalculator.calculateSimilarity(s1, s2);
    }

    // 미사용
    public static double calculateSimilarity(String s1, String s2, int maxSampleLength) {
        return SimilarityCalculator.calculateSimilarity(s1, s2, maxSampleLength);
    }

    // qna comparison, accuracy, repetitive 호출
    public static double calculateSemanticSimilarity(String text1, String text2) {
        return SimilarityCalculator.calculateSemanticSimilarity(text1, text2);
    }

    // 미사용
    public static double calculateAverageSemanticSimilarity(List<String> sentences1, List<String> sentences2) {
        return SimilarityCalculator.calculateAverageSemanticSimilarity(sentences1, sentences2);
    }

    // repetitive, slidemapper 호출
    public static double jaccardSimilarity(Set<String> a, Set<String> b) {
        return SimilarityCalculator.jaccardSimilarity(a, b);
    }

    // 미사용
    public static List<SimilarityCalculator.SimilarSentencePair> findSimilarSentences(
            List<String> sentences, double threshold) {
        return SimilarityCalculator.findSimilarSentences(sentences, threshold);
    }

    // accuracy 호출
    public static boolean isAIEnabled() {
        return SimilarityCalculator.isAIEnabled();
    }

    // ========== 하위 호환성: DTO 타입 별칭 ==========

    /**
     * @deprecated SimilarityCalculator.SimilarSentencePair 사용 권장
     */
    @Deprecated
    public static class SimilarSentencePair extends SimilarityCalculator.SimilarSentencePair {
        public SimilarSentencePair(int index1, int index2, double similarity) {
            super(index1, index2, similarity);
        }
    }

    // ========== 한국어 형태소 분석 (KoreanMorphologyAnalyzer) ==========

    public static List<String> tokenizeKomoran(String text) {
        return KomoranAnalyzer.tokenizeKomoran(text);
    }

    public static List<String> komoranNGrams(String text, int n) {
        return KomoranAnalyzer.komoranNGrams(text, n);
    }

    public static Set<String> extractKeywordsKomoran(List<String> words, int minFrequency) {
        return KomoranAnalyzer.extractKeywordsKomoran(words, minFrequency);
    }

    /** 상위 N개 키워드(빈도순)를 반환합니다. UI에서 상위 10개만 보여줄 때 사용하세요. */
    public static List<String> extractTopKeywordsKomoran(List<String> words, int topN) {
        return KomoranAnalyzer.extractTopKeywordsKomoran(words, topN);
    }

    /** NEW: 스팬 포함 정규화 토큰 반환 (외부 서비스에서 직접 사용) */
    public static List<KomoranAnalyzer.NormToken> tokenizeKomoranWithSpans(String text) {
        return KomoranAnalyzer.tokenizeForRepeatWithSpans(text);
    }

    /** NEW: 스팬 포함 정규화 토큰에서 norm만 추출 (빈도/유사도 등 간편용) */
    public static List<String> tokenizeKomoranNorms(String text) {
        return tokenizeKomoranWithSpans(text).stream()
                .map(nt -> nt.norm)
                .toList();
    }

    /** NEW: 의미 토큰만 사용(N,V,S)하여 norm 기반 N-gram 생성 */
    public static List<String> komoranMeaningfulNGrams(String text, int n) {
        var nts = tokenizeKomoranWithSpans(text);
        var sig = nts.stream()

                .filter(nt -> nt.posHead.equals("N") || nt.posHead.equals("V") || nt.posHead.equals("S"))
                .toList();

        if (sig.size() < n)
            return List.of();
        List<String> out = new ArrayList<>();
        for (int i = 0; i + (n - 1) < sig.size(); i++) {
            out.add(sig.subList(i, i + n).stream()
                    .map(nt -> nt.norm)
                    .collect(java.util.stream.Collectors.joining(" ")));
        }
        return out;
    }

    /**
     * Context-aware variant that invokes KomoranAnalyzer with a context label and
     * returns meaningful n-grams.
     */
    public static List<String> komoranMeaningfulNGrams(String text, int n, String context) {
        var nts = KomoranAnalyzer.tokenizeForRepeatWithSpans(text, context);
        var sig = nts.stream()
                .filter(nt -> nt.posHead.equals("N") || nt.posHead.equals("V") || nt.posHead.equals("S"))
                .toList();

        if (sig.size() < n)
            return List.of();
        List<String> out = new ArrayList<>();
        for (int i = 0; i + (n - 1) < sig.size(); i++) {
            out.add(sig.subList(i, i + n).stream()
                    .map(nt -> nt.norm)
                    .collect(java.util.stream.Collectors.joining(" ")));
        }
        return out;
    }
}
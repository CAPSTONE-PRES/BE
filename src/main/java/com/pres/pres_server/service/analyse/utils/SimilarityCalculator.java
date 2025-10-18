package com.pres.pres_server.service.analyse.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.pres.pres_server.service.ai.OpenAIEmbeddingService;

public class SimilarityCalculator {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SimilarityCalculator.class);

    private static OpenAIEmbeddingService embeddingService;

    public static void setEmbeddingService(OpenAIEmbeddingService service) {
        embeddingService = service;
    }

    public static int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];
        for (int i = 0; i <= len1; i++)
            dp[i][0] = i;
        for (int j = 0; j <= len2; j++)
            dp[0][j] = j;
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[len1][len2];
    }

    public static double calculateSimilarity(String s1, String s2) {
        return calculateSimilarity(s1, s2, 1000);
    }

    public static double calculateSimilarity(String s1, String s2, int maxSampleLength) {
        String sample1 = s1.length() > maxSampleLength ? s1.substring(0, maxSampleLength) : s1;
        String sample2 = s2.length() > maxSampleLength ? s2.substring(0, maxSampleLength) : s2;
        int distance = levenshteinDistance(sample1, sample2);
        int maxLength = Math.max(sample1.length(), sample2.length());
        if (maxLength == 0)
            return 1.0;
        return 1.0 - ((double) distance / maxLength);
    }


    public static double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty())
            return 0.0;
        Set<String> inter = new java.util.HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new java.util.HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
    }

    public static double calculateSemanticSimilarity(String text1, String text2) {
        if (embeddingService != null) {
            try {
                // AI 기반 의미론적 유사도
                return embeddingService.calculateSemanticSimilarity(text1, text2);
            } catch (Exception e) {
                // AI 실패 시 Levenshtein으로 폴백
                log.warn("AI 유사도 계산 실패, Levenshtein으로 폴백: {}", e.getMessage());
            }
        }

        return com.pres.pres_server.service.analyse.utils.SimilarityCalculator.calculateSimilarity(text1, text2);
    }

    public static double calculateAverageSemanticSimilarity(List<String> sentences1, List<String> sentences2) {
        if (sentences1.isEmpty() || sentences2.isEmpty()) {
            return 0.0;
        }

        double totalSimilarity = 0.0;
        int count = 0;

        for (String s1 : sentences1) {
            for (String s2 : sentences2) {
                totalSimilarity += calculateSemanticSimilarity(s1, s2);
                count++;
            }
        }

        return count > 0 ? totalSimilarity / count : 0.0;
    }

    public static List<SimilarSentencePair> findSimilarSentences(List<String> sentences, double threshold) {
        List<SimilarSentencePair> similarPairs = new ArrayList<>();

        for (int i = 0; i < sentences.size(); i++) {
            for (int j = i + 1; j < sentences.size(); j++) {
                double similarity = calculateSemanticSimilarity(sentences.get(i), sentences.get(j));

                if (similarity >= threshold) {
                    similarPairs.add(new SimilarSentencePair(i, j, similarity));
                }
            }
        }

        return similarPairs;
    }

    public static class SimilarSentencePair {
        public final int index1;
        public final int index2;
        public final double similarity;

        public SimilarSentencePair(int index1, int index2, double similarity) {
            this.index1 = index1;
            this.index2 = index2;
            this.similarity = similarity;
        }
    }

    public static boolean isAIEnabled() {
        return embeddingService != null;
    }
}

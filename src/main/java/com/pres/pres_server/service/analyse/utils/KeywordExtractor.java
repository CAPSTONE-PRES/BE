package com.pres.pres_server.service.analyse.utils;

import java.util.*;
import java.util.stream.Collectors;

public class KeywordExtractor {
    public static Set<String> extractKeywords(List<String> words) {
        return extractKeywords(words, 2);
    }

    public static Set<String> extractKeywords(List<String> words, int minFrequency) {
        List<String> filteredWords = words.stream()
                .filter(word -> !TextNormalizer.isStopword(word))
                .filter(word -> word.length() >= 2)
                .collect(Collectors.toList());
        Map<String, Long> wordFreq = filteredWords.stream()
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));
        final int SHORT_DOC_THRESHOLD = 20;
        final int SHORT_DOC_TOPN = 3;
        if (wordFreq.isEmpty())
            return Collections.emptySet();
        if (filteredWords.size() <= SHORT_DOC_THRESHOLD) {
            return wordFreq.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(SHORT_DOC_TOPN)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }
        return wordFreq.entrySet().stream()
                .filter(entry -> entry.getValue() >= minFrequency)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public static double calculateKeywordMatchRate(Set<String> keywords1, Set<String> keywords2) {
        if (keywords1.isEmpty())
            return 0.0;
        Set<String> matched = new java.util.HashSet<>(keywords1);
        matched.retainAll(keywords2);
        return (double) matched.size() / keywords1.size();
    }

    public static Set<String> findMissingKeywords(Set<String> keywords1, Set<String> keywords2) {
        Set<String> missing = new java.util.HashSet<>(keywords1);
        missing.removeAll(keywords2);
        return missing;
    }
}

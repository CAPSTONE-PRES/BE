package com.pres.pres_server.service.analyse.utils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 텍스트 정규화 및 전처리 유틸리티
 */
public class TextNormalizer {

    // 한글 불용어 (조사, 접속사 등)
    private static final Set<String> KOREAN_STOPWORDS = Set.of(
            "그", "이", "저", "것", "수", "등", "및", "또한", "하지만", "그리고",
            "그러나", "따라서", "즉", "또", "혹은", "있다", "없다", "한다",
            "하다", "되다", "이다", "아니다", "같다", "보다", "크다", "작다", "은", "는", "가");

    // 영어 불용어
    private static final Set<String> ENGLISH_STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "is", "are", "was", "were", "be", "been", "have", "has", "had",
            "do", "does", "did", "will", "would", "should", "could", "may", "might");

    /**
     * 텍스트 정규화 (소문자, 특수문자 제거, 공백 정리)
     */
    public static String normalize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        return text.toLowerCase()
                .replaceAll("[^가-힣a-z0-9\\s]", " ") // 한글, 영문, 숫자, 공백만 남김
                .replaceAll("\\s+", " ") // 연속 공백 제거
                .trim();
    }

    /**
     * 불용어 체크
     */
    public static boolean isStopword(String word) {
        return KOREAN_STOPWORDS.contains(word) || ENGLISH_STOPWORDS.contains(word);
    }

    /**
     * 불용어 제거 (단어 리스트)
     */
    public static List<String> removeStopwords(List<String> words) {
        return words.stream()
                .filter(word -> !isStopword(word))
                .filter(word -> word.length() >= 2) // 2글자 이상만
                .collect(Collectors.toList());
    }

    /**
     * 빈도수 계산
     */
    public static Map<String, Long> calculateFrequency(List<String> words) {
        return words.stream()
                .filter(word -> !isStopword(word))
                .filter(word -> word.length() >= 2)
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));
    }
}
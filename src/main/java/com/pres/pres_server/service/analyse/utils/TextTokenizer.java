package com.pres.pres_server.service.analyse.utils;

import java.util.*;
import java.util.stream.Collectors;

public class TextTokenizer {
    public static List<String> tokenizeWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(text.split("\\s+")).filter(word -> word.length() > 1).collect(Collectors.toList());
    }

    public static List<String> tokenizeSentences(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(text.split("(?<=[.!?])\\s+")).map(String::trim)
                .filter(sentence -> !sentence.isEmpty() && sentence.length() > 10)
                .collect(Collectors.toList());
    }

    public static List<String> tokenizeKoreanSentences(String text) {
        if (text == null || text.trim().isEmpty())
            return Collections.emptyList();
        List<String> sentences = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            buffer.append(word).append(" ");
            boolean isEnding = word.matches(".*[요죠네군여다]$") ||
                    word.matches(".*(습니다|ㅂ니다|거든요|거예요|어요|아요)$");
            boolean nextIsConnector = (i + 1 < words.length) &&
                    words[i + 1].matches("그리고|그런데|근데|하지만|그래서|또|그러면서");
            if (isEnding && (nextIsConnector || i == words.length - 1)) {
                String sentence = buffer.toString().trim();
                if (sentence.length() > 10) {
                    sentences.add(sentence);
                    buffer.setLength(0);
                }
            }
        }
        if (buffer.length() > 0) {
            String remaining = buffer.toString().trim();
            if (remaining.length() > 10) {
                sentences.add(remaining);
            }
        }
        return sentences;
    }

    // N-그램 생성 (연속된 n개의 단어 묶음)
    public static List<String> generateNGrams(List<String> words, int n) {
        if (words == null || words.size() < n)
            return new ArrayList<>();

        List<String> nGrams = new ArrayList<>();
        for (int i = 0; i <= words.size() - n; i++) {
            String nGram = String.join(" ", words.subList(i, i + n));
            nGrams.add(nGram);
        }
        return nGrams;
    }
}

package com.pres.pres_server.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * OpenAI Embeddings API 서비스
 * 텍스트를 벡터로 변환하여 의미론적 유사도 계산
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIEmbeddingService {

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.api.model:text-embedding-3-small}")
    private String embeddingModel;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/embeddings";

    private final RestTemplate restTemplate = new RestTemplate();

    // 임베딩 캐시 (비용 절감용)
    private final Map<String, float[]> embeddingCache = new HashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;

    /**
     * 두 텍스트의 의미론적 유사도 계산 (0.0 ~ 1.0)
     * 
     * @param text1 첫 번째 텍스트
     * @param text2 두 번째 텍스트
     * @return 코사인 유사도 (0.0 ~ 1.0)
     */
    public double calculateSemanticSimilarity(String text1, String text2) {
        log.debug("▶ 의미론적 유사도 계산 시작 - text1 길이: {}, text2 길이: {}",
                text1.length(), text2.length());

        try {
            // 1. 임베딩 벡터 생성 (캐시 활용)
            float[] embedding1 = getEmbeddingWithCache(text1);
            float[] embedding2 = getEmbeddingWithCache(text2);

            // 2. 코사인 유사도 계산
            double similarity = cosineSimilarity(embedding1, embedding2);

            log.debug("✅ 유사도 계산 완료 - similarity: {:.4f}", similarity);
            return similarity;

        } catch (Exception e) {
            log.error("❌ 의미론적 유사도 계산 실패 - Levenshtein으로 폴백", e);
            // 에러 시 기존 방식으로 폴백
            return fallbackToLevenshtein(text1, text2);
        }
    }

    /**
     * 캐시를 활용한 임베딩 벡터 생성
     */
    private float[] getEmbeddingWithCache(String text) {
        // 캐시 키 생성 (텍스트 정규화 후 해시)
        String cacheKey = normalizeForCache(text);

        // 캐시 확인
        if (embeddingCache.containsKey(cacheKey)) {
            log.debug("  • 캐시 히트: {}", cacheKey.substring(0, Math.min(50, cacheKey.length())));
            return embeddingCache.get(cacheKey);
        }

        // 캐시 미스 → API 호출
        log.debug("  • 캐시 미스 → API 호출");
        float[] embedding = getEmbedding(text);

        // 캐시 저장 (용량 제한)
        if (embeddingCache.size() < MAX_CACHE_SIZE) {
            embeddingCache.put(cacheKey, embedding);
        } else {
            log.warn("  • 캐시 용량 초과 - 저장 생략");
        }

        return embedding;
    }

    /**
     * OpenAI API 호출하여 임베딩 벡터 생성
     */
    private float[] getEmbedding(String text) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("OpenAI API key가 설정되지 않았습니다. application.yml에 openai.api.key를 설정하세요.");
        }

        try {
            // 요청 본문 생성
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", embeddingModel);
            requestBody.put("input", text);

            // 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // API 호출
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    OPENAI_API_URL,
                    request,
                    Map.class);

            // 응답에서 임베딩 벡터 추출
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new IllegalStateException("OpenAI API 응답이 null입니다");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            if (data == null || data.isEmpty()) {
                throw new IllegalStateException("OpenAI API 응답에 data가 없습니다");
            }

            @SuppressWarnings("unchecked")
            List<Double> embeddingList = (List<Double>) data.get(0).get("embedding");
            if (embeddingList == null || embeddingList.isEmpty()) {
                throw new IllegalStateException("OpenAI API 응답에 embedding이 없습니다");
            }

            // Double → float 변환
            float[] embedding = new float[embeddingList.size()];
            for (int i = 0; i < embeddingList.size(); i++) {
                embedding[i] = embeddingList.get(i).floatValue();
            }

            log.debug("  • API 호출 성공 - 벡터 차원: {}", embedding.length);
            return embedding;

        } catch (Exception e) {
            log.error("  • OpenAI API 호출 실패", e);
            throw new RuntimeException("임베딩 벡터 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 코사인 유사도 계산
     * similarity = (A · B) / (||A|| × ||B||)
     */
    private double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException("벡터 차원이 일치하지 않습니다");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * 캐시 키 생성용 텍스트 정규화
     */
    private String normalizeForCache(String text) {
        if (text == null)
            return "";

        // 공백 정규화 후 앞 100자만 사용 (키 길이 제한)
        String normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.length() > 100 ? normalized.substring(0, 100) : normalized;
    }

    /**
     * API 실패 시 Levenshtein Distance로 폴백
     */
    private double fallbackToLevenshtein(String text1, String text2) {
        log.warn("  • Levenshtein Distance로 폴백");
        return com.pres.pres_server.service.analyse.TextAnalysisUtils
                .calculateSimilarity(text1, text2);
    }

    /**
     * 캐시 통계 (모니터링용)
     */
    public Map<String, Object> getCacheStats() {
        return Map.of(
                "cacheSize", embeddingCache.size(),
                "maxCacheSize", MAX_CACHE_SIZE,
                "hitRate", "N/A (구현 필요)");
    }

    /**
     * 캐시 초기화 (테스트용)
     */
    public void clearCache() {
        embeddingCache.clear();
        log.info("임베딩 캐시 초기화 완료");
    }
}

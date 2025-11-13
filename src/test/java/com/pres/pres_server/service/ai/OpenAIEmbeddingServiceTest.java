//package com.pres.pres_server.service.ai;
//
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * OpenAI Embeddings API 테스트
// * 실제 비용이 발생하므로 주의!
// */
//@Slf4j
//@SpringBootTest
//class OpenAIEmbeddingServiceTest {
//
//    @Autowired
//    private OpenAIEmbeddingService embeddingService;
//
//    @Test
//    void testSemanticSimilarity_SameMeaning() {
//        // Given: 같은 의미의 다른 표현
//        String text1 = "안녕하세요. 오늘 발표를 시작하겠습니다.";
//        String text2 = "여러분 안녕하십니까. 지금부터 프레젠테이션을 진행하겠습니다.";
//
//        // When
//        double similarity = embeddingService.calculateSemanticSimilarity(text1, text2);
//
//        // Then
//        log.info("✅ 같은 의미 유사도: {:.4f}", similarity);
//        assertTrue(similarity > 0.7, "같은 의미는 유사도 0.7 이상이어야 함");
//    }
//
//    @Test
//    void testSemanticSimilarity_DifferentMeaning() {
//        // Given: 완전히 다른 의미
//        String text1 = "오늘 날씨가 정말 좋습니다.";
//        String text2 = "머신러닝 알고리즘의 핵심 원리를 설명하겠습니다.";
//
//        // When
//        double similarity = embeddingService.calculateSemanticSimilarity(text1, text2);
//
//        // Then
//        log.info("✅ 다른 의미 유사도: {:.4f}", similarity);
//        assertTrue(similarity < 0.5, "다른 의미는 유사도 0.5 미만이어야 함");
//    }
//
//    @Test
//    void testSemanticSimilarity_Synonyms() {
//        // Given: 동의어 테스트
//        String text1 = "이 프로젝트는 매우 중요합니다.";
//        String text2 = "해당 프로젝트는 굉장히 중대합니다.";
//
//        // When
//        double similarity = embeddingService.calculateSemanticSimilarity(text1, text2);
//
//        // Then
//        log.info("✅ 동의어 유사도: {:.4f}", similarity);
//        assertTrue(similarity > 0.8, "동의어는 유사도 0.8 이상이어야 함");
//    }
//
//    @Test
//    void testCachePerformance() {
//        // Given
//        String text1 = "캐시 성능 테스트를 진행합니다.";
//        String text2 = "캐시가 정상적으로 작동하는지 확인합니다.";
//
//        // When: 첫 번째 호출 (API 호출)
//        long start1 = System.currentTimeMillis();
//        double similarity1 = embeddingService.calculateSemanticSimilarity(text1, text2);
//        long time1 = System.currentTimeMillis() - start1;
//
//        // When: 두 번째 호출 (캐시 히트)
//        long start2 = System.currentTimeMillis();
//        double similarity2 = embeddingService.calculateSemanticSimilarity(text1, text2);
//        long time2 = System.currentTimeMillis() - start2;
//
//        // Then
//        log.info("✅ 첫 번째 호출: {}ms, 두 번째 호출 (캐시): {}ms", time1, time2);
//        assertEquals(similarity1, similarity2, 0.0001, "캐시된 결과는 동일해야 함");
//        assertTrue(time2 < time1, "캐시 히트는 더 빨라야 함");
//    }
//
//    @Test
//    void testCacheStats() {
//        // Given: 몇 번 호출
//        embeddingService.calculateSemanticSimilarity("테스트1", "테스트2");
//        embeddingService.calculateSemanticSimilarity("테스트3", "테스트4");
//
//        // When
//        var stats = embeddingService.getCacheStats();
//
//        // Then
//        log.info("✅ 캐시 통계: {}", stats);
//        assertTrue((int) stats.get("cacheSize") > 0, "캐시에 데이터가 있어야 함");
//    }
//}

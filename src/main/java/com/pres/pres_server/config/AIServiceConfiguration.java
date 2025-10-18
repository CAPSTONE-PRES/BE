package com.pres.pres_server.config;

import com.pres.pres_server.service.ai.OpenAIEmbeddingService;
import com.pres.pres_server.service.analyse.TextAnalysisUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * AI 서비스 초기화 설정
 * TextAnalysisUtils에 OpenAIEmbeddingService를 주입
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AIServiceConfiguration {

    private final OpenAIEmbeddingService embeddingService;

    /**
     * 애플리케이션 시작 시 AI 서비스 초기화
     */
    @PostConstruct
    public void initializeAIService() {
        log.info("🤖 AI service start");

        try {
            // TextAnalysisUtils에 OpenAI Embeddings 서비스 주입
            TextAnalysisUtils.setEmbeddingService(embeddingService);

            log.info("✅ AI service reset complete - OpenAI Embeddings onboard");
            log.info("   ↳ AI  (text-embedding-3-small)");

        } catch (Exception e) {
            log.error("❌ AI service reset failed - Levenshtein Distance로 폴백", e);
        }
    }
}

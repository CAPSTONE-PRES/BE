package com.pres.pres_server.service.analyse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 필러 워드(Filler) 분석 서비스
 * 
 * ⚠️ RepetitiveTextAnalysisService(말의 반복)와는 다른 목적!
 * - FillerService: 필러 워드 탐지 (추임새 + 습관적 표현)
 * - RepetitiveTextAnalysisService: 발표 내용의 불필요한 반복 탐지 (키워드 아닌 3회 이상 반복)
 * 
 * 목적: 말더듬/습관적 표현 탐지
 * 방식: 정규식으로 단어 경계 정확하게 체크 (^|\\s)(단어)(?=\\s|$)
 */
@Service
public class FillerService {

    /**
     * 슬라이드별 필러 분석 결과 DTO
     */
    public static class SlideFillerDto {
        private final int slideIndex;
        private final Map<String, Integer> fillerCounts;

        public SlideFillerDto(int slideIndex, Map<String, Integer> fillerCounts) {
            this.slideIndex = slideIndex;
            this.fillerCounts = fillerCounts;
        }

        public int getSlideIndex() {
            return slideIndex;
        }

        public Map<String, Integer> getFillerCounts() {
            return fillerCounts;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(FillerService.class);

    /**
     * 필러 워드 패턴 정의 (추임새 4개 + 습관적 표현 12개 = 총 16개)
     */
    private static final Map<String, Pattern> FILLER_PATTERNS;

    static {
        Map<String, Pattern> patterns = new java.util.LinkedHashMap<>();

        // 추임새 (짧은 무의식적 소리)
        patterns.put("음", Pattern.compile("(^|\\s)(음)(?=\\s|$)"));
        patterns.put("어", Pattern.compile("(^|\\s)(어)(?=\\s|$)"));
        patterns.put("아", Pattern.compile("(^|\\s)(아)(?=\\s|$)"));
        patterns.put("뭐지", Pattern.compile("(^|\\s)(뭐지)(?=\\s|$)"));

        // 습관적 표현 (긴 의식적 단어)
        patterns.put("그러니까", Pattern.compile("(^|\\s)(그러니까)(?=\\s|$)"));
        patterns.put("그 다음에", Pattern.compile("(^|\\s)(그 다음에)(?=\\s|$)"));
        patterns.put("약간", Pattern.compile("(^|\\s)(약간)(?=\\s|$)"));
        patterns.put("되게", Pattern.compile("(^|\\s)(되게)(?=\\s|$)"));
        patterns.put("엄청", Pattern.compile("(^|\\s)(엄청)(?=\\s|$)"));
        patterns.put("진짜", Pattern.compile("(^|\\s)(진짜)(?=\\s|$)"));
        patterns.put("막", Pattern.compile("(^|\\s)(막)(?=\\s|$)"));
        patterns.put("이제", Pattern.compile("(^|\\s)(이제)(?=\\s|$)"));
        patterns.put("그냥", Pattern.compile("(^|\\s)(그냥)(?=\\s|$)"));
        patterns.put("좀", Pattern.compile("(^|\\s)(좀)(?=\\s|$)"));
        patterns.put("완전", Pattern.compile("(^|\\s)(완전)(?=\\s|$)"));
        patterns.put("정말", Pattern.compile("(^|\\s)(정말)(?=\\s|$)"));

        FILLER_PATTERNS = java.util.Collections.unmodifiableMap(patterns);
    }

    /**
     * 슬라이드별 텍스트 리스트를 받아 각 슬라이드별로 필러 카운트를 반환 (슬라이드 인덱스 포함)
     * 
     * @param slideTexts 슬라이드별 텍스트(순서대로)
     * @return 슬라이드별 필러 분석 결과 리스트 (슬라이드 인덱스, 카운트)
     */
    public java.util.List<SlideFillerDto> countFillersBySlides(java.util.List<String> slideTexts) {
        java.util.List<SlideFillerDto> result = new java.util.ArrayList<>();
        if (slideTexts == null || slideTexts.isEmpty()) {
            return result;
        }
        for (int i = 0; i < slideTexts.size(); i++) {
            String text = slideTexts.get(i);
            Map<String, Integer> counts = countFillersByRegex(text);
            result.add(new SlideFillerDto(i, counts));
        }
        return result;
    }

    /**
     * 텍스트에서 필러 워드를 정규식으로 탐지하고 개수를 카운트
     * 
     * @param text 분석할 텍스트
     * @return 각 필러 워드별 출현 횟수 (불변 Map)
     */
    public Map<String, Integer> countFillersByRegex(String text) {
        log.info("▶ 필러 분석 시작 - 텍스트 길이: {}", text != null ? text.length() : 0);

        // 입력값 검증
        if (text == null || text.trim().isEmpty()) {
            log.warn("입력 텍스트가 비어있어 필러 분석 불가");
            return Map.of(); // 빈 불변 Map 반환
        }

        Map<String, Integer> fillerCounts = new HashMap<>();

        // 1) 텍스트 정규화 (TextAnalysisUtils 사용으로 일관성 유지)
        String normalized = TextAnalysisUtils.normalizeText(text);
        log.debug("    ▶ 정규화된 텍스트: \"{}\"", normalized);

        // 2) 모든 필러 패턴 검사 (Map 기반 루프 방식)
        for (Map.Entry<String, Pattern> entry : FILLER_PATTERNS.entrySet()) {
            String fillerWord = entry.getKey();
            Pattern pattern = entry.getValue();

            Matcher matcher = pattern.matcher(normalized);
            while (matcher.find()) {
                fillerCounts.merge(fillerWord, 1, Integer::sum);
                log.debug("    → \"{}\" 발견 (위치: {})", fillerWord, matcher.start());
            }
        }

        log.info("✅ 필러 분석 완료 - 발견된 종류: {}, 총 횟수: {}",
                fillerCounts.size(),
                fillerCounts.values().stream().mapToInt(Integer::intValue).sum());

        // 불변 Map으로 반환 (외부 수정 방지)
        return Map.copyOf(fillerCounts);
    }
}

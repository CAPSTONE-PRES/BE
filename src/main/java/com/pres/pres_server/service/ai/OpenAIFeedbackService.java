package com.pres.pres_server.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * OpenAI ChatCompletion 기반 피드백 생성 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIFeedbackService {

    @Value("${openai.api.key:}")
    private String apiKey;

    private static final String CHAT_MODEL = "gpt-4o-mini";
    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 공통: OpenAI 응답 Map에서 content 문자열을 안전하게 추출합니다.
     * 문제 발생 시 RuntimeException을 던져 호출자가 처리하게 합니다.
     */
    private String extractContentFromResponse(Map<String, Object> responseBody) {
        if (responseBody == null) {
            throw new RuntimeException("AI 서비스 응답이 비어있습니다.");
        }

        Object errorObj = responseBody.get("error");
        if (errorObj instanceof Map) {
            Map<?, ?> error = (Map<?, ?>) errorObj;
            String errorMessage = error.get("message") != null ? error.get("message").toString() : "알 수 없는 오류";
            String errorType = error.get("type") != null ? error.get("type").toString() : "error";
            throw new RuntimeException("AI 서비스 오류: [" + errorType + "] " + errorMessage);
        }

        Object choicesObj = responseBody.get("choices");
        if (!(choicesObj instanceof List)) {
            throw new RuntimeException("AI 서비스 응답에 choices가 없습니다.");
        }
        List<?> choices = (List<?>) choicesObj;
        if (choices.isEmpty() || !(choices.get(0) instanceof Map)) {
            throw new RuntimeException("AI 서비스 응답에 choices가 비어있거나 형식이 올바르지 않습니다.");
        }

        Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
        Object messageObj = firstChoice.get("message");
        if (!(messageObj instanceof Map)) {
            throw new RuntimeException("AI 응답에 message가 없습니다.");
        }
        Map<?, ?> message = (Map<?, ?>) messageObj;
        Object contentObj = message.get("content");
        if (!(contentObj instanceof String)) {
            throw new RuntimeException("AI 응답 content가 문자열이 아닙니다.");
        }
        String content = (String) contentObj;
        if (content == null || content.isBlank()) {
            throw new RuntimeException("AI 응답 content가 비어있습니다.");
        }
        return content;
    }

    // QNA 피드백 생성 (표현방식, 논리 흐름) - JSON 반환
    public Map<String, String> generateQnaFeedback(String question, String idealAnswer, String userAnswer) {
        String prompt = buildPrompt(question, idealAnswer, userAnswer);
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", CHAT_MODEL);
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", "너는 발표/면접 코칭 전문가야. 피드백은 친절하고 구체적으로 작성해."));
            messages.add(Map.of("role", "user", "content", prompt));
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 500);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<?> response = null;
            try {
                response = restTemplate.postForEntity(
                        OPENAI_CHAT_URL,
                        request,
                        Map.class);
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                log.error("OpenAI API 클라이언트 오류 [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
                return Map.of("error", "AI 서비스 요청 오류: " + e.getMessage());
            } catch (org.springframework.web.client.HttpServerErrorException e) {
                log.error("OpenAI API 서버 오류 [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
                return Map.of("error", "AI 서비스 일시적 오류: " + e.getMessage());
            } catch (org.springframework.web.client.ResourceAccessException e) {
                log.error("OpenAI API 연결 오류: {}", e.getMessage());
                return Map.of("error", "AI 서비스 연결 실패: " + e.getMessage());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) ((response != null) ? response.getBody() : null);
            String content;
            try {
                content = extractContentFromResponse(responseBody);
            } catch (Exception ex) {
                log.error("OpenAI 응답 처리 실패: {}", ex.getMessage());
                return Map.of("error", "AI 서비스 응답 처리 실패: " + ex.getMessage());
            }

            // JSON 파싱
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> result = objectMapper.readValue(content, Map.class);
                return result;
            } catch (Exception jsonEx) {
                log.error("OpenAI 피드백 JSON 파싱 실패", jsonEx);
                if (content.trim().startsWith("<")) {
                    return Map.of("error", "AI 피드백 JSON 파싱에 실패했습니다. (HTML 응답)", "raw", content);
                }
                return Map.of("error", "AI 피드백 JSON 파싱에 실패했습니다.", "raw", content);
            }
        } catch (Exception e) {
            log.error("OpenAI 피드백 생성 실패", e);
            return Map.of("error", "AI 피드백 생성에 실패했습니다.");
        }
    }

    // (피드백 타입 enum은 필요시 다시 추가)

    // 공통: 텍스트 프롬프트를 보내고 content 문자열을 반환
    // HTTP 관련 예외를 중앙에서 처리하여 모든 피드백 함수에 일관된 동작을 제공합니다.
    private String executeChatRequest(String systemPrompt, String userPrompt) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", CHAT_MODEL);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 800);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<?> response = null;
        try {
            response = restTemplate.postForEntity(OPENAI_CHAT_URL, request, Map.class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("OpenAI API 클라이언트 오류 [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("AI 서비스 요청 오류: " + e.getMessage());
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("OpenAI API 서버 오류 [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("AI 서비스 일시적 오류: " + e.getMessage());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("OpenAI API 연결 오류: {}", e.getMessage());
            throw new RuntimeException("AI 서비스 연결 실패: " + e.getMessage());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) ((response != null) ? response.getBody() : null);
        // extractContentFromResponse가 내부 structure 검사 및 error 필드를 처리합니다.
        return extractContentFromResponse(responseBody);
    }

    // 항목별 피드백 생성 예시: 망설임(침묵+추임새)
    public Map<String, String> generateHesitationFeedback(String slideId, String transcript, int hesitationCount,
            double totalSilenceSec) {
        String system = "너는 발표 코칭 전문가야. 아래 조건에 맞춰 친절하고 구체적으로 피드백을 JSON으로 반환해.";
        String user = String.format(
                "슬라이드: %s\n대본/전사: %s\n망설임 횟수: %d\n총 침묵(초): %.2f\n\n" +
                        "요구: 'hesitation' 키에 피드백을 담은 JSON으로만 응답해. 예: {\"hesitation\":\"...\"}",
                slideId, transcript, hesitationCount, totalSilenceSec);
        try {
            String content = executeChatRequest(system, user);
            @SuppressWarnings("unchecked")
            Map<String, String> result = objectMapper.readValue(content, Map.class);
            return result;
        } catch (Exception e) {
            log.error("망설임 피드백 생성 실패", e);
            return Map.of("error", "망설임 피드백 생성 실패", "raw", e.getMessage());
        }
    }

    // 항목별: 반복
    public Map<String, String> generateRepetitionFeedback(String slideId, String transcript, int repeatedWordCount,
            List<String> repeatedWords) {
        String system = "너는 발표 코칭 전문가야. 반복되는 단어/구문에 대해 친절하게 지적하고 대체 표현을 제시해. JSON으로 반환.";
        String user = String.format(
                "슬라이드: %s\n전사: %s\n반복 단어 수: %d\n반복 단어 목록: %s\n\n요구: {\"repetition\":\"...\"}",
                slideId, transcript, repeatedWordCount, repeatedWords);
        try {
            String content = executeChatRequest(system, user);
            @SuppressWarnings("unchecked")
            Map<String, String> result = objectMapper.readValue(content, Map.class);
            return result;
        } catch (Exception e) {
            log.error("반복 피드백 생성 실패", e);
            return Map.of("error", "반복 피드백 생성 실패", "raw", e.getMessage());
        }
    }

    // 항목별: 정확도
    public Map<String, String> generateAccuracyFeedback(String slideId, String transcript, String expectedKeyPoints) {
        String system = "너는 발표 코칭 전문가야. 발표 정확도(핵심내용 누락/오류)를 평가하고 보완 문장을 제시해. JSON으로 반환.";
        String user = String.format(
                "슬라이드: %s\n전사: %s\n기대 핵심포인트: %s\n\n요구: {\"accuracy\":\"...\"}",
                slideId, transcript, expectedKeyPoints);
        try {
            String content = executeChatRequest(system, user);
            @SuppressWarnings("unchecked")
            Map<String, String> result = objectMapper.readValue(content, Map.class);
            return result;
        } catch (Exception e) {
            log.error("정확도 피드백 생성 실패", e);
            return Map.of("error", "정확도 피드백 생성 실패", "raw", e.getMessage());
        }
    }

    // 항목별: 속도
    public Map<String, String> generatePaceFeedback(String slideId, String transcript, double wpm, double idealWpm) {
        String system = "너는 발표 코칭 전문가야. 말의 속도가 적절한지 판단하고 조절 팁을 JSON으로 반환해.";
        String user = String.format(
                "슬라이드: %s\n전사: %s\n현재 WPM: %.1f\n권장 WPM: %.1f\n\n요구: {\"pace\":\"...\"}",
                slideId, transcript, wpm, idealWpm);
        try {
            String content = executeChatRequest(system, user);
            @SuppressWarnings("unchecked")
            Map<String, String> result = objectMapper.readValue(content, Map.class);
            return result;
        } catch (Exception e) {
            log.error("속도 피드백 생성 실패", e);
            return Map.of("error", "속도 피드백 생성 실패", "raw", e.getMessage());
        }
    }

    /**
     * 전체 세션에 대한 요약 피드백 생성
     * 반환: 간결한 텍스트(한두 문장)
     */
    public String generateOverallFeedback(String sessionId, String aggregatedComments) {
        String system = "너는 발표 코칭 전문가야. 전체 세션에 대한 간결한 한두 문장 요약 피드백을 제공해줘.";
        String user = String.format(
                "세션: %s\n슬라이드별 코멘트(원문): %s\n\n요구: 위 코멘트를 참고해서 전체 연습 세션에 대한 한두 문장짜리 요약 피드백을 자연스러운 한국어 문장으로 반환해줘. 응답은 최대 200토큰의 평문 텍스트로만 제공하고, JSON이 아닌 텍스트만 반환해줘.",
                sessionId, aggregatedComments == null ? "" : aggregatedComments);
        try {
            String content = executeChatRequest(system, user);
            return content != null ? content.trim() : null;
        } catch (Exception e) {
            log.error("전체 요약 피드백 생성 실패: {}", e.getMessage());
            return null;
        }
    }

    private String buildPrompt(String question, String idealAnswer, String userAnswer) {
        return String.format(
                "아래는 면접/발표 QnA입니다.\n" +
                        "질문: %s\n" +
                        "모범 답변: %s\n" +
                        "사용자 답변: %s\n" +
                        "\n" +
                        "아래 기준에 따라 피드백을 작성해줘.\n" +
                        "1. 피드백은 두 가지로 나눠서 작성: (1) 표현방식 (2) 논리적 흐름 구성(PREP)\n" +
                        "2. 표현방식: '하지만', '그리고', '그러나' 등 연결어가 3번 이상 반복되면 부정적으로 평가\n" +
                        "3. 논리적 흐름: Point(주장)-Reason(이유)-Example(예시)-Point(결론강조) 순서(PREP)로 답변이 구성되어 있는지 평가.\n" +
                        "   PREP 구조가 섞여있거나 누락/순서 오류가 있으면 논리 흐름이 부족하다고 평가하고, 개선점을 제시\n" +
                        "4. 각 항목별로 구체적이고 친절하게 피드백 작성.\n" +
                        "5. 반드시 아래와 같은 JSON 형식으로만 답변해.\n" +
                        "{\\n  \"expression\": \"...표현방식 피드백...\",\\n  \"logic\": \"...논리적 흐름 피드백...\"\\n}\n",
                question, idealAnswer, userAnswer);
    }
}

package com.pres.pres_server.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * OpenAI ChatCompletion 기반 피드백 생성 서비스
 * (QnA 비교 등에서 자연어 피드백 생성)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIFeedbackService {

    @Value("${openai.api.key:}")
    private String apiKey;

    private static final String CHAT_MODEL = "gpt-4o-mini";
    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * QnA 피드백 생성 (표현방식, 논리 흐름) - JSON 반환
     * 
     * @param question    질문
     * @param idealAnswer 모범 답변
     * @param userAnswer  사용자 답변
     * @return Map<String, String> (expression, logic)
     */
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

            Object bodyObj = (response != null) ? response.getBody() : null;
            if (!(bodyObj instanceof Map)) {
                log.error("OpenAI API 응답이 비어있거나 Map이 아님: {}", bodyObj);
                return Map.of("error", "AI 서비스 응답이 비어있거나 올바르지 않습니다.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) bodyObj;
            // responseBody는 이미 null 아님이 보장됨

            // OpenAI error 필드 처리
            Object errorObj = responseBody.get("error");
            if (errorObj instanceof Map) {
                Map<?, ?> error = (Map<?, ?>) errorObj;
                String errorMessage = error.get("message") != null ? error.get("message").toString() : "알 수 없는 오류";
                String errorType = error.get("type") != null ? error.get("type").toString() : "error";
                log.error("OpenAI API 오류 [{}]: {}", errorType, errorMessage);
                return Map.of("error", "AI 서비스 오류: [" + errorType + "] " + errorMessage);
            }

            Object choicesObj = responseBody.get("choices");
            if (!(choicesObj instanceof List)) {
                log.error("OpenAI 응답에 choices 없음 또는 타입 불일치: {}", choicesObj);
                return Map.of("error", "AI 서비스 응답에 choices가 없습니다.");
            }
            List<?> choices = (List<?>) choicesObj;
            if (choices.isEmpty() || !(choices.get(0) instanceof Map)) {
                log.error("OpenAI 응답 choices 비어있음 또는 첫 요소 타입 불일치: {}", choices);
                return Map.of("error", "AI 서비스 응답에 choices가 없습니다.");
            }
            Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
            Object messageObj = firstChoice.get("message");
            if (!(messageObj instanceof Map)) {
                log.error("OpenAI 응답에 message 없음 또는 타입 불일치: {}", messageObj);
                return Map.of("error", "AI 서비스 응답에 message가 없습니다.");
            }
            Map<?, ?> message = (Map<?, ?>) messageObj;
            Object contentObj = message.get("content");
            String content = (contentObj instanceof String) ? (String) contentObj : null;
            if (content == null || content.isBlank()) {
                log.error("OpenAI 응답 content가 비어있음");
                return Map.of("error", "AI 서비스 응답 content가 비어있습니다.");
            }
            // JSON 파싱
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, String> result = mapper.readValue(content, Map.class);
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

    /**
     * 프롬프트 생성 (표현방식, 논리 흐름)
     */
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

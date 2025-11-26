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
            // enforce structured json response for QnA feedback to reduce parsing errors
            requestBody.put("response_format", buildQnaResponseFormat());
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 1500);

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
                log.debug("OpenAI raw content (QnA): {}", content);
            } catch (Exception ex) {
                // 파싱 실패 시 원시 응답을 경고로 남겨 디버깅에 활용할 수 있게 합니다.
                log.warn("OpenAI raw response (QnA): {}", responseBody);
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

    private String buildPrompt(String question, String idealAnswer, String userAnswer) {
        return String.format(
                "아래는 발표 QnA입니다.\n" +
                        "질문: %s\n" +
                        "모범 답변(이상적인 답변): %s\n" +
                        "사용자 답변: %s\n" +
                        "\n" +
                        "너의 역할:\n" +
                        "- 너는 발표/면접 코칭 전문가다.\n" +
                        "- 모범 답변과 사용자 답변을 비교해서, 표현 방식과 논리적 흐름 관점에서 차이점을 분석하고 구체적인 개선 피드백을 줘야 한다.\n" +
                        "\n" +
                        "피드백 작성 기준:\n" +
                        "1. 표현(expression) 피드백\n" +
                        "   - 문장 구조: 한 문장에 너무 많은 정보가 들어가 있지는 않은지, 문장을 적절히 나눴는지, 문장의 길이가 적절한지 설명해줘.\n" +
                        "   - 연결어/리듬: '하지만', '그리고', '그러나' 등 연결어 사용이 과한지, 리듬이 단조로운지, 문단 구분이 적절한지 분석해줘.\n" +
                        "   - 어조/톤: 발표/면접용 원고로서 자연스러운지, 구어체/문어체 균형이 적절한지 언급해줘.\n" +
                        "   - 위 항목들을 토대로, 왜 기존 문장이 읽기/듣기 어렵고, 개선된 문장은 왜 더 명확한지\n" +
                        "     예시 문장을 인용하면서 설명해줘.\n" +
                        "   - 가능하면 다음과 같이 번호를 붙여 구조화해서 작성해줘:\n" +
                        "     1. 문장 구조의 명확화\n" +
                        "     2. 연결어와 리듬의 개선\n" +
                        "     3. 어조/톤 및 표현의 자연스러움\n" +
                        "\n" +
                        "2. 논리(logic) 피드백\n" +
                        "   - PREP 구조( Point-Reason-Example-Point ) 또는 그에 준하는 논리 구조가 잘 드러나는지 평가해줘.\n" +
                        "   - '원칙(핵심 메시지) → 일반적인 흐름 → 예외적인 경우 → 결론/핵심 정리'처럼 정보가\n" +
                        "     단계적으로 배열되어 있는지 설명해줘.\n" +
                        "   - 핵심 메시지가 문단에서 얼마나 잘 드러나는지, 마지막 문장이 핵심을 잘 강조하는지 분석해줘.\n" +
                        "   - 필요하다면, \"기존 답변\"과 \"이상적인 구조\"의 차이를\n" +
                        "     1) 논리 흐름, 2) 정보 배치, 3) 핵심 메시지 강조\n" +
                        "     이런 식으로 번호를 붙여 설명해줘.\n" +
                        "\n" +
                        "출력 형식(매우 중요):\n" +
                        "- 반드시 아래 JSON 형식으로만, 추가 텍스트 없이 답변해.\n" +
                        "- 각 값은 한국어로 작성하고, 문장/문단 안에서 1., 2., 3. 같이 번호를 사용해도 된다.\n" +
                        "{\\n" +
                        "  \"expression\": \"...표현 방식에 대한 상세 피드백...\",\\n" +
                        "  \"logic\": \"...논리 흐름에 대한 상세 피드백...\"\\n" +
                        "}\n",
                question, idealAnswer, userAnswer);
    }

    // (피드백 타입 enum은 필요시 다시 추가)

    // 공통: 텍스트 프롬프트를 보내고 content 문자열을 반환
    // HTTP 관련 예외를 중앙에서 처리하여 모든 피드백 함수에 일관된 동작을 제공합니다.
    private String executeChatRequest(String systemPrompt, String userPrompt) throws Exception {
        return executeChatRequest(systemPrompt, userPrompt, null);
    }

    private String executeChatRequest(String systemPrompt, String userPrompt, Map<String, Object> responseFormat)
            throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", CHAT_MODEL);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 800);
        if (responseFormat != null) {
            requestBody.put("response_format", responseFormat);
        }

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
        try {
            return extractContentFromResponse(responseBody);
        } catch (Exception ex) {
            // 파싱/포맷 오류 발생 시 원시 응답을 WARN으로 남기고 예외를 재던집니다.
            log.warn("OpenAI raw response (possibly malformed) model={} response={}", CHAT_MODEL, responseBody);
            throw ex;
        }
    }

    // 항목별 피드백 생성: silence (침묵 관련, 기존 hesitation 대체용)
    public Map<String, String> generateSilenceFeedback(String slideId, String transcript, int silenceCount,
            double totalSilenceSec) {
        String system = "너는 발표 코칭 전문가야. 침묵(멈춤) 사용에 대해 짧고 명확한 피드백을 한두 문장으로만 제공해. 문단 대신 짧은 코멘트 한두 문장만 써.";

        String user = String.format(
                "슬라이드: %s\n전사: %s\n침묵 횟수: %d\n총 침묵(초): %.2f\n\n" +
                        "요구:\n" +
                        "- 침묵이 너무 잦거나 길어서 흐름이 끊기는 경우, 혹은 적절히 활용하면 좋겠다는 방향성을 한두 문장으로만 한국어 존댓말로 써줘.\n" +
                        "- 지나치게 장황하게 설명하지 말고, 느낌 + 간단한 제안을 한두 문장으로 끝내.\n" +
                        "- 반드시 {\"silence\": \"...\"} 형태의 JSON 한 개만 반환해.",
                slideId, transcript, silenceCount, totalSilenceSec);

        try {
            Map<String, Object> responseFormat = buildStringFieldResponseFormat("silence", "침묵 관련 코멘트");
            String content = executeChatRequest(system, user, responseFormat);
            log.debug("OpenAI raw content (silence): {}", content);
            @SuppressWarnings("unchecked")
            Map<String, String> result = objectMapper.readValue(content, Map.class);
            return result;
        } catch (Exception e) {
            log.error("silence 피드백 생성 실패", e);
            return Map.of("error", "silence 피드백 생성 실패", "raw", e.getMessage());
        }
    }

    // 항목별: 반복 repetition
    public Map<String, String> generateRepetitionFeedback(String slideId, String transcript, int repeatedWordCount,
            List<String> repeatedWords) {
        String system = "너는 발표 코칭 전문가야. 반복되는 어휘에 대해 짧고 명확한 피드백을 한두 문장으로만 제공해. 문단 대신 짧은 코멘트 한두 문장만 써.";

        String user = String.format(
                "슬라이드: %s\n전사: %s\n반복 단어 수: %d\n반복 단어 목록: %s\n\n" +
                        "요구:\n" +
                        "- 같은 표현이 자주 반복될 때 생기는 문제와, 다양한 표현을 쓰라는 제안을 한두 문장으로만 한국어 존댓말로 써줘.\n" +
                        "- 예를 들면 이런 톤이야: \"같은 표현이 반복되어 전달력이 다소 떨어질 수 있어요. 다양한 표현을 사용해보는 연습이 필요해 보여요.\" 같은 느낌.\n" +
                        "- 리스트, 번호, 불릿 없이 문장만 출력해.\n" +
                        "- 반드시 {\"repetition\": \"...\"} 형태의 JSON 한 개만 반환해.",
                slideId, transcript, repeatedWordCount, repeatedWords);

        try {
            Map<String, Object> responseFormat = buildStringFieldResponseFormat("repetition", "반복 관련 코멘트");
            String content = executeChatRequest(system, user, responseFormat);
            log.debug("OpenAI raw content (repetition): {}", content);
            @SuppressWarnings("unchecked")
            Map<String, String> result = objectMapper.readValue(content, Map.class);
            return result;
        } catch (Exception e) {
            log.error("반복 피드백 생성 실패", e);
            return Map.of("error", "반복 피드백 생성 실패", "raw", e.getMessage());
        }
    }

    // 항목별: 필러 filler
    public Map<String, String> generateFillerFeedback(String slideId, String transcript, int fillerCount,
            List<String> fillerWords) {
        String system = "너는 발표 코칭 전문가야. 말 속의 추임새(필러)에 대해 짧고 명확한 피드백을 한두 문장으로만 제공해. 문단 대신 짧은 코멘트 한두 문장만 써.";

        String user = String.format(
                "슬라이드: %s\n전사: %s\n필러 총 개수: %d\n필러 목록: %s\n\n" +
                        "요구:\n" +
                        "- 청중 입장에서 느껴지는 인상과 개선 방향을 한두 문장으로만 한국어 존댓말(○○해요, ○○해보세요)로 작성해줘.\n" +
                        "- 예를 들면 이런 톤이야: \"불필요한 추임새가 잦아 발표 흐름이 끊기는 느낌이 들었어요. 발표 전에 내용을 더 숙지하면 줄일 수 있을 거예요.\" 같은 느낌.\n" +
                        "- 리스트, 번호, 불릿(–, •, 1.) 없이 문장만 출력해.\n" +
                        "- 반드시 {\"filler\": \"...\"} 형태의 JSON 한 개만 반환해.",
                slideId, transcript, fillerCount, fillerWords);

        try {
            Map<String, Object> responseFormat = buildStringFieldResponseFormat("filler", "필러 관련 코멘트");
            String content = executeChatRequest(system, user, responseFormat);
            log.debug("OpenAI raw content (filler): {}", content);
            @SuppressWarnings("unchecked")
            Map<String, String> result = objectMapper.readValue(content, Map.class);
            return result;
        } catch (Exception e) {
            log.error("필러 피드백 생성 실패", e);
            return Map.of("error", "필러 피드백 생성 실패", "raw", e.getMessage());
        }
    }

    // 항목별: 정확도 accuracy
    public Map<String, String> generateAccuracyFeedback(String slideId, String transcript, String expectedKeyPoints) {
        String system = "너는 발표 코칭 전문가야. 내용의 정확도와 핵심 포인트 전달 여부에 대해 짧고 명확한 피드백을 한두 문장으로만 제공해. 문단 대신 짧은 코멘트 한두 문장만 써.";

        String user = String.format(
                "슬라이드: %s\n전사: %s\n기대 핵심포인트: %s\n\n" +
                        "요구:\n" +
                        "- 내용의 정확도가 부족해서 생길 수 있는 혼란과, 발표 전에 정보를 다시 점검하라는 메시지를 한두 문장으로만 한국어 존댓말로 써줘.\n" +
                        "- 예를 들면 이런 톤이야: \"내용의 정확도가 다소 부족해 전달에 혼란이 있을 수 있어요. 발표 전에 정보를 다시 한 번 점검해보면 좋겠어요.\" 같은 느낌.\n" +
                        "- 리스트, 번호, 불릿 없이 문장만 출력해.\n" +
                        "- 반드시 {\"accuracy\": \"...\"} 형태의 JSON 한 개만 반환해.",
                slideId, transcript, expectedKeyPoints);

        try {
            Map<String, Object> responseFormat = buildStringFieldResponseFormat("accuracy", "정확도 관련 코멘트");
            String content = executeChatRequest(system, user, responseFormat);
            log.debug("OpenAI raw content (accuracy): {}", content);
            @SuppressWarnings("unchecked")
            Map<String, String> result = objectMapper.readValue(content, Map.class);
            return result;
        } catch (Exception e) {
            log.error("정확도 피드백 생성 실패", e);
            return Map.of("error", "정확도 피드백 생성 실패", "raw", e.getMessage());
        }
    }

    // 항목별: 속도 pace
    public Map<String, String> generatePaceFeedback(String slideId, String transcript, double spm, double idealSpm) {
        String system = "너는 발표 코칭 전문가야. 말하기 속도에 대해 짧고 명확한 피드백을 한두 문장으로만 제공해. 문단 대신 짧은 코멘트 한두 문장만 써.";

        String user = String.format(
                "슬라이드: %s\n전사: %s\n현재 SPM: %.1f\n권장 SPM: %.1f\n\n" +
                        "요구:\n" +
                        "- 말하기 속도가 빠르거나 느려서 생기는 문제와, 어떻게 조정하면 좋을지 한두 문장으로만 한국어 존댓말로 코멘트를 써줘.\n" +
                        "- 예를 들면 이런 톤이야: \"조금 빠르게 말하는 경향이 있어 중요한 부분이 잘 들리지 않았어요. 핵심 내용에서는 속도를 천천히 조절해보면 좋아요.\" 같은 느낌.\n"
                        +
                        "- 리스트, 번호, 불릿 없이 문장만 출력해.\n" +
                        "- 반드시 {\"pace\": \"...\"} 형태의 JSON 한 개만 반환해.",
                slideId, transcript, spm, idealSpm);

        try {
            Map<String, Object> responseFormat = buildStringFieldResponseFormat("pace", "속도 관련 코멘트");
            String content = executeChatRequest(system, user, responseFormat);
            log.debug("OpenAI raw content (pace): {}", content);
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
     * issueType에는 사람이 읽을 수 있는 라벨(예: "말하기 속도", "불필요한 추임새")를 넘기는 걸 권장.
     */
    public String generateOverallFeedback(String sessionId, String issueType, String aggregatedComments) {
        String system = "너는 발표 코칭 전문가야. 전체 발표 연습 세션에 대해 가장 개선이 필요한 한 가지 항목만 짚어서, 짧은 총평 한두 문장을 제시해.";

        String user = String.format(
                "세션 ID: %s\n" +
                        "가장 낮은 점수 항목(사람이 읽을 수 있는 이름): %s\n" +
                        "해당 항목에 대한 슬라이드별 코멘트(원문):\n%s\n\n" +
                        "요구:\n" +
                        "- 위 코멘트들을 참고해서, \"%s\" 항목에 대한 전체 연습 세션 총평을 한국어 한두 문장으로만 작성해줘.\n" +
                        "- 예를 들면 이런 느낌이야: \"말하기 속도를 좀만 더 천천히 해서 전달력을 높여보아요!\" 처럼, 문제점 + 짧은 제안 + 약간의 응원까지 담긴 한 문장.\n" +
                        "- 리스트, 번호, 불릿 없이 문장만 반환해.\n" +
                        "- JSON이 아닌 순수 텍스트만, 최대 200토큰 이내로 출력해.",
                sessionId,
                issueType,
                aggregatedComments == null ? "" : aggregatedComments,
                issueType);

        try {
            String content = executeChatRequest(system, user);
            return content != null ? content.trim() : null;
        } catch (Exception e) {
            log.error("전체 요약 피드백 생성 실패: {}", e.getMessage());
            return null;
        }
    }

    // response_format helpers: build a strict json_schema expecting a single string
    // field
    private Map<String, Object> buildStringFieldResponseFormat(String fieldName, String description) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> fieldSpec = new HashMap<>();
        fieldSpec.put("type", "string");
        fieldSpec.put("description", description != null ? description : fieldName);
        properties.put(fieldName, fieldSpec);
        schema.put("properties", properties);
        schema.put("required", List.of(fieldName));
        schema.put("additionalProperties", false);

        Map<String, Object> jsonSchemaContainer = new HashMap<>();
        jsonSchemaContainer.put("name", fieldName + "Schema");
        jsonSchemaContainer.put("schema", schema);
        jsonSchemaContainer.put("strict", true);

        Map<String, Object> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", jsonSchemaContainer);
        return responseFormat;
    }

    // response_format for QnA feedback expecting expression + logic fields
    private Map<String, Object> buildQnaResponseFormat() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> expr = new HashMap<>();
        expr.put("type", "string");
        expr.put("description", "표현방식 피드백");
        Map<String, Object> logic = new HashMap<>();
        logic.put("type", "string");
        logic.put("description", "논리적 흐름 피드백");
        properties.put("expression", expr);
        properties.put("logic", logic);
        schema.put("properties", properties);
        schema.put("required", List.of("expression", "logic"));
        schema.put("additionalProperties", false);

        Map<String, Object> jsonSchemaContainer = new HashMap<>();
        jsonSchemaContainer.put("name", "qnaFeedbackSchema");
        jsonSchemaContainer.put("schema", schema);
        jsonSchemaContainer.put("strict", true);

        Map<String, Object> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", jsonSchemaContainer);
        return responseFormat;
    }
}

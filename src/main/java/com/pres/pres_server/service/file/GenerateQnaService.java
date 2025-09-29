package com.pres.pres_server.service.file;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.QnaAnswer;
import com.pres.pres_server.domain.QnaQuestion;
import com.pres.pres_server.dto.QnaDto;
import com.pres.pres_server.repository.QnaAnswerRepository;
import com.pres.pres_server.repository.QnaQuestionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class GenerateQnaService {
    @Value("${openai.api.key}")
    private String OPENAI_API_KEY;

    private final RestTemplate restTemplate;
    private final QnaQuestionRepository qnaQuestionRepository;
    private final QnaAnswerRepository qnaAnswerRepository;

    // extractedText를 받아서 예상 질문과 답변을 생성하는 메서드
    public QnaDto generateQna(String extractedText) {
        validateExtractedText(extractedText);

        // 1단계: 예상 질문들 생성
        String questionsPrompt = buildQuestionsPrompt(extractedText);
        String questionsResponse = callAiModel(questionsPrompt);

        // 2단계: 각 질문에 대한 답변 생성
        String answersPrompt = buildAnswersPrompt(extractedText, questionsResponse);
        String answersResponse = callAiModel(answersPrompt);

        // DTO 생성 및 반환
        QnaDto qna = new QnaDto();
        qna.setQuestion(questionsResponse);
        qna.setAnswer(answersResponse);
        return qna;
    }

    // extractedText와 CueCard를 받아서 예상 질문과 답변을 생성하고 DB에 저장하는 메서드
    @Transactional
    public List<QnaQuestion> generateAndSaveQna(String extractedText, CueCard cueCard) {
        validateExtractedText(extractedText);
        validateCueCard(cueCard);

        // 1단계: 예상 질문들 생성
        String questionsPrompt = buildQuestionsPrompt(extractedText);
        String questionsResponse = callAiModel(questionsPrompt);

        // 2단계: 각 질문에 대한 답변 생성
        String answersPrompt = buildAnswersPrompt(extractedText, questionsResponse);
        String answersResponse = callAiModel(answersPrompt);

        // 질문과 답변을 파싱하고 DB에 저장
        return parseAndSaveQna(questionsResponse, answersResponse, cueCard, extractedText);
    }

    // 텍스트 검증 메서드
    private void validateExtractedText(String extractedText) {
        if (extractedText == null || extractedText.trim().isEmpty()) {
            throw new IllegalArgumentException("추출된 텍스트가 비어있습니다.");
        }
    }

    // CueCard 검증 메서드
    private void validateCueCard(CueCard cueCard) {
        if (cueCard == null) {
            throw new IllegalArgumentException("CueCard가 null입니다.");
        }
        if (cueCard.getCueId() == null) {
            throw new IllegalArgumentException("CueCard ID가 null입니다.");
        }
    }

    // 기존 CueCard의 저장된 QnA 조회
    public List<QnaQuestion> getSavedQna(CueCard cueCard) {
        return qnaQuestionRepository.findActiveByCueCard(cueCard);
    }

    // QnA 파싱 및 저장 로직
    private List<QnaQuestion> parseAndSaveQna(String questionsResponse, String answersResponse,
            CueCard cueCard, String extractedText) {
        List<QnaQuestion> savedQuestions = new ArrayList<>();

        // 응답 검증
        if (questionsResponse == null || questionsResponse.trim().isEmpty()) {
            System.err.println("질문 응답이 비어있습니다.");
            return savedQuestions;
        }
        if (answersResponse == null || answersResponse.trim().isEmpty()) {
            System.err.println("답변 응답이 비어있습니다.");
            return savedQuestions;
        }

        try {
            // 질문들을 파싱 (실제 구현에서는 더 정교한 파싱 로직 필요)
            String[] questionBlocks = questionsResponse.split("\\*\\*질문 \\d+:");
            String[] answerBlocks = answersResponse.split("\\*\\*질문 \\d+에 대한 답변:");

            // 파싱 결과 검증
            if (questionBlocks.length <= 1) {
                System.err.println("질문 파싱 실패: 예상 형식과 다름");
                return savedQuestions;
            }
            if (answerBlocks.length <= 1) {
                System.err.println("답변 파싱 실패: 예상 형식과 다름");
                return savedQuestions;
            }

            for (int i = 1; i < questionBlocks.length && i < answerBlocks.length; i++) {
                try {
                    // 개별 질문-답변 저장
                    String questionText = questionBlocks[i].trim();
                    String answerText = answerBlocks[i].trim();

                    // 내용 검증
                    if (questionText.isEmpty() || answerText.isEmpty()) {
                        System.err.println("질문 또는 답변이 비어있음 (인덱스: " + i + ")");
                        continue;
                    }

                    // 질문 저장
                    QnaQuestion question = new QnaQuestion();
                    question.setCueCardId(cueCard);
                    question.setBody(questionText);
                    question.setOrigin("AI_GENERATED");
                    question.setModel("gpt-4o-mini");
                    question.setStatus("ACTIVE");
                    question.setPrompt_hash(generatePromptHash(extractedText));
                    question.setConfidence(0.8f); // AI 생성은 기본 confidence
                    question.setCreated_at(LocalDateTime.now());
                    question.setUpdated_at(LocalDateTime.now());

                    QnaQuestion savedQuestion = qnaQuestionRepository.save(question);
                    savedQuestions.add(savedQuestion);

                    // 해당 질문에 대한 답변 저장
                    QnaAnswer answer = new QnaAnswer();
                    answer.setQnaQuestion(savedQuestion);
                    answer.setAnswerType("AI_GENERATED");
                    answer.setBody(answerText);
                    answer.setOrigin("AI_GENERATED");
                    answer.setModel("gpt-4o-mini");
                    answer.setConfidence(0.8f);
                    answer.setCreatedAt(LocalDateTime.now());
                    answer.setUpdatedAt(LocalDateTime.now());

                    qnaAnswerRepository.save(answer);
                } catch (Exception e) {
                    System.err.println("개별 QnA 저장 실패 (인덱스: " + i + "): " + e.getMessage());
                    // 개별 실패는 전체를 중단시키지 않음
                }
            }
        } catch (Exception e) {
            System.err.println("QnA 파싱 및 저장 실패: " + e.getMessage());
            e.printStackTrace();
            // 트랜잭션이 있으므로 예외 발생 시 롤백됨
            throw new RuntimeException("QnA 저장 중 오류가 발생했습니다.", e);
        }

        if (savedQuestions.isEmpty()) {
            System.err.println("저장된 QnA가 없습니다. 파싱 또는 저장 과정에서 문제가 발생했을 수 있습니다.");
        }

        return savedQuestions;
    }

    // 프롬프트 해시 생성 (중복 생성 방지용)
    private String generatePromptHash(String extractedText) {
        return String.valueOf(extractedText.hashCode());
    }

    // 기존 QnA 삭제 (재생성 전)
    @Transactional
    public void clearExistingQna(CueCard cueCard) {
        List<QnaQuestion> existingQuestions = qnaQuestionRepository.findByCueCardId(cueCard);
        for (QnaQuestion question : existingQuestions) {
            // 관련 답변들도 함께 삭제
            List<QnaAnswer> answers = qnaAnswerRepository.findByQnaQuestion(question);
            qnaAnswerRepository.deleteAll(answers);
        }
        qnaQuestionRepository.deleteAll(existingQuestions);
    }

    // CueCard의 QnA를 강제 재생성 (기존 데이터 삭제 후 새로 생성)
    @Transactional
    public List<QnaQuestion> regenerateQna(String extractedText, CueCard cueCard) {
        // 기존 QnA 삭제
        clearExistingQna(cueCard);

        // 새로 생성
        return generateAndSaveQna(extractedText, cueCard);
    }

    // 예상 질문 생성 프롬프트
    private String buildQuestionsPrompt(String extractedText) {
        return """
                너는 대학생 발표자료를 분석해서 청중이 물어볼 수 있는 예상 질문을 생성하는 전문가야.

                아래 발표 자료 내용을 분석해서, 청중이 발표 후에 물어볼 만한 **핵심 질문 5개**를 생성해줘.

                ---
                [지침]
                [1] 질문 유형
                - **내용 이해 확인**: 발표 내용의 핵심을 제대로 이해했는지 확인하는 질문
                - **세부사항 문의**: 발표에서 다루지 않은 구체적인 세부사항에 대한 질문
                - **실무 적용**: 발표 내용을 실제로 어떻게 활용할 수 있는지에 대한 질문
                - **비판적 사고**: 발표 내용에 대한 다른 관점이나 한계점을 묻는 질문
                - **확장 질문**: 발표 주제와 관련된 추가적인 영역에 대한 질문

                [2] 질문 작성 원칙
                - 발표 내용을 **정확히 이해한 사람**이 물어볼 법한 수준 높은 질문
                - **"네/아니오"**로 답할 수 있는 단순한 질문은 피해주세요
                - 발표자가 **충분히 답변할 수 있는** 범위 내의 질문
                - **구체적이고 명확한** 질문 (애매하거나 추상적이지 않게)
                - 각 질문은 **서로 다른 관점**에서 접근

                [3] 출력 형식
                **질문 1: [내용 이해 확인]**
                구체적인 질문 내용

                **질문 2: [세부사항 문의]**
                구체적인 질문 내용

                **질문 3: [실무 적용]**
                구체적인 질문 내용

                **질문 4: [비판적 사고]**
                구체적인 질문 내용

                **질문 5: [확장 질문]**
                구체적인 질문 내용

                [4] 추가 지침
                - 발표 주제와 내용에 **직접적으로 관련된** 질문만 생성
                - 발표자료에 **근거가 있는** 질문 위주로 작성
                - 질문의 **난이도는 대학생 수준**에 맞춤
                - **형식을 정확히** 지켜서 출력해주세요

                ---
                [발표 자료 내용]
                """ + extractedText + """
                ---
                [예상 질문 생성]
                """;
    }

    // 예상 답변 생성 프롬프트
    private String buildAnswersPrompt(String extractedText, String questions) {
        return """
                너는 발표자가 되어 청중의 질문에 대한 이상적인 답변을 생성하는 전문가야.

                발표 자료 내용과 예상 질문을 바탕으로, 각 질문에 대한 **발표자의 이상적인 답변**을 생성해줘.

                ---
                [지침]
                [1] 답변 작성 원칙
                - 발표 자료 내용에 **근거를 두고** 답변
                - **구체적이고 정확한** 정보 제공
                - **자신감 있고 전문적인** 어조로 작성
                - 필요시 **추가 설명이나 예시** 포함
                - **"잘 모르겠습니다"**라는 답변은 지양

                [2] 답변 구조
                - **핵심 답변**: 질문에 대한 직접적인 답변
                - **근거 제시**: 발표 자료의 어떤 부분을 근거로 하는지
                - **부연 설명**: 필요시 추가적인 설명이나 예시
                - **마무리**: 질문에 대한 완결성 있는 마무리

                [3] 답변 어조
                - **공식적이고 학술적인** 발표체 ("~입니다", "~합니다")
                - **논리적이고 체계적인** 설명
                - **자신감 있는** 어조 (확신을 가지고 답변)
                - **친근하면서도 전문적인** 느낌

                [4] 출력 형식
                **질문 1에 대한 답변:**
                구체적이고 상세한 답변 내용 (2-3문장으로 충분히 설명)

                **질문 2에 대한 답변:**
                구체적이고 상세한 답변 내용 (2-3문장으로 충분히 설명)

                **질문 3에 대한 답변:**
                구체적이고 상세한 답변 내용 (2-3문장으로 충분히 설명)

                **질문 4에 대한 답변:**
                구체적이고 상세한 답변 내용 (2-3문장으로 충분히 설명)

                **질문 5에 대한 답변:**
                구체적이고 상세한 답변 내용 (2-3문장으로 충분히 설명)

                [5] 추가 지침
                - 각 답변은 **질문의 의도를 정확히 파악**하고 답변
                - **발표 자료를 벗어나지 않는** 범위에서 답변
                - **형식을 정확히** 지켜서 출력해주세요

                ---
                [발표 자료 내용]
                """ + extractedText + """

                [예상 질문들]
                """ + questions + """
                ---
                [이상적인 답변 생성]
                """;
    }

    // OpenAI API 호출 메서드
    private String callAiModel(String prompt) {
        // API 키 검증
        if (OPENAI_API_KEY == null || OPENAI_API_KEY.trim().isEmpty()) {
            throw new IllegalStateException("OpenAI API 키가 설정되지 않았습니다.");
        }

        String url = "https://api.openai.com/v1/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(OPENAI_API_KEY);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o-mini");
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "너는 발표 Q&A를 생성하는 전문가야."),
                Map.of("role", "user", "content", prompt)));
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 2500);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("OpenAI API 응답이 비어있습니다.");
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");

            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("OpenAI API 응답에 선택지가 없습니다.");
            }

            Map<String, Object> firstChoice = choices.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");

            if (message == null) {
                throw new RuntimeException("OpenAI API 응답에 메시지가 없습니다.");
            }

            String content = (String) message.get("content");
            if (content == null || content.trim().isEmpty()) {
                throw new RuntimeException("OpenAI API 응답 내용이 비어있습니다.");
            }

            return content.trim();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("OpenAI API 클라이언트 오류 (4xx): " + e.getMessage());
            e.printStackTrace();
            return "API 요청에 문제가 있습니다. 잠시 후 다시 시도해주세요.";
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            System.err.println("OpenAI API 서버 오류 (5xx): " + e.getMessage());
            e.printStackTrace();
            return "OpenAI 서버에 일시적인 문제가 있습니다. 잠시 후 다시 시도해주세요.";
        } catch (org.springframework.web.client.ResourceAccessException e) {
            System.err.println("네트워크 연결 오류: " + e.getMessage());
            e.printStackTrace();
            return "네트워크 연결을 확인하고 다시 시도해주세요.";
        } catch (Exception e) {
            System.err.println("Q&A 생성 실패: " + e.getMessage());
            e.printStackTrace();
            return "Q&A 생성에 실패했습니다. 잠시 후 다시 시도해주세요.";
        }
    }
}

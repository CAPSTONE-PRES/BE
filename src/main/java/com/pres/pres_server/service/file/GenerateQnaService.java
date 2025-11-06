package com.pres.pres_server.service.file;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pres.pres_server.dto.qna.QnaGenerateResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.QnaAnswer;
import com.pres.pres_server.domain.QnaQuestion;
import com.pres.pres_server.dto.qna.QnaDetailDto;
import com.pres.pres_server.dto.qna.QnaListDto;
import com.pres.pres_server.exception.QnaGenerationException;
import com.pres.pres_server.repository.QnaAnswerRepository;
import com.pres.pres_server.repository.QnaQuestionRepository;
import com.pres.pres_server.repository.PresentationFileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateQnaService {
    @Value("${openai.api.key}")
    private String OPENAI_API_KEY;

    private final RestTemplate restTemplate;
    private final QnaQuestionRepository qnaQuestionRepository;
    private final QnaAnswerRepository qnaAnswerRepository;
    private final PresentationFileRepository presentationFileRepository;
    private final ExtractTextService extractTextService;

    // === 핵심 API 메서드들 ===

    // 1. Q&A 미리보기 (생성만, 저장 안함)
    public Map<String, String> generateQna(String extractedText) {
        return generateQnaWithoutTransaction(extractedText);
    }

    // Q&A 생성 (트랜잭션 없음 - 외부 API 호출)
    private Map<String, String> generateQnaWithoutTransaction(String extractedText) {
        validateExtractedText(extractedText);

        // Q&A 생성 (단일 호출로 변경)
        String qnaPrompt = buildQnaPrompt(extractedText);
        String qnaResponse = callAiModel(qnaPrompt);
        log.info("=== AI Q&A 응답 ===");
        log.info("{}", qnaResponse);

        // 생성된 내용 반환 (저장하지 않음)
        Map<String, String> result = new HashMap<>();
        result.put("qna", qnaResponse);
        return result;
    }

    // 2. Q&A 생성 및 저장 (메인 API)
    public List<QnaQuestion> generateAndSaveQna(String extractedText, Long fileId) {
        log.info("Q&A 생성 및 저장 시작 - fileId: {}", fileId);
        validateFileId(fileId);

        // 1단계: Q&A 생성 (트랜잭션 외부에서 실행)
        Map<String, String> generatedContent;
        try {
            generatedContent = generateQnaWithoutTransaction(extractedText);
        } catch (Exception e) {
            log.error("Q&A 생성 실패 - fileId: {}, 오류: {}", fileId, e.getMessage(), e);
            throw new QnaGenerationException("Q&A 생성에 실패했습니다: " + e.getMessage(), e);
        }

        // 2단계: DB 저장 (파싱 포함)
        return saveQnaToDatabase(fileId, extractedText, generatedContent);
    }

    // DB 저장만 담당하는 트랜잭션 메서드
    @Transactional(rollbackFor = Exception.class)
    public List<QnaQuestion> saveQnaToDatabase(Long fileId, String extractedText,
            Map<String, String> generatedContent) {
        // 기존 Q&A 삭제 (중복 방지)
        clearExistingQnaInternal(fileId);

        // PresentationFile 조회
        PresentationFile presentationFile = presentationFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다: " + fileId));

        // DB 저장 (개선된 파싱 로직 사용)
        String qnaResponse = generatedContent.get("qna");
        List<QnaQuestion> result = parseAndSaveQnaFromJson(qnaResponse, presentationFile, extractedText);

        // 파싱 실패 시 명확한 예외 발생
        if (result.isEmpty()) {
            throw new QnaGenerationException("AI 응답을 파싱할 수 없습니다. 응답 형식이 올바르지 않거나 빈 응답입니다.");
        }

        log.info("Q&A 생성 및 저장 완료 - fileId: {}, 질문 수: {}", fileId, result.size());
        return result;
    }

    /**
     * Q&A 생성 및 저장 - 응답 DTO까지 포함
     * Controller에서는 이 메서드만 호출하면 됨
     */
    public QnaGenerateResponseDto generateAndSaveQnaWithResponse(Long fileId) {
        log.info("Q&A 생성 및 저장 시작 - fileId: {}", fileId);

        String fullText = extractTextService.getFullTextByFileId(fileId);
        var savedQuestions = generateAndSaveQna(fullText, fileId);
        QnaListDto qnaListDto = getSavedQnaAsDto(fileId);

        return QnaGenerateResponseDto.builder()
                .success(true)
                .message(String.format("Q&A가 성공적으로 생성되고 저장되었습니다. (질문 %d개 생성)",
                        savedQuestions.size()))
                .qnaList(qnaListDto)
                .build();
    }

    /**
     * Q&A 재생성 - 응답 DTO까지 포함
     */
    public QnaGenerateResponseDto regenerateQnaWithResponse(Long fileId) {
        log.info("Q&A 재생성 시작 - fileId: {}", fileId);

        String fullText = extractTextService.getFullTextByFileId(fileId);
        var regeneratedQuestions = regenerateQna(fullText, fileId);
        QnaListDto qnaListDto = getSavedQnaAsDto(fileId);

        return QnaGenerateResponseDto.builder()
                .success(true)
                .message(String.format("Q&A가 성공적으로 재생성되었습니다. (질문 %d개 생성)",
                        regeneratedQuestions.size()))
                .qnaList(qnaListDto)
                .build();
    }

    /**
     * Q&A 미리보기 - 응답 Map까지 포함
     */
    public Map<String, Object> previewQnaWithResponse(Long fileId) {
        String fullText = extractTextService.getFullTextByFileId(fileId);
        Map<String, String> qnaContent = generateQna(fullText);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Q&A 미리보기가 생성되었습니다.");
        response.put("preview", qnaContent);

        return response;
    }

    // JSON 기반 QnA 파싱 및 저장 로직 (개선된 버전)
    private List<QnaQuestion> parseAndSaveQnaFromJson(String qnaResponse, PresentationFile presentationFile,
            String extractedText) {
        log.info("=== Q&A 파싱 및 저장 시작 ===");
        log.debug("트랜잭션 활성 상태 (시작): {}",
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());

        List<QnaQuestion> savedQuestions = new ArrayList<>();

        // 응답 검증
        if (qnaResponse == null || qnaResponse.trim().isEmpty()) {
            log.warn("Q&A 응답이 비어있습니다");
            throw new QnaGenerationException("AI 응답이 비어있습니다");
        }

        try {
            log.info("=== JSON 파싱 시작 ===");
            log.info("Q&A 응답 전체 내용:\n{}", qnaResponse);

            // Jackson ObjectMapper를 사용한 JSON 파싱 (UTF-8 인코딩 및 제어문자 허용)
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);

            // 이스케이프된 JSON 문자열 처리 (확실한 방법)
            String cleanedResponse = qnaResponse.trim();

            // 응답이 따옴표로 감싸져 있고 이스케이프 문자가 포함된 경우 처리
            if (cleanedResponse.startsWith("\"") && cleanedResponse.endsWith("\"")) {
                log.info("이스케이프된 JSON 문자열 감지, 정리 시작");
                // 앞뒤 따옴표 제거
                cleanedResponse = cleanedResponse.substring(1, cleanedResponse.length() - 1);
                // 이스케이프 문자 복원
                cleanedResponse = cleanedResponse
                        .replace("\\\"", "\"") // 이스케이프된 따옴표
                        .replace("\\n", "\n") // 이스케이프된 줄바꿈
                        .replace("\\r", "\r") // 이스케이프된 캐리지 리턴
                        .replace("\\t", "\t") // 이스케이프된 탭
                        .replace("\\/", "/") // 이스케이프된 슬래시
                        .replace("\\\\", "\\"); // 이스케이프된 백슬래시 (마지막에 처리)

                log.info("이스케이프 정리 완료");
                log.debug("정리된 JSON:\n{}", cleanedResponse);
            }

            JsonNode rootNode = objectMapper.readTree(cleanedResponse);
            JsonNode itemsNode = rootNode.get("items");

            if (itemsNode == null || !itemsNode.isArray()) {
                log.error("JSON 형식 오류: 'items' 배열을 찾을 수 없음");
                log.error("파싱된 JSON 구조: {}", rootNode.toString());
                throw new QnaGenerationException("JSON 형식 오류: 'items' 배열을 찾을 수 없습니다");
            }

            log.info("✅ JSON 파싱 성공! 파싱된 아이템 수: {}", itemsNode.size());

            // 각 Q&A 아이템 처리
            for (JsonNode item : itemsNode) {
                try {
                    int index = item.get("index").asInt();
                    String category = item.get("category").asText();
                    String questionText = item.get("question").asText();
                    String answerText = item.get("answer").asText();

                    log.info("처리 중인 Q{} - 카테고리: [{}]", index, category);
                    log.info("질문: {}", questionText.substring(0, Math.min(100, questionText.length())));
                    log.info("답변: {}", answerText.substring(0, Math.min(100, answerText.length())));

                    // 내용 검증
                    if (questionText.trim().isEmpty() || answerText.trim().isEmpty()) {
                        log.warn("Q{}: 질문 또는 답변이 비어있음", index);
                        continue;
                    }

                    log.info("Q{} 저장 시작", index);

                    // 질문 저장
                    QnaQuestion question = new QnaQuestion();
                    question.setPresentationFile(presentationFile);
                    question.setBody(questionText.trim());
                    question.setOrigin("AI_GENERATED");
                    question.setModel("gpt-4o-mini");
                    question.setStatus("ACTIVE");
                    question.setPrompt_hash(generatePromptHash(extractedText));
                    question.setConfidence(0.8f);
                    question.setCreated_at(LocalDateTime.now());
                    question.setUpdated_at(LocalDateTime.now());

                    // 질문 엔티티 유효성 검사 로그
                    log.debug("질문 엔티티 생성 완료 - presentationFile ID: {}, body length: {}",
                            presentationFile.getFileId(), questionText.trim().length());

                    log.info("Q{} 질문 저장 중...", index);
                    QnaQuestion savedQuestion = qnaQuestionRepository.save(question);
                    log.info("Q{} 질문 저장 완료, ID: {}", index, savedQuestion.getQnaId());

                    // 트랜잭션 상태 확인
                    log.debug("트랜잭션 활성 상태: {}",
                            org.springframework.transaction.support.TransactionSynchronizationManager
                                    .isActualTransactionActive());

                    savedQuestions.add(savedQuestion);

                    // 해당 질문에 대한 답변 저장
                    QnaAnswer answer = new QnaAnswer();
                    answer.setQnaQuestion(savedQuestion);
                    answer.setAnswerType("AI_GENERATED");
                    answer.setBody(answerText.trim());
                    answer.setOrigin("AI_GENERATED");
                    answer.setModel("gpt-4o-mini");
                    answer.setConfidence(0.8f);
                    answer.setCreatedAt(LocalDateTime.now());
                    answer.setUpdatedAt(LocalDateTime.now());

                    // 답변 엔티티 유효성 검사 로그
                    log.debug("답변 엔티티 생성 완료 - questionId: {}, body length: {}",
                            savedQuestion.getQnaId(), answerText.trim().length());

                    log.info("Q{} 답변 저장 중...", index);
                    qnaAnswerRepository.save(answer);
                    log.info("Q{} 답변 저장 완료", index);

                    log.info("✅ Q{} 저장 완료 - 질문: {}", index,
                            questionText.substring(0, Math.min(50, questionText.length())) + "...");

                } catch (Exception e) {
                    log.error("개별 Q&A 처리 실패: {}", e.getMessage(), e);
                    log.error("실패한 아이템의 index: {}", item.has("index") ? item.get("index").asText() : "N/A");
                    // 개별 실패는 전체를 중단시키지 않음
                }
            }

        } catch (Exception e) {
            log.error("JSON 파싱 및 Q&A 저장 중 오류 발생", e);
            throw new QnaGenerationException("JSON 파싱에 실패했습니다: " + e.getMessage(), e);
        }

        if (savedQuestions.isEmpty()) {
            log.error("❌ 저장된 Q&A가 없습니다. JSON 파싱 또는 저장 과정에서 문제가 발생했을 수 있습니다.");
            log.debug("트랜잭션 활성 상태 (실패 시): {}",
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .isActualTransactionActive());
            throw new QnaGenerationException("파싱된 Q&A가 없습니다. AI 응답 형식을 확인해주세요.");
        } else {
            log.info("🎉 Q&A 저장 완료! 총 {}개 질문이 성공적으로 저장되었습니다!", savedQuestions.size());
            log.debug("트랜잭션 활성 상태 (성공 시): {}",
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .isActualTransactionActive());
        }

        return savedQuestions;
    }

    // 3. 저장된 Q&A 조회 (DTO 형태)
    public QnaListDto getSavedQnaAsDto(Long fileId) {
        validateFileId(fileId);
        List<QnaQuestion> questions = qnaQuestionRepository.findActiveByFileId(fileId);

        QnaListDto qnaListDto = new QnaListDto();
        qnaListDto.setFileId(fileId);

        if (questions.isEmpty()) {
            qnaListDto.setQuestions(new ArrayList<>());
            return qnaListDto;
        }

        List<QnaDetailDto> questionDtos = new ArrayList<>();

        for (QnaQuestion question : questions) {
            // 해당 질문의 답변들 조회
            var answers = qnaAnswerRepository.findByQnaQuestion(question);

            QnaDetailDto questionDto = new QnaDetailDto();
            questionDto.setQuestionId(question.getQnaId());
            questionDto.setQuestionBody(question.getBody());
            questionDto.setOrigin(question.getOrigin());
            questionDto.setModel(question.getModel());
            questionDto.setConfidence(question.getConfidence());
            questionDto.setCreatedAt(question.getCreated_at());

            // 답변들을 DTO로 변환
            List<QnaDetailDto.QnaAnswerDto> answerDtos = new ArrayList<>();
            for (QnaAnswer answer : answers) {
                QnaDetailDto.QnaAnswerDto answerDto = new QnaDetailDto.QnaAnswerDto();
                answerDto.setAnswerId(answer.getAnswerId());
                answerDto.setAnswerBody(answer.getBody());
                answerDto.setAnswerType(answer.getAnswerType());
                answerDto.setOrigin(answer.getOrigin());
                answerDto.setModel(answer.getModel());
                answerDto.setConfidence(answer.getConfidence());
                answerDto.setCreatedAt(answer.getCreatedAt());
                answerDtos.add(answerDto);
            }
            questionDto.setAnswers(answerDtos);
            questionDtos.add(questionDto);
        }

        qnaListDto.setQuestions(questionDtos);
        return qnaListDto;
    }

    // 4. 기존 Q&A 재생성 (사용자가 다시 생성 요청할 때)
    public List<QnaQuestion> regenerateQna(String extractedText, Long fileId) {
        log.info("Q&A 재생성 시작 - fileId: {}", fileId);
        validateFileId(fileId);

        // 1단계: Q&A 새로 생성 (트랜잭션 외부에서 실행)
        Map<String, String> generatedContent;
        try {
            generatedContent = generateQnaWithoutTransaction(extractedText);
        } catch (Exception e) {
            log.error("Q&A 재생성 실패 - fileId: {}, 오류: {}", fileId, e.getMessage(), e);
            throw new QnaGenerationException("Q&A 재생성에 실패했습니다: " + e.getMessage(), e);
        }

        // 2단계: DB 저장 (파싱 포함)
        return saveQnaToDatabase(fileId, extractedText, generatedContent);
    }

    // === 유틸리티 메서드들 ===

    private void validateExtractedText(String extractedText) {
        if (extractedText == null || extractedText.trim().isEmpty()) {
            throw new IllegalArgumentException("추출된 텍스트가 비어있습니다.");
        }
    }

    private void validateFileId(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 파일 ID입니다: " + fileId);
        }
    }

    // 프롬프트 해시 생성 (중복 생성 방지용)
    private String generatePromptHash(String extractedText) {
        return String.valueOf(extractedText.hashCode());
    }

    // 기존 QnA 삭제 (내부 사용 - 트랜잭션 없음)
    private void clearExistingQnaInternal(Long fileId) {
        List<QnaQuestion> existingQuestions = qnaQuestionRepository.findByPresentationFile_FileId(fileId);
        if (!existingQuestions.isEmpty()) {
            log.info("기존 Q&A 삭제 중 - fileId: {}, 질문 수: {}", fileId, existingQuestions.size());
            for (QnaQuestion question : existingQuestions) {
                // 관련 답변들도 함께 삭제
                List<QnaAnswer> answers = qnaAnswerRepository.findByQnaQuestion(question);
                qnaAnswerRepository.deleteAll(answers);
            }
            qnaQuestionRepository.deleteAll(existingQuestions);
        }
    }

    // === 프롬프트 빌더 메서드들 ===

    // JSON 형식 Q&A 생성 프롬프트
    private String buildQnaPrompt(String extractedText) {
        return """
                너는 대학생 발표자료를 분석해서 청중이 물어볼 수 있는 예상 질문과 답변을 생성하는 전문가야.

                아래 발표 자료 내용을 분석해서, 청중이 발표 후에 물어볼 만한 핵심 질문 5개와 각각에 대한 이상적인 답변을 생성해줘.

                ---
                [지침]
                [1] 질문 유형 (각 질문은 다음 5가지 카테고리 중 정확히 하나씩 포함해야 함)
                - "내용 이해 확인": 발표 내용의 핵심을 제대로 이해했는지 확인하는 질문
                - "세부사항 문의": 발표에서 다루지 않은 구체적인 세부사항에 대한 질문
                - "실무 적용": 발표 내용을 실제로 어떻게 활용할 수 있는지에 대한 질문
                - "비판적 사고": 발표 내용에 대한 다른 관점이나 한계점을 묻는 질문
                - "확장 질문": 발표 주제와 관련된 추가적인 영역에 대한 질문

                [2] 질문 작성 원칙
                - 발표 내용을 정확히 이해한 사람이 물어볼 법한 수준 높은 질문
                - "네/아니오"로 답할 수 있는 단순한 질문은 피해주세요
                - 발표자가 충분히 답변할 수 있는 범위 내의 질문
                - 구체적이고 명확한 질문 (애매하거나 추상적이지 않게)

                [3] 답변 작성 원칙
                - 발표 자료 내용에 근거를 두고 답변
                - 구체적이고 정확한 정보 제공 (2-3문장으로 충분히 설명)
                - 자신감 있고 전문적인 어조로 작성 ("~입니다", "~합니다")
                - 발표 자료를 벗어나지 않는 범위에서 답변

                [4] 출력 형식 (⚠️ 반드시 아래 JSON 형식으로만 응답하세요)
                {
                  "items": [
                    {
                      "index": 1,
                      "category": "내용 이해 확인",
                      "question": "구체적인 질문 내용",
                      "answer": "구체적이고 상세한 답변 내용"
                    },
                    {
                      "index": 2,
                      "category": "세부사항 문의",
                      "question": "구체적인 질문 내용",
                      "answer": "구체적이고 상세한 답변 내용"
                    },
                    {
                      "index": 3,
                      "category": "실무 적용",
                      "question": "구체적인 질문 내용",
                      "answer": "구체적이고 상세한 답변 내용"
                    },
                    {
                      "index": 4,
                      "category": "비판적 사고",
                      "question": "구체적인 질문 내용",
                      "answer": "구체적이고 상세한 답변 내용"
                    },
                    {
                      "index": 5,
                      "category": "확장 질문",
                      "question": "구체적인 질문 내용",
                      "answer": "구체적이고 상세한 답변 내용"
                    }
                  ]
                }

                ⚠️ 중요 지침:
                - 반드시 위 JSON 형식으로만 응답하세요
                - “코드펜스(```) 금지”
                - 추가 설명이나 다른 텍스트는 포함하지 마세요
                - index는 1부터 5까지 순서대로
                - category는 위 5가지 중 정확히 하나씩
                - question과 answer는 비어있지 않게 작성
                - JSON 문법을 정확히 지켜주세요

                ---
                [발표 자료 내용]
                """ + extractedText + """
                ---
                [JSON 형식 Q&A 생성]
                """;
    }

    // OpenAI API 호출 메서드
    private String callAiModel(String prompt) {
        // API 키 검증
        if (OPENAI_API_KEY == null || OPENAI_API_KEY.trim().isEmpty()) {
            log.error("OpenAI API 키가 설정되지 않았습니다");
            throw new IllegalStateException("OpenAI API 키가 설정되지 않았습니다.");
        }

        String url = "https://api.openai.com/v1/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(OPENAI_API_KEY);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o-mini");
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "너는 발표 Q&A를 생성하는 전문가야. 반드시 JSON 형식으로만 응답해야 한다."),
                Map.of("role", "user", "content", prompt)));
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 2500);

        // JSON 형식 강제 설정
        Map<String, Object> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        requestBody.put("response_format", responseFormat);

        // 요청 확인 로그
        log.info("🚀 OpenAI API 요청 - 모델: {}, JSON mode: active", requestBody.get("model"));
        log.debug("요청 본문: {}", requestBody);

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

            log.debug("OpenAI API 호출 성공, 응답 길이: {}", content.length());
            return content.trim();

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("OpenAI API 클라이언트 오류 (4xx): {}", e.getMessage());
            throw new RuntimeException("AI 서비스 요청에 문제가 있습니다: " + e.getMessage(), e);
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("OpenAI API 서버 오류 (5xx): {}", e.getMessage());
            throw new RuntimeException("AI 서비스에 일시적인 문제가 있습니다: " + e.getMessage(), e);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("네트워크 연결 오류: {}", e.getMessage());
            throw new RuntimeException("네트워크 연결을 확인해 주세요: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Q&A 생성 중 예상치 못한 오류 발생", e);
            throw new RuntimeException("Q&A 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 순차적으로 질문 조회 (연습 세션용)
     * 
     * @param fileId 파일 ID
     * @param index  질문 인덱스 (0부터 시작, 0~4)
     * @return 해당 인덱스의 질문
     */
    public com.pres.pres_server.dto.qna.QnaQuestionDto getQuestionByIndex(Long fileId, int index) {
        validateFileId(fileId);

        List<QnaQuestion> questions = qnaQuestionRepository.findActiveByFileId(fileId);

        if (questions.isEmpty()) {
            throw new IllegalStateException("저장된 질문이 없습니다. fileId: " + fileId);
        }

        if (index < 0 || index >= questions.size()) {
            throw new IllegalArgumentException(
                    String.format("유효하지 않은 인덱스입니다. index: %d, 전체 질문 수: %d", index, questions.size()));
        }

        // 순차적 선택
        QnaQuestion question = questions.get(index);

        return com.pres.pres_server.dto.qna.QnaQuestionDto.builder()
                .questionId(question.getQnaId())
                .questionBody(question.getBody())
                .build();
    }

    /**
     * 모범 답변 조회 (내부용)
     */
    public QnaAnswer getIdealAnswer(Long questionId) {
        QnaQuestion question = qnaQuestionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("질문을 찾을 수 없습니다: " + questionId));

        return qnaAnswerRepository
                .findFirstByQnaQuestionAndAnswerType(question, "AI_GENERATED")
                .orElseThrow(() -> new IllegalArgumentException("모범 답변을 찾을 수 없습니다: " + questionId));
    }

}

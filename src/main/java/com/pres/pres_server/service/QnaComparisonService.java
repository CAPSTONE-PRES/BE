package com.pres.pres_server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pres.pres_server.domain.*;
import com.pres.pres_server.dto.qna.QnaAnswerResponseDto;
import com.pres.pres_server.dto.qna.QnaComparisonDto;
import com.pres.pres_server.repository.*;
import com.pres.pres_server.service.ai.OpenAIFeedbackService;
import com.pres.pres_server.service.analyse.TextAnalysisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class QnaComparisonService {

        private final WhisperService whisperService;
        private final QnaAnswerRepository qnaAnswerRepository;
        private final QnaQuestionRepository qnaQuestionRepository;
        private final QnaAnswerComparisonRepository qnaAnswerComparisonRepository;
        private final PracticeSessionRepository practiceSessionRepository;
        private final OpenAIFeedbackService openAIFeedbackService;
        private final ObjectMapper objectMapper;
        private final PlatformTransactionManager transactionManager;

        /**
         * 사용자의 음성 답변을 텍스트로 변환하고 저장
         */
        @Transactional
        public QnaAnswerResponseDto submitAnswer(Long sessionId, Long questionId, MultipartFile audioFile)
                        throws Exception {
                log.info("📝 QnA 답변 제출 - sessionId: {}, questionId: {}", sessionId, questionId);

                PracticeSession session = practiceSessionRepository.findById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다"));

                QnaQuestion question = qnaQuestionRepository.findById(questionId)
                                .orElseThrow(() -> new IllegalArgumentException("질문을 찾을 수 없습니다"));

                File wavFile = convertToWav(audioFile);

                String sttText = whisperService.transcribe(wavFile);
                log.info("✅ STT 변환 완료 - length: {}", sttText.length());

                QnaAnswer userAnswer = new QnaAnswer();
                userAnswer.setQnaQuestion(question);
                userAnswer.setPracticeSession(session);
                userAnswer.setAnswerType("user");
                userAnswer.setBody(sttText);
                userAnswer.setOrigin("USER_SPEECH");
                userAnswer.setRawStt(sttText);
                userAnswer.setCreatedAt(LocalDateTime.now());
                userAnswer.setUpdatedAt(LocalDateTime.now());

                QnaAnswer savedAnswer = qnaAnswerRepository.save(userAnswer);
                log.info("✅ 사용자 답변 저장 완료 - answerId: {}", savedAnswer.getAnswerId());

                Optional<QnaAnswerComparison> existingComparison = qnaAnswerComparisonRepository
                                .findByPracticeSessionAndQnaQuestion(session, question);

                QnaAnswerResponseDto.QnaAnswerResponseDtoBuilder respBuilder = QnaAnswerResponseDto.builder()
                                .answerId(savedAnswer.getAnswerId())
                                .questionId(questionId)
                                .sttText(savedAnswer.getBody())
                                .message("답변이 저장되었습니다");

                if (existingComparison.isPresent()) {
                        QnaAnswerComparison comp = existingComparison.get();
                        respBuilder.comparisonId(comp.getComparisonId())
                                        .comparisonAvailable(true);
                } else {
                        respBuilder.comparisonAvailable(false);
                }

                return respBuilder.build();
        }

        /**
         * 저장된 QnA 비교 결과 조회 (피드백용)
         */
        @Transactional(readOnly = true)
        public QnaComparisonDto getComparisonResult(Long sessionId) {
                log.info("📊 저장된 QnA 비교 결과 조회 - sessionId: {}", sessionId);

                PracticeSession session = practiceSessionRepository.findById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다"));

                QnaAnswerComparison comparison = qnaAnswerComparisonRepository
                                .findByPracticeSession(session)
                                .orElseThrow(() -> new IllegalStateException("QnA 비교 결과가 없습니다"));

                log.info("✅ 비교 결과 조회 완료 - comparisonId: {}", comparison.getComparisonId());

                QnaQuestion question = comparison.getQnaQuestion();
                QnaAnswer idealAnswer = comparison.getIdealAnswer();
                QnaAnswer userAnswer = comparison.getUserAnswer();

                List<Map<String, String>> feedback = loadFeedbackFromNotesOrGenerate(comparison, question, idealAnswer,
                                userAnswer);

                List<String> missingKeywords = extractMissingKeywords(
                                userAnswer.getBody(),
                                idealAnswer.getBody());

                return QnaComparisonDto.builder()
                                .comparisonId(comparison.getComparisonId())
                                .questionId(question.getQnaId())
                                .question(question.getBody())
                                .idealAnswer(idealAnswer.getBody())
                                .userAnswer(userAnswer.getBody())
                                .similarity(comparison.getSimCosine())
                                .keywordRecall(comparison.getKeywordRecall())
                                .coverage(comparison.getCoverage())
                                .feedback(feedback)
                                .missingKeywords(missingKeywords)
                                .build();
        }

        /**
         * 특정 세션에 저장된 모든 QnA 비교 결과를 DTO 리스트로 반환
         */
        @Transactional(readOnly = true)
        public List<QnaComparisonDto> getAllComparisons(Long sessionId) {
                log.info("📚 세션의 모든 QnA 비교 결과 조회 - sessionId: {}", sessionId);

                PracticeSession session = practiceSessionRepository.findById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다"));

                List<QnaAnswerComparison> comps = qnaAnswerComparisonRepository
                                .findAllByPracticeSession(session);

                List<QnaComparisonDto> out = new ArrayList<>();
                for (QnaAnswerComparison comp : comps) {
                        QnaQuestion question = comp.getQnaQuestion();
                        QnaAnswer ideal = comp.getIdealAnswer();
                        QnaAnswer user = comp.getUserAnswer();

                        List<Map<String, String>> feedback = loadFeedbackFromNotesOrGenerate(comp, question, ideal,
                                        user);
                        List<String> missingKeywords = extractMissingKeywords(user.getBody(), ideal.getBody());

                        out.add(QnaComparisonDto.builder()
                                        .comparisonId(comp.getComparisonId())
                                        .questionId(question.getQnaId())
                                        .question(question.getBody())
                                        .idealAnswer(ideal.getBody())
                                        .userAnswer(user.getBody())
                                        .similarity(comp.getSimCosine())
                                        .keywordRecall(comp.getKeywordRecall())
                                        .coverage(comp.getCoverage())
                                        .feedback(feedback)
                                        .missingKeywords(missingKeywords)
                                        .build());
                }

                log.info("✅ 세션의 QnA 비교 결과 개수: {}", out.size());
                return out;
        }

        /**
         * 사용자 답변과 모범 답변 비교 및 피드백 생성 (최초 비교 시)
         */
        @Transactional
        public QnaComparisonDto compareAnswer(Long sessionId) {
                log.info("🔍 QnA 답변 비교 시작 - sessionId: {}", sessionId);

                PracticeSession session = practiceSessionRepository.findById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다"));

                List<QnaAnswer> userAnswers = qnaAnswerRepository
                                .findByPracticeSessionAndAnswerType(session, "user");

                if (userAnswers.isEmpty()) {
                        throw new IllegalStateException("제출된 답변이 없습니다");
                }

                QnaAnswer userAnswer = userAnswers.get(0);
                QnaQuestion question = userAnswer.getQnaQuestion();

                log.info("📝 사용자 답변 조회 완료 - questionId: {}", question.getQnaId());

                QnaAnswer idealAnswer = qnaAnswerRepository
                                .findFirstByQnaQuestionAndAnswerType(question, "AI_GENERATED")
                                .orElseThrow(() -> new IllegalArgumentException("모범 답변을 찾을 수 없습니다"));

                log.info("📚 모범 답변 조회 완료 - answerId: {}", idealAnswer.getAnswerId());

                float cosineSimilarity = calculateCosineSimilarity(
                                userAnswer.getBody(),
                                idealAnswer.getBody());

                float keywordRecall = calculateKeywordRecall(
                                userAnswer.getBody(),
                                idealAnswer.getBody());

                float coverage = calculateCoverage(
                                userAnswer.getBody(),
                                idealAnswer.getBody());

                log.info("✅ 유사도 분석 완료 - cosine: {}, keywordRecall: {}, coverage: {}",
                                cosineSimilarity, keywordRecall, coverage);

                QnaAnswerComparison comparison = new QnaAnswerComparison();
                comparison.setQnaQuestion(question);
                comparison.setIdealAnswer(idealAnswer);
                comparison.setUserAnswer(userAnswer);
                comparison.setPracticeSession(session);
                comparison.setSimCosine(cosineSimilarity);
                comparison.setKeywordRecall(keywordRecall);
                comparison.setCoverage(coverage);
                comparison.setCreatedAt(LocalDateTime.now());

                qnaAnswerComparisonRepository.save(comparison);
                log.info("✅ 비교 결과 저장 완료 - comparisonId: {}", comparison.getComparisonId());

                // AI 피드백 생성 + notes(JSON) 저장
                List<Map<String, String>> feedback = generateFeedback(
                                question.getBody(),
                                idealAnswer.getBody(),
                                userAnswer.getBody(),
                                cosineSimilarity,
                                keywordRecall,
                                coverage);

                List<String> missingKeywords = extractMissingKeywords(
                                userAnswer.getBody(),
                                idealAnswer.getBody());

                if (feedback != null && !feedback.isEmpty()) {
                        try {
                                String json = objectMapper.writeValueAsString(feedback);
                                comparison.setNotes(json);
                                qnaAnswerComparisonRepository.save(comparison);
                        } catch (Exception e) {
                                log.warn("QnA 피드백 notes JSON 직렬화 실패 - comparisonId={}", comparison.getComparisonId(),
                                                e);
                        }
                }

                return QnaComparisonDto.builder()
                                .comparisonId(comparison.getComparisonId())
                                .questionId(question.getQnaId())
                                .question(question.getBody())
                                .idealAnswer(idealAnswer.getBody())
                                .userAnswer(userAnswer.getBody())
                                .similarity(cosineSimilarity)
                                .keywordRecall(keywordRecall)
                                .coverage(coverage)
                                .feedback(feedback)
                                .missingKeywords(missingKeywords)
                                .build();
        }

        /**
         * 의미론적 유사도 계산
         */
        private float calculateCosineSimilarity(String text1, String text2) {
                double similarity = TextAnalysisUtils.calculateSemanticSimilarity(text1, text2);
                return (float) similarity;
        }

        /**
         * 키워드 재현율 계산
         */
        private float calculateKeywordRecall(String userAnswer, String idealAnswer) {
                String normalized1 = TextAnalysisUtils.normalizeText(userAnswer);
                String normalized2 = TextAnalysisUtils.normalizeText(idealAnswer);

                List<String> userWords = TextAnalysisUtils.tokenizeWords(normalized1);
                List<String> idealWords = TextAnalysisUtils.tokenizeWords(normalized2);

                Set<String> userKeywords = TextAnalysisUtils.extractKeywords(userWords);
                Set<String> idealKeywords = TextAnalysisUtils.extractKeywords(idealWords);

                double matchRate = TextAnalysisUtils.calculateKeywordMatchRate(idealKeywords, userKeywords);

                return (float) matchRate;
        }

        /**
         * 커버리지 계산
         */
        private float calculateCoverage(String userAnswer, String idealAnswer) {
                List<String> userWords = TextAnalysisUtils.tokenizeWords(
                                TextAnalysisUtils.normalizeText(userAnswer));
                List<String> idealWords = TextAnalysisUtils.tokenizeWords(
                                TextAnalysisUtils.normalizeText(idealAnswer));

                if (idealWords.isEmpty())
                        return 0.0f;

                double coverage = Math.min(1.0, (double) userWords.size() / idealWords.size());
                return (float) coverage;
        }

        // 수치 기반 폴백 피드백
        private List<String> generateFallbackFeedback(float similarity, float keywordRecall, float coverage) {
                String msg;
                if (similarity >= 0.8 && keywordRecall >= 0.7) {
                        msg = "훌륭합니다! 핵심 내용을 잘 전달했습니다.";
                } else if (similarity >= 0.6) {
                        msg = "주요 내용은 언급했으나 세부 설명이 부족합니다.";
                } else {
                        msg = "핵심 내용을 더 구체적으로 설명해보세요.";
                }
                return List.of(msg);
        }

        /**
         * AI 기반 피드백 생성 시도. 실패하면 수치 기반 폴백을 사용합니다.
         * 결과는 notes 저장 전/후 모두에서 공통으로 사용.
         */
        private List<Map<String, String>> generateFeedback(String question, String idealAnswer, String userAnswer,
                        float similarity, float keywordRecall, float coverage) {
                try {
                        List<Map<String, String>> aiItems = openAIFeedbackService.generateQnaFeedback(
                                        question, idealAnswer, userAnswer);

                        if (aiItems == null || aiItems.isEmpty()) {
                                log.warn("OpenAI 피드백을 비어있는 응답으로 반환했습니다. 폴백 사용");
                                return wrapLegacyFeedback(
                                                generateFallbackFeedback(similarity, keywordRecall, coverage));
                        }

                        if (aiItems.size() == 1 && aiItems.get(0) != null && aiItems.get(0).containsKey("error")) {
                                log.warn("OpenAI 피드백 생성 실패: {}", aiItems.get(0).get("error"));
                                return wrapLegacyFeedback(
                                                generateFallbackFeedback(similarity, keywordRecall, coverage));
                        }

                        return aiItems;

                } catch (Exception e) {
                        log.error("OpenAI 피드백 호출 실패, 폴백 사용", e);
                        return wrapLegacyFeedback(generateFallbackFeedback(similarity, keywordRecall, coverage));
                }
        }

        /**
         * 기존 폴백(List<String>)을 새 구조(List<Map<String,String>>)로 감싸는 헬퍼.
         */
        private List<Map<String, String>> wrapLegacyFeedback(List<String> legacyTexts) {
                List<Map<String, String>> wrapped = new ArrayList<>();
                if (legacyTexts == null || legacyTexts.isEmpty()) {
                        return wrapped;
                }

                for (String text : legacyTexts) {
                        if (text == null || text.trim().isEmpty())
                                continue;

                        Map<String, String> item = new HashMap<>();
                        item.put("title", ""); // 폴백이라 명시적인 title 없음
                        item.put("content", text.trim());
                        item.put("improvement", ""); // 구분 불가 → 공백
                        wrapped.add(item);
                }
                return wrapped;
        }

        /**
         * 부족한 키워드 추출
         */
        private List<String> extractMissingKeywords(String userAnswer, String idealAnswer) {
                String normalized1 = TextAnalysisUtils.normalizeText(userAnswer);
                String normalized2 = TextAnalysisUtils.normalizeText(idealAnswer);

                List<String> userWords = TextAnalysisUtils.tokenizeWords(normalized1);
                List<String> idealWords = TextAnalysisUtils.tokenizeWords(normalized2);

                Set<String> userKeywords = TextAnalysisUtils.extractKeywords(userWords);
                Set<String> idealKeywords = TextAnalysisUtils.extractKeywords(idealWords);

                Set<String> missing = TextAnalysisUtils.findMissingKeywords(idealKeywords, userKeywords);

                return new ArrayList<>(missing);
        }

        /**
         * MultipartFile을 WAV로 변환 (임시 구현)
         */
        private File convertToWav(MultipartFile audioFile) throws Exception {
                File tempFile = File.createTempFile("qna_answer_", ".wav");
                audioFile.transferTo(tempFile);
                return tempFile;
        }

        /**
         * 특정 질문에 대한 비교 수행 및 피드백 생성 (질문 단위)
         */
        @Transactional
        public QnaComparisonDto compareAnswerForQuestion(Long sessionId, Long questionId) {
                log.info("🔍 QnA 답변 비교 시작 (question) - sessionId: {}, questionId: {}", sessionId, questionId);

                PracticeSession session = practiceSessionRepository.findById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다"));

                QnaQuestion question = qnaQuestionRepository.findById(questionId)
                                .orElseThrow(() -> new IllegalArgumentException("질문을 찾을 수 없습니다"));

                Optional<QnaAnswerComparison> existing = qnaAnswerComparisonRepository
                                .findByPracticeSessionAndQnaQuestion(session, question);

                if (existing.isPresent()) {
                        QnaAnswerComparison comp = existing.get();
                        QnaAnswer ideal = comp.getIdealAnswer();
                        QnaAnswer user = comp.getUserAnswer();

                        List<Map<String, String>> feedback = loadFeedbackFromNotesOrGenerate(comp, question, ideal,
                                        user);
                        List<String> missingKeywords = extractMissingKeywords(user.getBody(), ideal.getBody());

                        return QnaComparisonDto.builder()
                                        .comparisonId(comp.getComparisonId())
                                        .questionId(question.getQnaId())
                                        .question(question.getBody())
                                        .idealAnswer(ideal.getBody())
                                        .userAnswer(user.getBody())
                                        .similarity(comp.getSimCosine())
                                        .keywordRecall(comp.getKeywordRecall())
                                        .coverage(comp.getCoverage())
                                        .feedback(feedback)
                                        .missingKeywords(missingKeywords)
                                        .build();
                }

                // 사용자 답변 조회
                List<QnaAnswer> userAnswers = qnaAnswerRepository.findByQnaQuestionAndAnswerType(question, "user");
                if (userAnswers.isEmpty()) {
                        throw new IllegalStateException("해당 질문에 대한 제출된 답변이 없습니다");
                }

                QnaAnswer userAnswer = userAnswers.get(0);

                QnaAnswer idealAnswer = qnaAnswerRepository
                                .findFirstByQnaQuestionAndAnswerType(question, "AI_GENERATED")
                                .orElseThrow(() -> new IllegalArgumentException("모범 답변을 찾을 수 없습니다"));

                float cosineSimilarity = calculateCosineSimilarity(userAnswer.getBody(), idealAnswer.getBody());
                float keywordRecall = calculateKeywordRecall(userAnswer.getBody(), idealAnswer.getBody());
                float coverage = calculateCoverage(userAnswer.getBody(), idealAnswer.getBody());

                QnaAnswerComparison comparison = new QnaAnswerComparison();
                comparison.setQnaQuestion(question);
                comparison.setIdealAnswer(idealAnswer);
                comparison.setUserAnswer(userAnswer);
                comparison.setPracticeSession(session);
                comparison.setSimCosine(cosineSimilarity);
                comparison.setKeywordRecall(keywordRecall);
                comparison.setCoverage(coverage);
                comparison.setCreatedAt(LocalDateTime.now());

                qnaAnswerComparisonRepository.save(comparison);

                List<Map<String, String>> feedback = generateFeedback(
                                question.getBody(),
                                idealAnswer.getBody(),
                                userAnswer.getBody(),
                                cosineSimilarity,
                                keywordRecall,
                                coverage);

                List<String> missingKeywords = extractMissingKeywords(userAnswer.getBody(), idealAnswer.getBody());

                if (feedback != null && !feedback.isEmpty()) {
                        try {
                                String json = objectMapper.writeValueAsString(feedback);
                                comparison.setNotes(json);
                                qnaAnswerComparisonRepository.save(comparison);
                        } catch (Exception e) {
                                log.warn("QnA 피드백 notes JSON 직렬화 실패(질문 단위) - comparisonId={}",
                                                comparison.getComparisonId(), e);
                        }
                }

                return QnaComparisonDto.builder()
                                .comparisonId(comparison.getComparisonId())
                                .questionId(question.getQnaId())
                                .question(question.getBody())
                                .idealAnswer(idealAnswer.getBody())
                                .userAnswer(userAnswer.getBody())
                                .similarity(cosineSimilarity)
                                .keywordRecall(keywordRecall)
                                .coverage(coverage)
                                .feedback(feedback)
                                .missingKeywords(missingKeywords)
                                .build();
        }

        /**
         * DB에 이미 저장된 특정 질문에 대한 비교 결과만 조회 (읽기 전용)
         */
        @Transactional(readOnly = true)
        public QnaComparisonDto getSavedComparisonForQuestion(Long sessionId, Long questionId) {
                log.info("📥 DB 저장된 질문 단위 QnA 비교 조회 - sessionId: {}, questionId: {}", sessionId, questionId);

                PracticeSession session = practiceSessionRepository.findById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다"));

                QnaQuestion question = qnaQuestionRepository.findById(questionId)
                                .orElseThrow(() -> new IllegalArgumentException("질문을 찾을 수 없습니다"));

                Optional<QnaAnswerComparison> existing = qnaAnswerComparisonRepository
                                .findByPracticeSessionAndQnaQuestion(session, question);

                if (existing.isPresent()) {
                        QnaAnswerComparison comp = existing.get();
                        QnaAnswer ideal = comp.getIdealAnswer();
                        QnaAnswer user = comp.getUserAnswer();

                        List<Map<String, String>> feedback = loadFeedbackFromNotesOrGenerate(comp, question, ideal,
                                        user);
                        List<String> missingKeywords = extractMissingKeywords(user.getBody(), ideal.getBody());

                        return QnaComparisonDto.builder()
                                        .comparisonId(comp.getComparisonId())
                                        .questionId(question.getQnaId())
                                        .question(question.getBody())
                                        .idealAnswer(ideal.getBody())
                                        .userAnswer(user.getBody())
                                        .similarity(comp.getSimCosine())
                                        .keywordRecall(comp.getKeywordRecall())
                                        .coverage(comp.getCoverage())
                                        .feedback(feedback)
                                        .missingKeywords(missingKeywords)
                                        .build();
                }

                throw new IllegalStateException("QnA 비교 결과가 없습니다");
        }

        /**
         * notes 필드에서 피드백 로드 또는 새로 생성
         */
        private List<Map<String, String>> loadFeedbackFromNotesOrGenerate(
                        QnaAnswerComparison comp,
                        QnaQuestion question,
                        QnaAnswer ideal,
                        QnaAnswer user) {
                String notes = comp.getNotes();
                if (notes != null && !notes.isBlank()) {
                        try {
                                return objectMapper.readValue(
                                                notes,
                                                new TypeReference<List<Map<String, String>>>() {
                                                });
                        } catch (Exception e) {
                                log.warn("QnA notes JSON 파싱 실패, 일시적 재생성. comparisonId={}", comp.getComparisonId(), e);
                        }
                }

                float sim = comp.getSimCosine() != null ? comp.getSimCosine() : 0f;
                float recall = comp.getKeywordRecall() != null ? comp.getKeywordRecall() : 0f;
                float cov = comp.getCoverage() != null ? comp.getCoverage() : 0f;

                // Generate feedback (may call OpenAI). If notes were empty, persist generated
                // JSON
                List<Map<String, String>> feedback = generateFeedback(
                                question.getBody(),
                                ideal.getBody(),
                                user.getBody(),
                                sim,
                                recall,
                                cov);

                if (feedback != null && !feedback.isEmpty()) {
                        try {
                                String json = objectMapper.writeValueAsString(feedback);
                                // Persist in a new transaction to avoid write-in-readonly exceptions
                                TransactionTemplate tt = new TransactionTemplate(transactionManager);
                                tt.execute(status -> {
                                        try {
                                                comp.setNotes(json);
                                                qnaAnswerComparisonRepository.save(comp);
                                        } catch (Exception e) {
                                                log.warn("QnA notes 저장 실패(읽기시 저장) - comparisonId={}",
                                                                comp.getComparisonId(), e);
                                        }
                                        return null;
                                });
                        } catch (Exception e) {
                                log.warn("QnA 피드백 notes JSON 직렬화 실패(읽기시 저장) - comparisonId={}", comp.getComparisonId(),
                                                e);
                        }
                }

                return feedback;
        }
}

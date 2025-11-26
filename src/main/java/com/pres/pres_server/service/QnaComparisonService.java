package com.pres.pres_server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.pres.pres_server.domain.*;
import com.pres.pres_server.dto.qna.*;
import com.pres.pres_server.repository.*;
import com.pres.pres_server.service.analyse.TextAnalysisUtils;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import com.pres.pres_server.service.ai.OpenAIFeedbackService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

//사용자 답변과 모범 답변 비교 및 피드백 생성
@Slf4j
@Service
@RequiredArgsConstructor
public class QnaComparisonService {

        private final WhisperService whisperService;
        private final QnaAnswerRepository qnaAnswerRepository;
        private final QnaQuestionRepository qnaQuestionRepository;
        private final QnaAnswerComparisonRepository qnaAnswerComparisonRepository;
        private final PracticeSessionRepository practiceSessionRepository;

        /**
         * 사용자의 음성 답변을 텍스트로 변환하고 저장
         * 
         * @param sessionId  연습 세션 ID
         * @param questionId 질문 ID
         * @param audioFile  사용자 음성 파일
         * @return STT 결과
         */
        private final OpenAIFeedbackService openAIFeedbackService;

        @Transactional
        public QnaAnswerResponseDto submitAnswer(Long sessionId, Long questionId, MultipartFile audioFile)
                        throws Exception {
                log.info("📝 QnA 답변 제출 - sessionId: {}, questionId: {}", sessionId, questionId);

                // 1. 세션 및 질문 조회
                PracticeSession session = practiceSessionRepository.findById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다"));

                QnaQuestion question = qnaQuestionRepository.findById(questionId)
                                .orElseThrow(() -> new IllegalArgumentException("질문을 찾을 수 없습니다"));

                // 2. 음성 파일 → WAV 변환 (필요시)
                File wavFile = convertToWav(audioFile);

                // 3. STT 변환
                String sttText = whisperService.transcribe(wavFile);
                log.info("✅ STT 변환 완료 - length: {}", sttText.length());

                // 4. QnaAnswer 엔티티 저장 (사용자 답변)
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

                // 5. 비교 결과 존재 여부 확인 및 응답 DTO 생성
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
         * 
         * @param sessionId 연습 세션 ID
         * @return 저장된 비교 결과
         */
        @Transactional(readOnly = true)
        public QnaComparisonDto getComparisonResult(Long sessionId) {
                log.info("📊 저장된 QnA 비교 결과 조회 - sessionId: {}", sessionId);

                // 1. 세션 조회
                PracticeSession session = practiceSessionRepository.findById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다"));

                // 2. 저장된 비교 결과 조회
                QnaAnswerComparison comparison = qnaAnswerComparisonRepository
                                .findByPracticeSession(session)
                                .orElseThrow(() -> new IllegalStateException("QnA 비교 결과가 없습니다"));

                log.info("✅ 비교 결과 조회 완료 - comparisonId: {}", comparison.getComparisonId());

                // 3. DTO 변환
                QnaQuestion question = comparison.getQnaQuestion();
                QnaAnswer idealAnswer = comparison.getIdealAnswer();
                QnaAnswer userAnswer = comparison.getUserAnswer();

                List<String> feedback = generateFeedback(
                                question.getBody(),
                                idealAnswer.getBody(),
                                userAnswer.getBody(),
                                comparison.getSimCosine(),
                                comparison.getKeywordRecall(),
                                comparison.getCoverage());

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
        public java.util.List<QnaComparisonDto> getAllComparisons(Long sessionId) {
                log.info("📚 세션의 모든 QnA 비교 결과 조회 - sessionId: {}", sessionId);

                PracticeSession session = practiceSessionRepository.findById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다"));

                java.util.List<QnaAnswerComparison> comps = qnaAnswerComparisonRepository
                                .findAllByPracticeSession(session);

                java.util.List<QnaComparisonDto> out = new java.util.ArrayList<>();
                for (QnaAnswerComparison comp : comps) {
                        QnaQuestion question = comp.getQnaQuestion();
                        QnaAnswer ideal = comp.getIdealAnswer();
                        QnaAnswer user = comp.getUserAnswer();

                        List<String> feedback = generateFeedback(
                                        question.getBody(),
                                        ideal.getBody(),
                                        user.getBody(),
                                        comp.getSimCosine(),
                                        comp.getKeywordRecall(),
                                        comp.getCoverage());

                        java.util.List<String> missingKeywords = extractMissingKeywords(user.getBody(),
                                        ideal.getBody());

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
         * 
         * @param sessionId 연습 세션 ID
         * @return 비교 결과 및 피드백
         */
        @Transactional
        public QnaComparisonDto compareAnswer(Long sessionId) {
                log.info("🔍 QnA 답변 비교 시작 - sessionId: {}", sessionId);

                // 1. 세션 조회
                PracticeSession session = practiceSessionRepository.findById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다"));

                // 2. 사용자 답변 조회
                List<QnaAnswer> userAnswers = qnaAnswerRepository
                                .findByPracticeSessionAndAnswerType(session, "user");

                if (userAnswers.isEmpty()) {
                        throw new IllegalStateException("제출된 답변이 없습니다");
                }

                QnaAnswer userAnswer = userAnswers.get(0); // 최초 1개만 처리
                QnaQuestion question = userAnswer.getQnaQuestion();

                log.info("📝 사용자 답변 조회 완료 - questionId: {}", question.getQnaId());

                // 3. 모범 답변 조회
                QnaAnswer idealAnswer = qnaAnswerRepository
                                .findFirstByQnaQuestionAndAnswerType(question, "AI_GENERATED")
                                .orElseThrow(() -> new IllegalArgumentException("모범 답변을 찾을 수 없습니다"));

                log.info("📚 모범 답변 조회 완료 - answerId: {}", idealAnswer.getAnswerId());

                // 4. 유사도 계산 (OpenAI Embeddings API)
                float cosineSimilarity = calculateCosineSimilarity(
                                userAnswer.getBody(),
                                idealAnswer.getBody());

                // 5. 키워드 재현율 계산
                float keywordRecall = calculateKeywordRecall(
                                userAnswer.getBody(),
                                idealAnswer.getBody());

                // 6. 커버리지 계산
                float coverage = calculateCoverage(
                                userAnswer.getBody(),
                                idealAnswer.getBody());

                log.info("✅ 유사도 분석 완료 - cosine: {}, keywordRecall: {}, coverage: {}",
                                cosineSimilarity, keywordRecall, coverage);

                // 7. QnaAnswerComparison 저장
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

                // 8. AI 피드백 생성
                List<String> feedback = generateFeedback(
                                question.getBody(),
                                idealAnswer.getBody(),
                                userAnswer.getBody(),
                                cosineSimilarity,
                                keywordRecall,
                                coverage);
                List<String> missingKeywords = extractMissingKeywords(
                                userAnswer.getBody(),
                                idealAnswer.getBody());

                // 9. 응답 DTO 생성
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
         * 코사인 유사도 계산
         * 의미론적 유사도 계산 (AI 우선, 폴백: Levenshtein)
         * TextAnalysisUtils의 calculateSemanticSimilarity() 사용
         */
        private float calculateCosineSimilarity(String text1, String text2) {
                // AI 기반 의미론적 유사도 계산 (또는 Levenshtein 폴백)
                double similarity = TextAnalysisUtils.calculateSemanticSimilarity(text1, text2);

                return (float) similarity;
        }

        /**
         * 키워드 재현율 계산
         * TextAnalysisUtils의 키워드 매칭 기능 사용
         */
        private float calculateKeywordRecall(String userAnswer, String idealAnswer) {
                // 1. 텍스트 정규화
                String normalized1 = TextAnalysisUtils.normalizeText(userAnswer);
                String normalized2 = TextAnalysisUtils.normalizeText(idealAnswer);

                // 2. 단어 토큰화
                List<String> userWords = TextAnalysisUtils.tokenizeWords(normalized1);
                List<String> idealWords = TextAnalysisUtils.tokenizeWords(normalized2);

                // 3. 키워드 추출
                Set<String> userKeywords = TextAnalysisUtils.extractKeywords(userWords);
                Set<String> idealKeywords = TextAnalysisUtils.extractKeywords(idealWords);

                // 4. 키워드 매칭률 계산
                double matchRate = TextAnalysisUtils.calculateKeywordMatchRate(idealKeywords, userKeywords);

                return (float) matchRate;
        }

        /**
         * 커버리지 계산 (이상적 답변의 내용을 얼마나 커버했는지)
         * 단어 수 기반 간단한 계산
         */
        private float calculateCoverage(String userAnswer, String idealAnswer) {
                List<String> userWords = TextAnalysisUtils.tokenizeWords(
                                TextAnalysisUtils.normalizeText(userAnswer));
                List<String> idealWords = TextAnalysisUtils.tokenizeWords(
                                TextAnalysisUtils.normalizeText(idealAnswer));

                if (idealWords.isEmpty())
                        return 0.0f;

                // 사용자 답변 길이 / 이상적 답변 길이 (최대 1.0)
                double coverage = Math.min(1.0, (double) userWords.size() / idealWords.size());

                return (float) coverage;
        }

        // 수치 기반 피드백 생성, AI 폴백
        private List<String> generateFeedback(float similarity, float keywordRecall, float coverage) {
                String msg;
                if (similarity >= 0.8 && keywordRecall >= 0.7) {
                        msg = "훌륭합니다! 핵심 내용을 잘 전달했습니다.";
                } else if (similarity >= 0.6) {
                        msg = "주요 내용은 언급했으나 세부 설명이 부족합니다.";
                } else {
                        msg = "핵심 내용을 더 구체적으로 설명해보세요.";
                }
                return java.util.List.of(msg);
        }

        /**
         * AI 기반 피드백 생성 시도. 실패하면 수치 기반 폴백을 사용합니다.
         */
        private List<String> generateFeedback(String question, String idealAnswer, String userAnswer,
                        float similarity, float keywordRecall, float coverage) {
                try {
                        Map<String, String> aiResult = openAIFeedbackService.generateQnaFeedback(question, idealAnswer,
                                        userAnswer);
                        if (aiResult == null || aiResult.isEmpty() || aiResult.containsKey("error")) {
                                log.warn("OpenAI 피드백을 생성하지 못했습니다. 폴백 사용 - reason: {}",
                                                (aiResult == null ? "null" : aiResult.getOrDefault("error", "empty")));
                                return generateFeedback(similarity, keywordRecall, coverage);
                        }

                        StringBuilder sb = new StringBuilder();
                        // 흔히 'expression'과 'logic' 키를 기대
                        if (aiResult.containsKey("expression")) {
                                sb.append("[표현]").append("\n").append(aiResult.get("expression"));
                        }
                        if (aiResult.containsKey("logic")) {
                                if (sb.length() > 0)
                                        sb.append("\n\n");
                                sb.append("[논리]").append("\n").append(aiResult.get("logic"));
                        }

                        // 다른 키(예: raw 등)는 뒤에 추가
                        for (Map.Entry<String, String> e : aiResult.entrySet()) {
                                String k = e.getKey();
                                if ("expression".equals(k) || "logic".equals(k) || "error".equals(k))
                                        continue;
                                if (sb.length() > 0)
                                        sb.append("\n\n");
                                sb.append("[").append(k).append("]\n").append(e.getValue());
                        }

                        String out = sb.toString().trim();
                        if (out.isEmpty())
                                return generateFeedback(similarity, keywordRecall, coverage);

                        // Split the AI output into paragraphs (double-newline) to form a list of
                        // feedback items
                        String[] parts = out.split("\\n\\n");
                        java.util.List<String> list = new java.util.ArrayList<>();
                        for (String p : parts) {
                                String t = p == null ? null : p.trim();
                                if (t != null && !t.isEmpty())
                                        list.add(t);
                        }
                        return list.isEmpty() ? generateFeedback(similarity, keywordRecall, coverage) : list;
                } catch (Exception e) {
                        log.error("OpenAI 피드백 호출 실패, 폴백 사용", e);
                        return generateFeedback(similarity, keywordRecall, coverage);
                }
        }

        /**
         * 부족한 키워드 추출
         * TextAnalysisUtils의 누락 키워드 찾기 기능 사용
         */
        private List<String> extractMissingKeywords(String userAnswer, String idealAnswer) {
                // 1. 텍스트 정규화 및 토큰화
                String normalized1 = TextAnalysisUtils.normalizeText(userAnswer);
                String normalized2 = TextAnalysisUtils.normalizeText(idealAnswer);

                List<String> userWords = TextAnalysisUtils.tokenizeWords(normalized1);
                List<String> idealWords = TextAnalysisUtils.tokenizeWords(normalized2);

                // 2. 키워드 추출
                Set<String> userKeywords = TextAnalysisUtils.extractKeywords(userWords);
                Set<String> idealKeywords = TextAnalysisUtils.extractKeywords(idealWords);

                // 3. 누락된 키워드 찾기
                Set<String> missing = TextAnalysisUtils.findMissingKeywords(idealKeywords, userKeywords);

                return new ArrayList<>(missing);
        }

        /**
         * MultipartFile을 WAV로 변환
         */
        private File convertToWav(MultipartFile audioFile) throws Exception {
                // TODO: 오디오 파일 변환 로직
                // 임시로 그대로 반환
                File tempFile = File.createTempFile("qna_answer_", ".wav");
                audioFile.transferTo(tempFile);
                return tempFile;
        }

        /**
         * 특정 질문에 대한 비교 수행 및 피드백 생성 (독립 메서드)
         * 
         * @param sessionId  연습 세션 ID
         * @param questionId 질문 ID
         * @return 비교 결과 및 피드백
         */
        @Transactional
        public QnaComparisonDto compareAnswerForQuestion(Long sessionId, Long questionId) {
                log.info("🔍 QnA 답변 비교 시작 (question) - sessionId: {}, questionId: {}", sessionId, questionId);

                PracticeSession session = practiceSessionRepository.findById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다"));

                QnaQuestion question = qnaQuestionRepository.findById(questionId)
                                .orElseThrow(() -> new IllegalArgumentException("질문을 찾을 수 없습니다"));

                // 이미 비교 결과가 저장되어 있으면 DB 값을 반환
                Optional<QnaAnswerComparison> existing = qnaAnswerComparisonRepository
                                .findByPracticeSessionAndQnaQuestion(session, question);
                if (existing.isPresent()) {
                        QnaAnswerComparison comp = existing.get();
                        QnaAnswer ideal = comp.getIdealAnswer();
                        QnaAnswer user = comp.getUserAnswer();
                        List<String> feedback = generateFeedback(
                                        question.getBody(),
                                        ideal.getBody(),
                                        user.getBody(),
                                        comp.getSimCosine(),
                                        comp.getKeywordRecall(),
                                        comp.getCoverage());

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

                // 사용자 답변 조회 (해당 question)
                List<QnaAnswer> userAnswers = qnaAnswerRepository.findByQnaQuestionAndAnswerType(question, "user");

                if (userAnswers.isEmpty()) {
                        throw new IllegalStateException("해당 질문에 대한 제출된 답변이 없습니다");
                }

                QnaAnswer userAnswer = userAnswers.get(0);

                // 이상적 답변 조회
                QnaAnswer idealAnswer = qnaAnswerRepository
                                .findFirstByQnaQuestionAndAnswerType(question, "AI_GENERATED")
                                .orElseThrow(() -> new IllegalArgumentException("모범 답변을 찾을 수 없습니다"));

                // 유사도/키워드/커버리지 계산 (기존 로직 재사용)
                float cosineSimilarity = calculateCosineSimilarity(userAnswer.getBody(), idealAnswer.getBody());
                float keywordRecall = calculateKeywordRecall(userAnswer.getBody(), idealAnswer.getBody());
                float coverage = calculateCoverage(userAnswer.getBody(), idealAnswer.getBody());

                // 저장
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

                List<String> feedback = generateFeedback(
                                question.getBody(),
                                idealAnswer.getBody(),
                                userAnswer.getBody(),
                                cosineSimilarity,
                                keywordRecall,
                                coverage);
                List<String> missingKeywords = extractMissingKeywords(userAnswer.getBody(), idealAnswer.getBody());

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

                        List<String> feedback = generateFeedback(
                                        question.getBody(),
                                        ideal.getBody(),
                                        user.getBody(),
                                        comp.getSimCosine(),
                                        comp.getKeywordRecall(),
                                        comp.getCoverage());

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
}

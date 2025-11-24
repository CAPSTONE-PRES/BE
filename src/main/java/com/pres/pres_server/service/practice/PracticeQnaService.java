package com.pres.pres_server.service.practice;

import com.pres.pres_server.domain.PracticeSession;
import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.Project;
import com.pres.pres_server.dto.qna.QnaAnswerResponseDto;
import com.pres.pres_server.dto.qna.QnaComparisonDto;
import com.pres.pres_server.dto.qna.QnaQuestionDto;
import com.pres.pres_server.repository.PracticeSessionRepository;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.QnaAnswerRepository;
import com.pres.pres_server.repository.QnaQuestionRepository;
import com.pres.pres_server.service.QnaComparisonService;
import com.pres.pres_server.domain.QnaQuestion;
import com.pres.pres_server.domain.QnaAnswer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

// 연습 세션 내 QnA 워크플로우 관리 전담
@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeQnaService {

    private final PracticeSessionRepository practiceSessionRepository;
    private final PresentationFileRepository presentationFileRepository;
    private final QnaComparisonService qnaComparisonService;
    private final QnaAnswerRepository qnaAnswerRepository;
    private final QnaQuestionRepository qnaQuestionRepository;

    /**
     * 세션에 대해 아직 사용자 답변이 없는 질문 중 랜덤으로 하나를 반환.
     * 남은 질문이 없으면 null을 반환(컨트롤러에서 204 처리 권장).
     */
    @Transactional(readOnly = true)
    public QnaQuestionDto getRandomUnansweredQuestion(Long sessionId) {
        log.info("📝 QnA 랜덤 미응답 질문 조회 - sessionId: {}", sessionId);

        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("연습 세션을 찾을 수 없습니다. sessionId: " + sessionId));

        Project project = session.getProject();
        PresentationFile presentationFile = presentationFileRepository.findByProject(project)
                .orElseThrow(() -> new IllegalArgumentException(
                        "프로젝트에 발표 파일이 없습니다. projectId: " + project.getProjectId()));

        Long fileId = presentationFile.getFileId();
        log.info(" 발표 파일 ID: {}", fileId);

        // 전체 질문 조회
        List<QnaQuestion> questions = qnaQuestionRepository.findActiveByFileId(fileId);
        if (questions.isEmpty()) {
            log.warn("파일에 저장된 질문이 없습니다. fileId: {}", fileId);
            // 프론트엔드가 '질문이 아예 생성되지 않음'과 '모두 응답됨'을 구분할 수 있도록 404 반환
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "QnA 질문이 생성되지 않았습니다. fileId: " + fileId);
        }

        // 해당 세션에서 이미 제출된(사용자) 답변이 있는 질문 ID 집합
        List<QnaAnswer> userAnswers = qnaAnswerRepository
                .findByPracticeSessionAndAnswerType(session, "user");
        java.util.Set<Long> answeredQuestionIds = userAnswers.stream()
                .map(a -> a.getQnaQuestion().getQnaId())
                .collect(java.util.stream.Collectors.toSet());

        // 남은 후보
        List<QnaQuestion> candidates = questions.stream()
                .filter(q -> !answeredQuestionIds.contains(q.getQnaId()))
                .toList();

        if (candidates.isEmpty()) {
            log.info("남은 질문이 없습니다 - 모든 질문에 대해 답변이 존재합니다. sessionId: {}", sessionId);
            return null;
        }

        // 랜덤 선택
        java.util.Random rnd = new java.util.Random();
        QnaQuestion chosen = candidates.get(rnd.nextInt(candidates.size()));

        return QnaQuestionDto.builder()
                .questionId(chosen.getQnaId())
                .questionBody(chosen.getBody())
                .build();
    }

    /**
     * 사용자의 QnA 답변 오디오 업로드 및 STT 변환
     * 
     * @param sessionId  연습 세션 ID
     * @param questionId QnA 질문 ID
     * @param audioFile  사용자 답변 오디오 파일
     * @return STT 변환된 답변 정보
     */
    @Transactional
    public QnaAnswerResponseDto submitAnswer(Long sessionId, Long questionId, MultipartFile audioFile)
            throws Exception {
        log.info("🎤 QnA 답변 업로드 - sessionId: {}, questionId: {}", sessionId, questionId);

        // 1. QnaComparisonService에 위임하여 STT 변환 및 저장
        QnaAnswerResponseDto response = qnaComparisonService.submitAnswer(sessionId, questionId, audioFile);
        log.info("✅ QnA 답변 업로드 완료 - answerId: {}", response.getAnswerId());

        return response;
    }

    /**
     * QnA 답변 비교 결과 조회 (저장된 결과 반환)
     * 
     * @param sessionId 연습 세션 ID
     * @return 비교 결과 (유사도, 키워드 재현율, 피드백 등)
     */
    @Transactional(readOnly = true)
    public QnaComparisonDto getComparison(Long sessionId) {
        log.info("📊 저장된 QnA 비교 결과 조회 - sessionId: {}", sessionId);

        // 1. QnaComparisonService에 위임하여 저장된 비교 결과 조회
        QnaComparisonDto comparison = qnaComparisonService.getComparisonResult(sessionId);
        log.info("✅ QnA 비교 결과 조회 완료 - comparisonId: {}", comparison.getComparisonId());

        return comparison;
    }

    /**
     * 특정 질문에 대한 비교 결과를 즉시 실행하여 반환
     * 
     * @param sessionId  연습 세션 ID
     * @param questionId 질문 ID
     * @return 질문 단위 비교 결과 (쓰기 트랜잭션: 분석 및 저장 수행)
     */
    @Transactional
    public QnaComparisonDto runAndSaveQuestionComparison(Long sessionId, Long questionId) {
        log.info("▶ 질문 단위 QnA 비교 실행(쓰기) - sessionId: {}, questionId: {}", sessionId, questionId);

        QnaComparisonDto comparison = qnaComparisonService.compareAnswerForQuestion(sessionId, questionId);

        log.info("✅ 질문 단위 QnA 비교(저장) 완료 - sessionId: {}, questionId: {}, comparisonId: {}",
                sessionId, questionId, comparison.getComparisonId());

        return comparison;
    }

    /**
     * 특정 질문에 대해 DB에 저장된 비교 결과만 조회 (읽기 전용)
     * 
     * @param sessionId  연습 세션 ID
     * @param questionId 질문 ID
     * @return 저장된 질문 단위 비교 결과
     */
    @Transactional(readOnly = true)
    public QnaComparisonDto getSavedQuestionComparison(Long sessionId, Long questionId) {
        log.info("▶ 질문 단위 QnA 저장된 비교 조회(읽기) - sessionId: {}, questionId: {}", sessionId, questionId);

        QnaComparisonDto comparison = qnaComparisonService.getSavedComparisonForQuestion(sessionId, questionId);

        log.info("✅ 질문 단위 QnA 저장된 비교 반환 - sessionId: {}, questionId: {}, comparisonId: {}",
                sessionId, questionId, comparison.getComparisonId());

        return comparison;
    }
}

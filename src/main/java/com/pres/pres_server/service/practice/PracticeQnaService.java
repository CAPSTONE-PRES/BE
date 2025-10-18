package com.pres.pres_server.service.practice;

import com.pres.pres_server.domain.PracticeSession;
import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.Project;
import com.pres.pres_server.dto.qna.QnaAnswerResponseDto;
import com.pres.pres_server.dto.qna.QnaComparisonDto;
import com.pres.pres_server.dto.qna.QnaQuestionDto;
import com.pres.pres_server.repository.PracticeSessionRepository;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.service.QnaComparisonService;
import com.pres.pres_server.service.file.GenerateQnaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// 연습 세션 내 QnA 워크플로우 관리 전담
@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeQnaService {

    private final PracticeSessionRepository practiceSessionRepository;
    private final PresentationFileRepository presentationFileRepository;
    private final GenerateQnaService generateQnaService;
    private final QnaComparisonService qnaComparisonService;

    /**
     * 연습 세션에 대한 순차적 QnA 질문 조회
     * 
     * @param sessionId 연습 세션 ID
     * @param index     질문 인덱스 (0부터 시작, 0~4)
     * @return 해당 인덱스의 질문
     */
    @Transactional(readOnly = true)
    public QnaQuestionDto getQuestion(Long sessionId, int index) {
        log.info("📝 QnA 질문 조회 - sessionId: {}, index: {}", sessionId, index);

        // 1. 세션 존재 여부 확인
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("연습 세션을 찾을 수 없습니다. sessionId: " + sessionId));

        // 2. 세션의 프로젝트에서 fileId 추출
        Project project = session.getProject();
        PresentationFile presentationFile = presentationFileRepository.findByProject(project)
                .orElseThrow(() -> new IllegalArgumentException(
                        "프로젝트에 발표 파일이 없습니다. projectId: " + project.getProjectId()));

        Long fileId = presentationFile.getFileId();
        log.info(" 발표 파일 ID: {}", fileId);

        // 3. GenerateQnaService를 통해 순차적으로 질문 조회
        QnaQuestionDto question = generateQnaService.getQuestionByIndex(fileId, index);
        log.info(" 질문 조회 완료 - index: {}, questionId: {}", index, question.getQuestionId());

        return question;
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
}

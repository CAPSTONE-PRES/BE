package com.pres.pres_server.service.analyse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pres.pres_server.domain.Feedback;
import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.PracticeSession;
import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.SessionWindow;
import com.pres.pres_server.dto.analyse.WindowDto;
import com.pres.pres_server.repository.FeedbackRepository;
import com.pres.pres_server.repository.CueCardRepository;
import com.pres.pres_server.repository.PracticeSessionRepository;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.ProjectRepository;
import com.pres.pres_server.repository.SessionWindowRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 분석 결과 저장 서비스
 * AudioAnalysisService에서 모든 분석을 완료한 결과를 받아서 DB에 저장만 담당
 */
@Service
@RequiredArgsConstructor
public class AnalysisResultService {
    private static final Logger log = LoggerFactory.getLogger(AnalysisResultService.class);

    private final PracticeSessionRepository sessionRepository;
    private final SessionWindowRepository windowRepository;
    private final FeedbackRepository feedbackRepository;
    private final ProjectRepository projectRepository;
    private final CueCardRepository cueCardRepository;
    private final PresentationFileRepository presentationFileRepository;
    private final ObjectMapper objectMapper;
    private final ScriptAccuracyService scriptAccuracyService;

    /**
     * 분석 결과를 DB에 저장 (신규 세션 생성)
     * 
     * @param projectId      프로젝트 ID
     * @param analysisResult 모든 분석 결과 (AudioAnalysisService에서 생성)
     * @return 저장된 PracticeSession ID
     */
    @Transactional
    public Long saveAnalysisResult(Long projectId, AudioAnalysisService.AnalysisResult analysisResult) {
        log.info("▶ 분석 결과 저장 시작 - projectId: {}, windows: {}",
                projectId, analysisResult.getWindows().size());

        // 1. PracticeSession 생성 및 저장
        PracticeSession session = createPracticeSession(projectId, analysisResult);
        session = sessionRepository.save(session);
        log.info("  • PracticeSession 저장 완료 - sessionId: {}", session.getSessionId());

        // 2. SessionWindow 리스트 생성 및 저장
        saveSessionWindows(session, analysisResult.getWindows());
        log.info("  • SessionWindow {} 개 저장 완료", analysisResult.getWindows().size());

        // 3. Feedback 계산 및 저장
        Feedback feedback = calculateAndSaveFeedback(session, analysisResult);
        log.info("  • Feedback 저장 완료 - totalScore: {}, grade: {}",
                feedback.getTotalScore(), feedback.getGrade());

        log.info("✅ 분석 결과 저장 완료 - sessionId: {}", session.getSessionId());
        return session.getSessionId();
    }

    /**
     * 분석 결과를 DB에 저장 (기존 세션 업데이트)
     * 
     * @param session        기존 PracticeSession 엔티티
     * @param analysisResult 모든 분석 결과
     */
    @Transactional
    public void saveAnalysisResult(PracticeSession session, AudioAnalysisService.AnalysisResult analysisResult) {
        log.info("▶ 분석 결과 업데이트 시작 - sessionId: {}, windows: {}",
                session.getSessionId(), analysisResult.getWindows().size());

        // 1. STT 텍스트 및 Duration 업데이트
        session.updateSttText(analysisResult.getFullSttText());
        session.updateDuration(analysisResult.getTotalDurationSeconds());
        sessionRepository.save(session);
        log.info("  • PracticeSession STT 및 Duration 업데이트 완료 - sessionId: {}, duration: {}초",
                session.getSessionId(), String.format("%.2f", analysisResult.getTotalDurationSeconds()));

        // 2. SessionWindow 리스트 생성 및 저장
        saveSessionWindows(session, analysisResult.getWindows());
        log.info("  • SessionWindow {} 개 저장 완료", analysisResult.getWindows().size());

        // 3. Feedback 계산 및 저장
        Feedback feedback = calculateAndSaveFeedback(session, analysisResult);
        log.info("  • Feedback 저장 완료 - totalScore: {}, grade: {}",
                feedback.getTotalScore(), feedback.getGrade());

        log.info("✅ 분석 결과 업데이트 완료 - sessionId: {}", session.getSessionId());
    }

    /**
     * PracticeSession 엔티티 생성
     */
    private PracticeSession createPracticeSession(Long projectId, AudioAnalysisService.AnalysisResult analysisResult) {
        // Project 조회
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다. projectId: " + projectId));

        // Builder 패턴으로 생성
        return PracticeSession.builder()
                .project(project)
                .sttText(analysisResult.getFullSttText())
                .practicedAt(LocalDateTime.now())
                .durationSeconds(analysisResult.getTotalDurationSeconds())
                .audioUrl(null) // 오디오 파일 저장 안 함
                .build();
    }

    /**
     * SessionWindow 리스트 저장
     */
    private void saveSessionWindows(PracticeSession session, List<WindowDto> windows) {
        int index = 0;
        for (WindowDto windowDto : windows) {
            SessionWindow window = new SessionWindow();
            window.setPracticeSession(session);
            window.setWindowIndex(index);
            window.setStartTime(windowDto.getStartSec());
            window.setEndTime(windowDto.getEndSec());
            window.setText(windowDto.getTranscript());
            window.setSpm(windowDto.getSpm());
            window.setSpmScore(windowDto.getSpmScore());

            // 상태 및 에러 메시지 저장
            window.setStatus(windowDto.getStatus());
            window.setErrorMessage(windowDto.getErrorMessage());

            // 필러 카운트를 JSON으로 변환
            try {
                String fillerJson = objectMapper.writeValueAsString(windowDto.getFillers());
                window.setFillerCounts(fillerJson);
            } catch (Exception e) {
                // 샘플 정보 수집(보안상 전체는 남기지 않음)
                String transcriptSample = windowDto.getTranscript() != null
                        ? windowDto.getTranscript().substring(0, Math.min(120, windowDto.getTranscript().length()))
                        : "";
                String fillerSample = (windowDto.getFillers() != null && !windowDto.getFillers().isEmpty())
                        ? windowDto.getFillers().keySet().stream().limit(5).toList().toString()
                        : "[]";

                log.warn(
                        "필러 카운트 JSON 변환 실패 - sessionId={}, windowIndex={}, start={}, end={}, transcriptSample={}, fillerSample={}",
                        session != null ? session.getSessionId() : null,
                        index,
                        windowDto.getStartSec(),
                        windowDto.getEndSec(),
                        transcriptSample,
                        fillerSample,
                        e);

                window.setFillerCounts("{}");
            }

            try {
                windowRepository.save(window);
            } catch (Exception e) {
                // 저장 실패 시에도 루프는 계속되어야 함
                log.error(
                        "SessionWindow 저장 실패 - sessionId={}, windowIndex={}, start={}, end={}, status={}, errorMessage={}",
                        session != null ? session.getSessionId() : null,
                        index,
                        windowDto.getStartSec(),
                        windowDto.getEndSec(),
                        windowDto.getStatus(),
                        windowDto.getErrorMessage(),
                        e);
            }
            index++;
        }
    }

    /**
     * Feedback 계산 및 저장
     * AudioAnalysisService에서 이미 분석된 결과를 사용
     */
    private Feedback calculateAndSaveFeedback(PracticeSession session,
            AudioAnalysisService.AnalysisResult analysisResult) {
        Feedback feedback = new Feedback();
        feedback.setPracticeSession(session);

        List<WindowDto> windows = analysisResult.getWindows();

        // 성공한 윈도우만 필터링
        List<WindowDto> successWindows = windows.stream()
                .filter(w -> "SUCCESS".equals(w.getStatus()))
                .toList();

        if (successWindows.isEmpty()) {
            // 모든 윈도우가 실패한 경우 기본값
            log.warn("모든 윈도우 분석 실패 - 기본 피드백 저장");
            setDefaultFeedback(feedback);
            return feedbackRepository.save(feedback);
        }

        // 1. SPM 점수 평균 계산 (성공한 윈도우만)
        double avgSpmScore = successWindows.stream()
                .mapToInt(WindowDto::getSpmScore)
                .average()
                .orElse(0.0);
        feedback.setSpmScore((int) Math.round(avgSpmScore));

        // 2. 필러 점수 계산 (성공한 윈도우만)
        int totalFillers = successWindows.stream()
                .mapToInt(w -> w.getFillers().values().stream()
                        .mapToInt(Integer::intValue)
                        .sum())
                .sum();
        int fillerScore = Math.max(0, 100 - (totalFillers * 2));
        feedback.setFillerScore(fillerScore);

        // 3. 반복 점수 (AudioAnalysisService에서 이미 분석됨)
        int repeatScore = getRepeatScore(analysisResult);
        feedback.setRepeatScore(repeatScore);

        // 4. 공백 점수 (AudioAnalysisService에서 이미 분석됨)
        int silenceScore = setSilenceInfo(feedback, analysisResult);

        // 5. 정확도 점수 (대본 필요 - 여기서 분석)
        int accuracyScore = analyzeAndSetAccuracy(session, analysisResult.getFullSttText(), feedback);

        // 6. 총점 계산 (SPM 20% + Filler 15% + Repeat 15% + Silence 15% + Accuracy 35%)
        int totalScore = (int) Math.round(
                avgSpmScore * 0.2 +
                        fillerScore * 0.15 +
                        repeatScore * 0.15 +
                        silenceScore * 0.15 +
                        accuracyScore * 0.35);
        feedback.setTotalScore(totalScore);

        // 7. 등급 계산
        String grade = calculateGrade(totalScore);
        feedback.setGrade(grade);

        return feedbackRepository.save(feedback);
    }

    /**
     * 기본 피드백 값 설정 (모든 윈도우 실패 시)
     */
    private void setDefaultFeedback(Feedback feedback) {
        feedback.setSpmScore(0);
        feedback.setFillerScore(0);
        feedback.setRepeatScore(0);
        feedback.setSilenceScore(0);
        feedback.setSilenceCount(0);
        feedback.setTotalSilenceDuration(0.0);
        feedback.setSilenceAnalysisSuccess(false);
        feedback.setAccuracyScore(0);
        feedback.setTotalScore(0);
        feedback.setGrade("F");
    }

    /**
     * 반복 점수 추출
     */
    private int getRepeatScore(AudioAnalysisService.AnalysisResult analysisResult) {
        RepetitiveTextAnalysisService.RepetitionAnalysisResult repetitionResult = analysisResult.getRepetitionResult();

        if (repetitionResult != null && repetitionResult.isSuccess()) {
            int score = repetitionResult.getRepetitionScore();
            log.info("  • 반복 점수: {} (N-gram: {})",
                    score,
                    repetitionResult.getNGramPatterns().size());
            return score;
        }

        log.warn("  • 반복 분석 결과 없음 - 기본값 100점 적용");
        return 100; // 기본값: 만점 (불이익 없음)
    }

    /**
     * 공백 정보 설정 및 점수 반환
     */
    private int setSilenceInfo(Feedback feedback, AudioAnalysisService.AnalysisResult analysisResult) {
        SilenceDetectionService.SilenceStatistics silenceStats = analysisResult.getSilenceStats();

        if (silenceStats != null) {
            feedback.setSilenceCount(silenceStats.getSilenceCount());
            feedback.setTotalSilenceDuration(silenceStats.getTotalSilenceDuration());
            feedback.setSilenceAnalysisSuccess(silenceStats.isSuccess());

            if (silenceStats.isSuccess()) {
                int score = Math.max(0, 100 - (silenceStats.getSilenceCount() * 10));
                feedback.setSilenceScore(score);
                log.info("  • 공백 점수: {} (횟수: {}, 총 {}초)",
                        score, silenceStats.getSilenceCount(),
                        String.format("%.2f", silenceStats.getTotalSilenceDuration()));
                return score;
            }
        }

        // 기본값 설정
        feedback.setSilenceCount(0);
        feedback.setTotalSilenceDuration(0.0);
        feedback.setSilenceScore(100);
        feedback.setSilenceAnalysisSuccess(false);
        log.warn("  • 공백 분석 결과 없음 - 기본값 100점 적용");
        return 100;
    }

    /**
     * 정확도 분석 및 설정
     */
    private int analyzeAndSetAccuracy(PracticeSession session, String sttText, Feedback feedback) {
        try {
            // 1. 프로젝트로 발표 파일 조회
            if (session.getProject() == null) {
                log.info("  • 프로젝트 정보 없음 - 정확도 분석 생략");
                return 100;
            }

            Optional<com.pres.pres_server.domain.PresentationFile> presentationFileOpt = presentationFileRepository
                    .findByProject(session.getProject());

            if (presentationFileOpt.isEmpty()) {
                log.info("  • 발표 파일 없음 - 정확도 분석 생략");
                return 100;
            }

            Long fileId = presentationFileOpt.get().getFileId();

            // 2. 큐카드 전체 조회
            List<CueCard> cueCards = cueCardRepository.findByPresentationFile_FileIdOrderBySlideNumberAscModeAscSectionNumberAsc(fileId);

            if (cueCards.isEmpty()) {
                log.info("  • 큐카드 없음 - 정확도 분석 생략");
                return 100;
            }

            // 3. 모든 큐카드 내용을 합쳐서 전체 대본 생성
            String fullScript = cueCards.stream()
                    .map(CueCard::getContent)
                    .filter(content -> content != null && !content.trim().isEmpty())
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");

            if (fullScript.isEmpty() || sttText == null || sttText.isEmpty()) {
                log.warn("  • 대본 또는 STT 텍스트 비어있음 - 정확도 분석 생략");
                return 100;
            }

            // 4. 정확도 분석 수행
            ScriptAccuracyService.AccuracyAnalysisResult accuracyResult = scriptAccuracyService
                    .analyzeAccuracy(fullScript, sttText);

            if (accuracyResult.isSuccess()) {
                int score = accuracyResult.getAccuracyScore();
                feedback.setScriptSimilarity(accuracyResult.getScriptSimilarity());

                // 누락된 키워드를 JSON 배열 형식으로 저장
                try {
                    String missingKeywordsJson = objectMapper.writeValueAsString(
                            accuracyResult.getMissingKeywords());
                    feedback.setMissingKeywords(missingKeywordsJson);
                } catch (Exception e) {
                    log.warn("누락 키워드 JSON 변환 실패", e);
                    feedback.setMissingKeywords("[]");
                }

                log.info("  • 정확도 점수: {} (유사도: {}, 키워드 매칭: {}/{})",
                        score,
                        String.format("%.2f", accuracyResult.getScriptSimilarity()),
                        accuracyResult.getMatchedKeywordCount(),
                        accuracyResult.getTotalKeywordCount());
                return score;
            } else {
                log.warn("  • 정확도 분석 실패 - 기본값 100점 적용");
                return 100;
            }

        } catch (Exception e) {
            log.error("  • 정확도 분석 중 오류 발생 - 기본값 100점 적용", e);
            return 100;
        }
    }

    /**
     * 점수를 기반으로 등급 계산
     */
    private String calculateGrade(int totalScore) {
        if (totalScore >= 90)
            return "A+";
        if (totalScore >= 85)
            return "A";
        if (totalScore >= 80)
            return "B+";
        if (totalScore >= 75)
            return "B";
        if (totalScore >= 70)
            return "C+";
        if (totalScore >= 65)
            return "C";
        if (totalScore >= 60)
            return "D+";
        if (totalScore >= 55)
            return "D";
        return "F";
    }
}

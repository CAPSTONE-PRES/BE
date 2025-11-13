package com.pres.pres_server.service.analyse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pres.pres_server.domain.Feedback;
import com.pres.pres_server.domain.SlideFeedback;
import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.PracticeSession;
import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.SessionWindow;
import com.pres.pres_server.dto.analyse.WindowDto;
import com.pres.pres_server.repository.FeedbackRepository;
import com.pres.pres_server.repository.SlideFeedbackRepository;
import com.pres.pres_server.repository.CueCardRepository;
import com.pres.pres_server.repository.PracticeSessionRepository;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.ProjectRepository;
import com.pres.pres_server.repository.SessionWindowRepository;
import com.pres.pres_server.service.analyse.utils.SlideSegmentExtractor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final SlideFeedbackRepository slideFeedbackRepository;
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

        // 4. 정확도 점수 (대본 필요 - 여기서 분석)
        int accuracyScore = analyzeAndSetAccuracy(session, analysisResult.getFullSttText(), feedback);

        // 5. 총점 계산 (SPM 25% + Filler 25% + Repeat 25% + Accuracy 25%)
        int totalScore = (int) Math.round(
                avgSpmScore * 0.25 +
                        fillerScore * 0.25 +
                        repeatScore * 0.25 +
                        accuracyScore * 0.25);
        feedback.setTotalScore(totalScore);

        // 7. 등급 계산
        String grade = calculateGrade(totalScore);
        feedback.setGrade(grade);

        Feedback savedFeedback = feedbackRepository.save(feedback);

        // 8. 슬라이드별 피드백 저장
        saveSlideAnalysis(savedFeedback, analysisResult);

        return savedFeedback;
    }

    /**
     * 기본 피드백 값 설정 (모든 윈도우 실패 시)
     */
    private void setDefaultFeedback(Feedback feedback) {
        feedback.setSpmScore(0);
        feedback.setFillerScore(0);
        feedback.setRepeatScore(0);
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
            List<CueCard> cueCards = cueCardRepository
                    .findByPresentationFile_FileIdOrderBySlideNumberAscModeAscSectionNumberAsc(fileId);

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

    /**
     * 슬라이드별 분석 결과 저장
     */
    private void saveSlideAnalysis(Feedback feedback, AudioAnalysisService.AnalysisResult analysisResult) {
        AudioAnalysisService.SlideAnalysisResult slideAnalysis = analysisResult.getSlideAnalysis();

        if (slideAnalysis == null || slideAnalysis.isEmpty()) {
            log.info("  • 슬라이드별 분석 결과 없음 - 저장 생략");
            return;
        }

        List<FillerService.SlideFillerDto> fillerResults = slideAnalysis.getFillerResults();
        List<List<SilenceDetectionService.SilenceInterval>> silenceResults = slideAnalysis.getSilenceResults();
        List<ScriptAccuracyService.AccuracyAnalysisResult> accuracyResults = slideAnalysis.getAccuracyResults();
        List<AudioAnalysisService.SlideSpmResult> spmResults = slideAnalysis.getSpmResults();
        List<RepetitiveTextAnalysisService.SlideRepetition> repetitionResults = slideAnalysis.getRepetitionResults();
        List<String> slideSttTexts = slideAnalysis.getSlideSttTexts();
        List<SlideSegmentExtractor.SlideInterval> intervals = slideAnalysis.getIntervals();

        int slideCount = fillerResults != null ? fillerResults.size() : 0;
        log.info("• 슬라이드별 피드백 저장 시작 - {} 개", slideCount);

        // 슬라이드별 반복 결과를 Map으로 변환 (빠른 조회를 위해)
        Map<Integer, List<RepetitiveTextAnalysisService.SlideRepetition>> repetitionMap = new HashMap<>();
        if (repetitionResults != null) {
            for (RepetitiveTextAnalysisService.SlideRepetition rep : repetitionResults) {
                repetitionMap.computeIfAbsent(rep.getSlideIndex(), k -> new ArrayList<>()).add(rep);
            }
        }

        for (int i = 0; i < slideCount; i++) {
            SlideFeedback slideFeedback = new SlideFeedback();
            slideFeedback.setFeedback(feedback);
            slideFeedback.setSlideNumber(i + 1); // 슬라이드 번호는 1부터 시작

            // 타임스탬프와 STT 텍스트 설정
            if (intervals != null && i < intervals.size()) {
                slideFeedback.setTimestampSeconds(intervals.get(i).getStartTime());
            }
            if (slideSttTexts != null && i < slideSttTexts.size()) {
                slideFeedback.setSlideText(slideSttTexts.get(i));
            }

            boolean hasIssue = false;

            // 1. SPM 정보
            if (spmResults != null && i < spmResults.size()) {
                AudioAnalysisService.SlideSpmResult spmResult = spmResults.get(i);
                slideFeedback.setSpmUser(spmResult.getSpm());
                slideFeedback.setSpmAverage(290); // 평균 SPM 기준값

                // SPM이 적정 범위를 벗어난 경우 이슈로 표시 (250 미만 또는 330 초과)
                if (spmResult.getSpm() < 250 || spmResult.getSpm() > 330) {
                    slideFeedback.setIssueType("SPEED");
                    hasIssue = true;
                }
            }

            // 2. 필러 정보
            if (fillerResults != null && i < fillerResults.size()) {
                FillerService.SlideFillerDto fillerDto = fillerResults.get(i);
                int totalFillers = fillerDto.getFillerCounts().values().stream()
                        .mapToInt(Integer::intValue)
                        .sum();

                if (totalFillers > 0) {
                    if (slideFeedback.getIssueType() == null) {
                        slideFeedback.setIssueType("FILLER");
                    }
                    slideFeedback.setFillerCount(totalFillers);
                    try {
                        String fillerDetail = objectMapper.writeValueAsString(fillerDto.getFillerCounts());
                        slideFeedback.setFillerDetail(fillerDetail);
                    } catch (Exception e) {
                        log.warn("필러 상세 정보 JSON 변환 실패 - slideNumber: {}", i + 1, e);
                    }
                    hasIssue = true;
                }
            }

            // 3. 공백 정보
            if (silenceResults != null && i < silenceResults.size()) {
                List<SilenceDetectionService.SilenceInterval> silences = silenceResults.get(i);
                if (!silences.isEmpty()) {
                    int silenceCount = silences.size();
                    double totalDuration = silences.stream()
                            .mapToDouble(SilenceDetectionService.SilenceInterval::getDuration)
                            .sum();

                    slideFeedback.setSilenceCount(silenceCount);
                    slideFeedback.setTotalSilenceDuration(totalDuration);
                    int silenceScore = Math.max(0, 100 - (silenceCount * 10));
                    slideFeedback.setSilenceScore(silenceScore);
                    slideFeedback.setSilenceAnalysisSuccess(true);

                    if (slideFeedback.getIssueType() == null) {
                        slideFeedback.setIssueType("SILENCE");
                    }
                    hasIssue = true;
                }
            }

            // 4. 반복 어휘 정보
            List<RepetitiveTextAnalysisService.SlideRepetition> slideRepetitions = repetitionMap.get(i + 1); // slideIndex는
                                                                                                             // 1부터 시작
            if (slideRepetitions != null && !slideRepetitions.isEmpty()) {
                int totalRepeatCount = slideRepetitions.stream()
                        .mapToInt(RepetitiveTextAnalysisService.SlideRepetition::getCount)
                        .sum();

                if (totalRepeatCount >= 3) {
                    if (slideFeedback.getIssueType() == null) {
                        slideFeedback.setIssueType("REPETITION");
                    }
                    slideFeedback.setRepeatCount(totalRepeatCount);

                    // 상위 3개 반복 패턴만 추출
                    String repeatDetail = slideRepetitions.stream()
                            .sorted(Comparator.comparingInt(RepetitiveTextAnalysisService.SlideRepetition::getCount)
                                    .reversed())
                            .limit(3)
                            .map(RepetitiveTextAnalysisService.SlideRepetition::getPattern)
                            .collect(Collectors.joining(", "));
                    slideFeedback.setRepeatDetail(repeatDetail);
                    hasIssue = true;
                }
            }

            // 5. 정확도 정보 (대본이 있을 때만)
            if (accuracyResults != null && i < accuracyResults.size()) {
                ScriptAccuracyService.AccuracyAnalysisResult accuracyResult = accuracyResults.get(i);
                if (accuracyResult.isSuccess()) {
                    // 정확도가 낮은 경우만 이슈로 표시 (80% 미만)
                    if (accuracyResult.getAccuracyScore() < 80) {
                        if (slideFeedback.getIssueType() == null) {
                            slideFeedback.setIssueType("ACCURACY");
                        }
                        slideFeedback.setErrorCount(
                                accuracyResult.getTotalKeywordCount() - accuracyResult.getMatchedKeywordCount());
                        hasIssue = true;
                    }
                }
            }

            // 피드백이 있는 경우만 저장 (이슈가 있는 슬라이드만)
            if (hasIssue) {
                slideFeedbackRepository.save(slideFeedback);
                log.info("    • 슬라이드 {} 피드백 저장 완료 - issueType: {}",
                        i + 1, slideFeedback.getIssueType());
            }
        }

        log.info("  • 슬라이드별 피드백 저장 완료");
    }
}

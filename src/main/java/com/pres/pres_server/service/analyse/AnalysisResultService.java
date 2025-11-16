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
import com.pres.pres_server.service.ai.OpenAIFeedbackService;
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
    private final OpenAIFeedbackService openAIFeedbackService;

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

            // 필러 카운트를 JSON으로 변환 (안전한 헬퍼 사용)
            window.setFillerCounts(toJsonSafe(windowDto.getFillers()));

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

        // 저장용 정확도 필드 설정
        feedback.setAccuracyScore(accuracyScore);

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
        feedback.setSilenceScore(100);
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
            log.info("  • 반복 점수: {} (N-gram: {}, 유사문장: {})",
                    score,
                    repetitionResult.getNGramPatterns().size(),
                    repetitionResult.getSimilarSentencePairs().size());
            return score;
        }

        log.warn("  • 반복 분석 결과 없음 - 기본값 100점 적용");
        return 100; // 기본값: 만점 (불이익 없음)
    }

    /**
     * 공백 정보 설정 및 점수 반환
     */
    private int setSilenceInfo(Feedback feedback, AudioAnalysisService.AnalysisResult analysisResult) {
        SilenceDetectionService.SilenceStatistics silenceStats = analysisResult == null ? null
                : analysisResult.getSilenceStats();

        if (silenceStats != null && silenceStats.isSuccess()) {
            int score = Math.max(0, 100 - (silenceStats.getSilenceCount() * 10));
            feedback.setSilenceScore(score);
            log.info("  • 공백 점수: {} (횟수: {}, 총 {}초)", score, silenceStats.getSilenceCount(),
                    String.format("%.2f", silenceStats.getTotalSilenceDuration()));
            return score;
        }

        // 슬라이드별 결과로도 대체 계산 시도
        if (analysisResult != null && analysisResult.getSlideAnalysis() != null
                && analysisResult.getSlideAnalysis().getSilenceResults() != null) {
            List<List<SilenceDetectionService.SilenceInterval>> silenceResults = analysisResult.getSlideAnalysis()
                    .getSilenceResults();
            if (!silenceResults.isEmpty()) {
                double sumScore = 0.0;
                for (List<SilenceDetectionService.SilenceInterval> sils : silenceResults) {
                    int silenceCount = sils == null ? 0 : sils.size();
                    int s = Math.max(0, 100 - (silenceCount * 10));
                    sumScore += s;
                }
                int avg = (int) Math.round(sumScore / silenceResults.size());
                feedback.setSilenceScore(avg);
                log.info("  • 슬라이드별 공백 평균 사용 - avgSilenceScore={}", avg);
                return avg;
            }
        }

        // 정보가 없으면 만점
        feedback.setSilenceScore(100);
        log.warn("  • 공백 분석 결과 없음 - 기본값 100점 적용");
        return 100;
    }

    /**
     * 정확도 분석 및 설정
     */
    private int analyzeAndSetAccuracy(PracticeSession session, String sttText, Feedback feedback) {
        try {
            // 1. 전체 대본을 가져옴(큐카드 기반)
            if (session.getProject() == null) {
                log.info("  • 프로젝트 정보 없음 - 정확도 분석 생략");
                return 100;
            }

            String fullScript = getFullScriptForSession(session);

            if (fullScript == null || fullScript.isEmpty() || sttText == null || sttText.isEmpty()) {
                log.warn("  • 대본 또는 STT 텍스트 비어있음 - 정확도 분석 생략");
                return 100;
            }

            // 2. 계산은 ScriptAccuracyService에 위임
            ScriptAccuracyService.AccuracyAnalysisResult accuracyResult = scriptAccuracyService
                    .analyzeAccuracy(fullScript, sttText);

            if (accuracyResult.isSuccess()) {
                int score = accuracyResult.getAccuracyScore();
                feedback.setScriptSimilarity(accuracyResult.getScriptSimilarity());

                // 누락된 키워드를 JSON 배열 형식으로 저장 (공통 헬퍼 사용)
                feedback.setMissingKeywords(toJsonSafe(accuracyResult.getMissingKeywords()));

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
     * 세션에서 PresentationFile/CueCard를 조회해 전체 대본 문자열을 생성
     */
    private String getFullScriptForSession(PracticeSession session) {
        try {
            Optional<com.pres.pres_server.domain.PresentationFile> presentationFileOpt = presentationFileRepository
                    .findByProject(session.getProject());

            if (presentationFileOpt.isEmpty())
                return "";

            Long fileId = presentationFileOpt.get().getFileId();

            List<CueCard> cueCards = cueCardRepository
                    .findByPresentationFile_FileIdOrderBySlideNumberAscModeAscSectionNumberAsc(fileId);

            if (cueCards == null || cueCards.isEmpty())
                return "";

            return cueCards.stream()
                    .map(CueCard::getContent)
                    .filter(content -> content != null && !content.trim().isEmpty())
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");
        } catch (Exception e) {
            log.warn("전체 대본 생성 중 오류: {}", e.getMessage());
            return "";
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
     * 안전한 JSON 직렬화 헬퍼
     */
    private String toJsonSafe(Object obj) {
        if (obj == null)
            return "null";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 직렬화 실패: {}", e.getMessage());
            if (obj instanceof java.util.Collection)
                return "[]";
            if (obj instanceof java.util.Map)
                return "{}";
            return "null";
        }
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
            // 여러 이슈를 수집하여 JSON으로 저장
            List<Map<String, Object>> issuesList = new ArrayList<>();

            // 1. SPM 정보
            if (spmResults != null && i < spmResults.size()) {
                AudioAnalysisService.SlideSpmResult spmResult = spmResults.get(i);
                slideFeedback.setSpmUser(spmResult.getSpm());
                slideFeedback.setSpmAverage(290); // 평균 SPM 기준값

                // SPM이 적정 범위를 벗어난 경우 이슈로 추가 (250 미만 또는 330 초과)
                if (spmResult.getSpm() < 250 || spmResult.getSpm() > 330) {
                    Map<String, Object> issueMap = new HashMap<>();
                    issueMap.put("issueType", "SPEED");
                    issueMap.put("spmUser", spmResult.getSpm());
                    issueMap.put("spmAverage", 290);
                    // 즉시 OpenAI로 속도 코멘트 생성(실패하면 null 허용)
                    try {
                        Map<String, String> p = openAIFeedbackService.generatePaceFeedback(String.valueOf(i + 1),
                                (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                spmResult.getSpm(), 130.0);
                        if (p != null && p.containsKey("pace"))
                            issueMap.put("comment", p.get("pace"));
                    } catch (Exception e) {
                        log.warn("속도 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                    }
                    issuesList.add(issueMap);
                    if (slideFeedback.getIssueType() == null)
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
                    slideFeedback.setFillerCount(totalFillers);
                    // 필러 상세 정보 JSON 직렬화 (안전히 처리)
                    slideFeedback.setFillerDetail(toJsonSafe(fillerDto.getFillerCounts()));

                    Map<String, Object> issueMap = new HashMap<>();
                    issueMap.put("issueType", "FILLER");
                    issueMap.put("fillerCount", totalFillers);
                    issueMap.put("fillerDetail", fillerDto.getFillerCounts());
                    // OpenAI 호출 (필러에 대한 코멘트)
                    try {
                        List<String> fillers = fillerDto.getFillerCounts().keySet().stream().limit(10).toList();
                        Map<String, String> r = openAIFeedbackService.generateRepetitionFeedback(String.valueOf(i + 1),
                                (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                totalFillers, fillers);
                        if (r != null && r.containsKey("repetition"))
                            issueMap.put("comment", r.get("repetition"));
                    } catch (Exception e) {
                        log.warn("필러 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                    }
                    issuesList.add(issueMap);
                    if (slideFeedback.getIssueType() == null)
                        slideFeedback.setIssueType("FILLER");
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

                    Map<String, Object> issueMap = new HashMap<>();
                    issueMap.put("issueType", "SILENCE");
                    issueMap.put("silenceCount", silenceCount);
                    issueMap.put("totalSilenceDuration", totalDuration);
                    issueMap.put("silenceScore", silenceScore);
                    try {
                        Map<String, String> h = openAIFeedbackService.generateHesitationFeedback(String.valueOf(i + 1),
                                (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                silenceCount, totalDuration);
                        if (h != null && h.containsKey("hesitation"))
                            issueMap.put("comment", h.get("hesitation"));
                    } catch (Exception e) {
                        log.warn("공백 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                    }
                    issuesList.add(issueMap);
                    if (slideFeedback.getIssueType() == null)
                        slideFeedback.setIssueType("SILENCE");
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
                    slideFeedback.setRepeatCount(totalRepeatCount);

                    // 반복 패턴을 Map 형태로 저장 (상위 3개)
                    Map<String, Integer> repeatMapTop = slideRepetitions.stream()
                            .sorted(Comparator.comparingInt(RepetitiveTextAnalysisService.SlideRepetition::getCount)
                                    .reversed())
                            .limit(3)
                            .collect(Collectors.toMap(
                                    RepetitiveTextAnalysisService.SlideRepetition::getPattern,
                                    RepetitiveTextAnalysisService.SlideRepetition::getCount,
                                    (a, b) -> a, LinkedHashMap::new));
                    // 기존 DB 필드에는 문자열로도 남겨 둠
                    String repeatDetailStr = String.join(", ", repeatMapTop.keySet());
                    slideFeedback.setRepeatDetail(repeatDetailStr);

                    Map<String, Object> issueMap = new HashMap<>();
                    issueMap.put("issueType", "REPETITION");
                    issueMap.put("repeatCount", totalRepeatCount);
                    issueMap.put("repeatDetail", repeatMapTop);
                    try {
                        List<String> patterns = slideRepetitions.stream()
                                .map(RepetitiveTextAnalysisService.SlideRepetition::getPattern).distinct().toList();
                        Map<String, String> r2 = openAIFeedbackService.generateRepetitionFeedback(String.valueOf(i + 1),
                                (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                totalRepeatCount, patterns);
                        if (r2 != null && r2.containsKey("repetition"))
                            issueMap.put("comment", r2.get("repetition"));
                    } catch (Exception e) {
                        log.warn("반복 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                    }
                    issuesList.add(issueMap);
                    if (slideFeedback.getIssueType() == null)
                        slideFeedback.setIssueType("REPETITION");
                    hasIssue = true;
                }
            }

            // 5. 정확도 정보 (대본이 있을 때만)
            if (accuracyResults != null && i < accuracyResults.size()) {
                ScriptAccuracyService.AccuracyAnalysisResult accuracyResult = accuracyResults.get(i);
                if (accuracyResult.isSuccess()) {
                    // 정확도가 낮은 경우만 이슈로 표시 (80% 미만)
                    if (accuracyResult.getAccuracyScore() < 80) {
                        slideFeedback.setErrorCount(
                                accuracyResult.getTotalKeywordCount() - accuracyResult.getMatchedKeywordCount());

                        Map<String, Object> issueMap = new HashMap<>();
                        issueMap.put("issueType", "ACCURACY");
                        issueMap.put("errorCount", slideFeedback.getErrorCount());
                        try {
                            String expectedKeyPoints = "";
                            Map<String, String> a = openAIFeedbackService.generateAccuracyFeedback(
                                    String.valueOf(i + 1),
                                    (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                    expectedKeyPoints);
                            if (a != null && a.containsKey("accuracy"))
                                issueMap.put("comment", a.get("accuracy"));
                        } catch (Exception e) {
                            log.warn("정확도 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                        }
                        issuesList.add(issueMap);
                        if (slideFeedback.getIssueType() == null)
                            slideFeedback.setIssueType("ACCURACY");
                        hasIssue = true;
                    }
                }
            }

            // 피드백이 있는 경우만 저장 (이슈가 있는 슬라이드만)
            if (hasIssue) {
                // 우선 이슈별 코멘트가 수집되었는지 확인하여 전체 코멘트로 결합
                String overallComment = issuesList.stream()
                        .map(m -> m.get("comment") == null ? null : String.valueOf(m.get("comment")))
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(" "));

                // 이슈별 코멘트가 없으면 기존 통합 생성기로 폴백(재시도 포함)
                if (overallComment == null || overallComment.isBlank()) {
                    String transcript = (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "";
                    String issue = slideFeedback.getIssueType();
                    overallComment = generateSlideComment(i, transcript, issue, fillerResults, silenceResults,
                            accuracyResults, spmResults, repetitionMap);
                    if (overallComment == null) {
                        log.info("OpenAI 코멘트가 null 입니다. 슬라이드 {} 에 대해 1회 재시도합니다.", i + 1);
                        overallComment = generateSlideComment(i, transcript, issue, fillerResults, silenceResults,
                                accuracyResults, spmResults, repetitionMap);
                    }
                }
                if (overallComment == null || overallComment.isBlank()) {
                    overallComment = "AI 코멘트 생성에 실패했습니다. 수동 검토가 필요합니다.";
                }

                slideFeedback.setComment(overallComment);

                // issuesList를 JSON으로 저장 (안전한 직렬화)
                slideFeedback.setIssues(toJsonSafe(issuesList));

                slideFeedbackRepository.save(slideFeedback);
                log.info("    • 슬라이드 {} 피드백 저장 완료 - issueType: {}", i + 1, slideFeedback.getIssueType());
            }
        }

        log.info("  • 슬라이드별 피드백 저장 완료");
    }

    /**
     * 슬라이드 코멘트 생성 헬퍼 (OpenAI 호출 래퍼)
     */
    private String generateSlideComment(int i, String transcript, String issue,
            List<FillerService.SlideFillerDto> fillerResults,
            List<List<SilenceDetectionService.SilenceInterval>> silenceResults,
            List<ScriptAccuracyService.AccuracyAnalysisResult> accuracyResults,
            List<AudioAnalysisService.SlideSpmResult> spmResults,
            Map<Integer, List<RepetitiveTextAnalysisService.SlideRepetition>> repetitionMap) {
        try {
            StringBuilder commentBuilder = new StringBuilder();
            if (issue == null) {
                // 여러 항목을 시도
                // hesitation
                if (silenceResults != null && i < silenceResults.size()) {
                    List<SilenceDetectionService.SilenceInterval> sils = silenceResults.get(i);
                    if (sils != null && !sils.isEmpty()) {
                        int silenceCount = sils.size();
                        double totalSilence = sils.stream()
                                .mapToDouble(SilenceDetectionService.SilenceInterval::getDuration).sum();
                        Map<String, String> h = openAIFeedbackService.generateHesitationFeedback(String.valueOf(i + 1),
                                transcript, silenceCount, totalSilence);
                        if (h != null && h.containsKey("hesitation"))
                            commentBuilder.append(h.get("hesitation")).append(" ");
                    }
                }
                // repetition
                List<RepetitiveTextAnalysisService.SlideRepetition> reps = repetitionMap.get(i + 1);
                if (reps != null && !reps.isEmpty()) {
                    int totalRep = reps.stream().mapToInt(RepetitiveTextAnalysisService.SlideRepetition::getCount)
                            .sum();
                    List<String> patterns = reps.stream().map(RepetitiveTextAnalysisService.SlideRepetition::getPattern)
                            .distinct().toList();
                    Map<String, String> r = openAIFeedbackService.generateRepetitionFeedback(String.valueOf(i + 1),
                            transcript, totalRep, patterns);
                    if (r != null && r.containsKey("repetition"))
                        commentBuilder.append(r.get("repetition")).append(" ");
                }
                // accuracy
                if (accuracyResults != null && i < accuracyResults.size()) {
                    ScriptAccuracyService.AccuracyAnalysisResult acc = accuracyResults.get(i);
                    if (acc != null && acc.isSuccess()) {
                        String expectedKeyPoints = "";
                        Map<String, String> a = openAIFeedbackService.generateAccuracyFeedback(String.valueOf(i + 1),
                                transcript, expectedKeyPoints);
                        if (a != null && a.containsKey("accuracy"))
                            commentBuilder.append(a.get("accuracy")).append(" ");
                    }
                }
                // pace
                if (spmResults != null && i < spmResults.size()) {
                    AudioAnalysisService.SlideSpmResult spmRes = spmResults.get(i);
                    if (spmRes != null) {
                        double currentWpm = spmRes.getSpm();
                        double idealWpm = 130.0;
                        Map<String, String> p = openAIFeedbackService.generatePaceFeedback(String.valueOf(i + 1),
                                transcript, currentWpm, idealWpm);
                        if (p != null && p.containsKey("pace"))
                            commentBuilder.append(p.get("pace")).append(" ");
                    }
                }
            } else {
                // issueType 기반 단일 호출
                switch (issue) {
                    case "SPEED":
                        if (spmResults != null && i < spmResults.size()) {
                            AudioAnalysisService.SlideSpmResult spmRes = spmResults.get(i);
                            if (spmRes != null) {
                                double currentWpm = spmRes.getSpm();
                                double idealWpm = 130.0;
                                Map<String, String> p = openAIFeedbackService
                                        .generatePaceFeedback(String.valueOf(i + 1), transcript, currentWpm, idealWpm);
                                if (p != null && p.containsKey("pace"))
                                    commentBuilder.append(p.get("pace")).append(" ");
                            }
                        }
                        break;
                    case "FILLER":
                        if (fillerResults != null && i < fillerResults.size()) {
                            FillerService.SlideFillerDto fillerDto = fillerResults.get(i);
                            int totalFillers = fillerDto.getFillerCounts().values().stream().mapToInt(Integer::intValue)
                                    .sum();
                            List<String> fillers = fillerDto.getFillerCounts().keySet().stream().limit(10).toList();
                            Map<String, String> r = openAIFeedbackService.generateRepetitionFeedback(
                                    String.valueOf(i + 1), transcript, totalFillers, fillers);
                            if (r != null && r.containsKey("repetition"))
                                commentBuilder.append(r.get("repetition")).append(" ");
                        }
                        break;
                    case "SILENCE":
                        if (silenceResults != null && i < silenceResults.size()) {
                            List<SilenceDetectionService.SilenceInterval> sils = silenceResults.get(i);
                            int silenceCount = sils.size();
                            double totalSilence = sils.stream()
                                    .mapToDouble(SilenceDetectionService.SilenceInterval::getDuration).sum();
                            Map<String, String> h = openAIFeedbackService.generateHesitationFeedback(
                                    String.valueOf(i + 1), transcript, silenceCount, totalSilence);
                            if (h != null && h.containsKey("hesitation"))
                                commentBuilder.append(h.get("hesitation")).append(" ");
                        }
                        break;
                    case "REPETITION":
                        List<RepetitiveTextAnalysisService.SlideRepetition> reps2 = repetitionMap.get(i + 1);
                        if (reps2 != null && !reps2.isEmpty()) {
                            int totalRep = reps2.stream()
                                    .mapToInt(RepetitiveTextAnalysisService.SlideRepetition::getCount).sum();
                            List<String> patterns = reps2.stream()
                                    .map(RepetitiveTextAnalysisService.SlideRepetition::getPattern).distinct().toList();
                            Map<String, String> r2 = openAIFeedbackService
                                    .generateRepetitionFeedback(String.valueOf(i + 1), transcript, totalRep, patterns);
                            if (r2 != null && r2.containsKey("repetition"))
                                commentBuilder.append(r2.get("repetition")).append(" ");
                        }
                        break;
                    case "ACCURACY":
                        if (accuracyResults != null && i < accuracyResults.size()) {
                            ScriptAccuracyService.AccuracyAnalysisResult acc2 = accuracyResults.get(i);
                            if (acc2 != null && acc2.isSuccess()) {
                                String expectedKeyPoints = "";
                                Map<String, String> a2 = openAIFeedbackService
                                        .generateAccuracyFeedback(String.valueOf(i + 1), transcript, expectedKeyPoints);
                                if (a2 != null && a2.containsKey("accuracy"))
                                    commentBuilder.append(a2.get("accuracy")).append(" ");
                            }
                        }
                        break;
                    default:
                        List<RepetitiveTextAnalysisService.SlideRepetition> reps3 = repetitionMap.get(i + 1);
                        if (silenceResults != null && i < silenceResults.size()) {
                            List<SilenceDetectionService.SilenceInterval> sils = silenceResults.get(i);
                            if (sils != null && !sils.isEmpty()) {
                                int silenceCount = sils.size();
                                double totalSilence = sils.stream()
                                        .mapToDouble(SilenceDetectionService.SilenceInterval::getDuration).sum();
                                Map<String, String> h = openAIFeedbackService.generateHesitationFeedback(
                                        String.valueOf(i + 1), transcript, silenceCount, totalSilence);
                                if (h != null && h.containsKey("hesitation"))
                                    commentBuilder.append(h.get("hesitation")).append(" ");
                            }
                        }
                        if (reps3 != null && !reps3.isEmpty()) {
                            int totalRep = reps3.stream()
                                    .mapToInt(RepetitiveTextAnalysisService.SlideRepetition::getCount).sum();
                            List<String> patterns = reps3.stream()
                                    .map(RepetitiveTextAnalysisService.SlideRepetition::getPattern).distinct().toList();
                            Map<String, String> r3 = openAIFeedbackService
                                    .generateRepetitionFeedback(String.valueOf(i + 1), transcript, totalRep, patterns);
                            if (r3 != null && r3.containsKey("repetition"))
                                commentBuilder.append(r3.get("repetition")).append(" ");
                        }
                        break;
                }
            }
            return commentBuilder.length() > 0 ? commentBuilder.toString().trim() : null;
        } catch (Exception e) {
            log.warn("OpenAI 기반 슬라이드 코멘트 생성 실패: {}", e.getMessage());
            return null;
        }
    }
}

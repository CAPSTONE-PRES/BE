package com.pres.pres_server.service.analyse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pres.pres_server.domain.Feedback;
import com.pres.pres_server.domain.SlideFeedback;
import com.pres.pres_server.domain.PracticeSession;
import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.SessionWindow;
import com.pres.pres_server.dto.analyse.WindowDto;
import com.pres.pres_server.repository.FeedbackRepository;
import com.pres.pres_server.repository.SlideFeedbackRepository;
import com.pres.pres_server.repository.PracticeSessionRepository;

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

import com.pres.pres_server.dto.practice.IssueDto;
import com.pres.pres_server.domain.IssueType;
import com.pres.pres_server.dto.practice.OffsetDto;
import com.pres.pres_server.dto.practice.SlideFeedbackDto;

/**
 * 분석 결과 저장 서비스
 * AudioAnalysisService에서 모든 분석을 완료한 결과를 받아서 DB에 저장만 담당
 */
@Service
@RequiredArgsConstructor
public class AnalysisResultService {
    private static final Logger log = LoggerFactory.getLogger(AnalysisResultService.class);
    // AI 폴백 코멘트(사용자에게 빈 화면 대신 노출할 기본 메시지)
    private static final String AI_FALLBACK_COMMENT = "AI 코멘트 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";

    private final PracticeSessionRepository sessionRepository;
    private final SessionWindowRepository windowRepository;
    private final FeedbackRepository feedbackRepository;
    private final ProjectRepository projectRepository;
    private final SlideFeedbackRepository slideFeedbackRepository;
    private final ObjectMapper objectMapper;
    private final SlideSegmentExtractor slideSegmentExtractor;
    private final OpenAIFeedbackService openAIFeedbackService;
    private final SpeechSpeedService speechSpeedService;

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

        // 5. 정확도 점수 (대본 필요) - AudioAnalysisService에서 이미 계산된 결과가 있으면 재사용
        int accuracyScore;
        if (analysisResult.getAccuracyResult() != null && analysisResult.getAccuracyResult().isSuccess()) {
            ScriptAccuracyService.AccuracyAnalysisResult globalAcc = analysisResult.getAccuracyResult();
            accuracyScore = globalAcc.getAccuracyScore();
            feedback.setScriptSimilarity(globalAcc.getScriptSimilarity());
            feedback.setMissingKeywords(toJsonSafe(globalAcc.getMissingKeywords()));
            log.info("  • 정확도 점수(재사용): {} (유사도: {}, 키워드 매칭: {}/{})",
                    accuracyScore,
                    String.format("%.2f", globalAcc.getScriptSimilarity()),
                    globalAcc.getMatchedKeywordCount(),
                    globalAcc.getTotalKeywordCount());
        } else {
            log.warn("  • 정확도 결과 없음 - AudioAnalysisService에서 계산된 accuracyResult가 필요합니다. 기본값 100점 적용");
            accuracyScore = 0;
            feedback.setScriptSimilarity(0.0);
            feedback.setMissingKeywords(toJsonSafe(Collections.emptyList()));
        }

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

        // 9. 전체 요약(Overall)은 프론트에서 필요 시 AI로 생성하여 반환하도록 설계합니다.
        // 저장하지 않고, DB에는 overallComment를 남기지 않습니다.
        log.debug("  • Overall short comment generation skipped (no DB persistence)");

        return savedFeedback;
    }

    /**
     * 기본 피드백 값 설정 (모든 윈도우 실패 시)
     */
    private void setDefaultFeedback(Feedback feedback) {
        feedback.setSpmScore(0);
        feedback.setFillerScore(0);
        feedback.setRepeatScore(0);
        feedback.setSilenceScore(0);
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
            int ngramSize = repetitionResult.getNGramPatterns() == null
                    ? 0
                    : repetitionResult.getNGramPatterns().size();
            int pairSize = repetitionResult.getSimilarSentencePairs() == null
                    ? 0
                    : repetitionResult.getSimilarSentencePairs().size();
            log.info("  • 반복 점수: {} (N-gram: {}, 유사문장: {})", score, ngramSize, pairSize);

            return score;
        }
        log.warn("  • 반복 분석 결과 없음 - 기본값 0점 적용");
        return 0; // 기본값: 0점 (불이익 없음)
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
        // 정보가 없으면 0점
        feedback.setSilenceScore(0);
        log.warn("  • 공백 분석 결과 없음 - 기본값 0점 적용");
        return 0;
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
     * Public wrapper to compute grade from score so controllers can reuse
     */
    public String computeGrade(int totalScore) {
        return calculateGrade(totalScore);
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

    // Offset/slide mapping helpers moved to SlideSegmentExtractor

    /**
     * 주 책임: 분석 결과(analysisResult)를 받아 DB의 SlideFeedback 엔티티들을 생성·저장.
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
        log.info("• repetitionResults size: {}",
                repetitionResults == null ? null : repetitionResults.size());

        if (repetitionResults != null) {
            for (RepetitiveTextAnalysisService.SlideRepetition rep : repetitionResults) {
                Integer key = rep.getSlideIndex();
                // fallback to slideIndices first entry if slideIndex is null
                try {
                    if (key == null && rep.getSlideIndices() != null && !rep.getSlideIndices().isEmpty())
                        key = rep.getSlideIndices().get(0);
                } catch (Exception ignore) {
                }
                if (key == null)
                    continue; // skip entries with no slide mapping
                repetitionMap.computeIfAbsent(key, k -> new ArrayList<>()).add(rep);
                try {
                    log.info("saveSlideAnalysis 반복 매핑: slideIndex={}, pattern={}, count={}", key,
                            rep.getPattern(), rep.getCount());
                } catch (Exception ex) {
                    log.debug("반복 결과 항목 로깅 실패: {}", ex.getMessage());
                }
            }
        }
        log.info("• repetitionMap keys: {}", repetitionMap.keySet());

        // === 전역 반복 분석 결과에서 패턴별/슬라이드별 Offset 수집 ===
        RepetitiveTextAnalysisService.RepetitionAnalysisResult globalRep = analysisResult.getRepetitionResult();
        Map<String, List<OffsetDto>> patternOffsetMap = new HashMap<>();

        if (globalRep != null && globalRep.isSuccess()) {
            // 1) 단어 반복(wordRepetitions)
            if (globalRep.getWordRepetitions() != null) {
                for (RepetitiveTextAnalysisService.WordRepetition wr : globalRep.getWordRepetitions()) {
                    if (wr == null || wr.getOffsets() == null)
                        continue;
                    String word = wr.getWord();
                    for (RepetitiveTextAnalysisService.Offset o : wr.getOffsets()) {
                        if (o == null)
                            continue;
                        Integer sIdx = o.getSlideIndex();
                        if (sIdx == null)
                            continue; // 슬라이드 매핑 안 된 건 버림

                        OffsetDto dto = OffsetDto.builder()
                                .slideIndex(sIdx)
                                .begin(o.getBegin())
                                .end(o.getEnd())
                                .text(o.getText())
                                .build();
                        patternOffsetMap.computeIfAbsent(word, k -> new ArrayList<>()).add(dto);
                    }
                }
            }

            // 2) N-gram 반복(nGramPatterns)
            if (globalRep.getNGramPatterns() != null) {
                for (RepetitiveTextAnalysisService.RepetitivePattern rp : globalRep.getNGramPatterns()) {
                    if (rp == null || rp.getOffsets() == null)
                        continue;
                    String pattern = rp.getPattern();
                    for (RepetitiveTextAnalysisService.Offset o : rp.getOffsets()) {
                        if (o == null)
                            continue;
                        Integer sIdx = o.getSlideIndex();
                        if (sIdx == null)
                            continue;

                        OffsetDto dto = OffsetDto.builder()
                                .slideIndex(sIdx)
                                .begin(o.getBegin())
                                .end(o.getEnd())
                                .text(o.getText())
                                .build();
                        patternOffsetMap.computeIfAbsent(pattern, k -> new ArrayList<>()).add(dto);
                    }
                }
            }
        }
        log.info("  • repetition(word/ngram) patterns collected: {}, keys={}",
                patternOffsetMap.size(), patternOffsetMap.keySet());

        // --- Precompute slide->(pattern->offsets) and slide->(pattern->count) maps
        // from patternOffsetMap
        Map<Integer, Map<String, List<OffsetDto>>> slidePatternOffsetsByPattern = new HashMap<>();
        Map<Integer, Map<String, Integer>> slidePatternCounts = new HashMap<>();
        if (patternOffsetMap != null && !patternOffsetMap.isEmpty()) {
            for (Map.Entry<String, List<OffsetDto>> e : patternOffsetMap.entrySet()) {
                String pattern = e.getKey();
                List<OffsetDto> offs = e.getValue();
                if (offs == null)
                    continue;
                for (OffsetDto off : offs) {
                    if (off == null || off.getSlideIndex() == null)
                        continue;
                    Integer sIdx = off.getSlideIndex();
                    slidePatternOffsetsByPattern
                            .computeIfAbsent(sIdx, k -> new HashMap<>())
                            .computeIfAbsent(pattern, k -> new ArrayList<>()).add(off);
                    slidePatternCounts
                            .computeIfAbsent(sIdx, k -> new LinkedHashMap<>())
                            .merge(pattern, 1, Integer::sum);
                }
            }
        }
        log.info("  • slidePatternCounts prepared for slides: {}", slidePatternCounts.keySet());

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
            // 여러 이슈를 수집하여 JSON으로 저장 (IssueDto 사용)
            List<IssueDto> issuesList = new ArrayList<>();

            // 1. SPM 정보
            if (spmResults != null && i < spmResults.size()) {
                AudioAnalysisService.SlideSpmResult spmResult = spmResults.get(i);
                slideFeedback.setSpmUser(spmResult.getSpm());
                slideFeedback.setSpmAverage(290); // 평균 SPM 기준값

                // SPM이 최적 범위(예: SpeechSpeedService 기준)를 벗어난 경우에만 이슈로 추가
                int spmVal = spmResult.getSpm();
                if (!speechSpeedService.isOptimalSpeed(spmVal)) {
                    IssueDto.IssueDtoBuilder speedBuilder = IssueDto.builder()
                            .issueType("SPEED")
                            .spmUser(spmResult.getSpm())
                            .spmAverage(290);
                    // 즉시 OpenAI로 속도 코멘트 생성(실패하면 null 허용)
                    try {
                        Map<String, String> p = openAIFeedbackService.generatePaceFeedback(String.valueOf(i + 1),
                                (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                spmResult.getSpm(), 290.0);
                        if (p != null && p.containsKey("pace"))
                            speedBuilder.comment(p.get("pace"));
                    } catch (Exception e) {
                        log.warn("속도 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                    }
                    issuesList.add(speedBuilder.build());
                    if (slideFeedback.getIssueType() == null)
                        slideFeedback.setIssueType(IssueType.SPEED);
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

                    IssueDto.IssueDtoBuilder fillerBuilder = IssueDto.builder()
                            .issueType("FILLER")
                            .fillerCount(totalFillers)
                            .fillerDetail(fillerDto.getFillerCounts());
                    // 필러 위치(오프셋) 수집: 슬라이드 텍스트에서 각 필러 워드의 모든 위치를 찾아 OffsetDto로 추가
                    try {
                        // Prefer offsets computed by FillerService if available (domain TextOffset)
                        List<OffsetDto> fillerOffsets = new ArrayList<>();
                        try {
                            List<TextOffset> pre = fillerDto.getOffsets();
                            if (pre != null && !pre.isEmpty()) {
                                fillerOffsets = pre.stream().map(o -> OffsetDto.builder()
                                        .begin(o.getBegin())
                                        .end(o.getEnd())
                                        .slideIndex(o.getSlideIndex())
                                        .text(o.getText())
                                        .build()).collect(Collectors.toList());
                            }
                        } catch (Exception ignore) {
                        }

                        if (fillerOffsets == null || fillerOffsets.isEmpty()) {
                            String slideText = (slideSttTexts != null && i < slideSttTexts.size())
                                    ? slideSttTexts.get(i)
                                    : "";
                            List<String> fillerWords = new ArrayList<>(fillerDto.getFillerCounts().keySet());
                            fillerOffsets = slideSegmentExtractor.collectOffsetsForSlide(slideText, fillerWords, i + 1);
                        }

                        if (fillerOffsets != null && !fillerOffsets.isEmpty()) {
                            fillerBuilder.offsets(fillerOffsets);
                        }
                    } catch (Exception ex) {
                        log.warn("필러 오프셋 수집 중 오류 - slide {}: {}", i + 1, ex.getMessage());
                    }
                    // OpenAI 호출 (필러에 대한 코멘트)
                    try {
                        List<String> fillers = fillerDto.getFillerCounts().keySet().stream().limit(10).toList();
                        Map<String, String> r = openAIFeedbackService.generateFillerFeedback(String.valueOf(i + 1),
                                (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                totalFillers, fillers);
                        if (r != null && r.containsKey("filler"))
                            fillerBuilder.comment(r.get("filler"));
                    } catch (Exception e) {
                        log.warn("필러 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                    }
                    issuesList.add(fillerBuilder.build());
                    if (slideFeedback.getIssueType() == null)
                        slideFeedback.setIssueType(IssueType.FILLER);
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

                    IssueDto.IssueDtoBuilder silenceBuilder = IssueDto.builder()
                            .issueType("SILENCE")
                            .silenceCount(silenceCount);
                    try {
                        Map<String, String> h = openAIFeedbackService.generateSilenceFeedback(String.valueOf(i + 1),
                                (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                silenceCount, totalDuration);
                        if (h != null && h.containsKey("silence"))
                            silenceBuilder.comment(h.get("silence"));
                    } catch (Exception e) {
                        log.warn("공백 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                    }
                    issuesList.add(silenceBuilder.build());
                    if (slideFeedback.getIssueType() == null)
                        slideFeedback.setIssueType(IssueType.SILENCE);
                    hasIssue = true;
                }
            }

            // 4. 반복 어휘 정보 - L1(단어 반복) 결과만 사용 (슬라이드별 집계)
            int slideIndex1Based = i + 1; // i는 0부터 시작하므로 +1
            Map<String, Integer> perPatternCount = new LinkedHashMap<>();
            List<OffsetDto> repOffsets = new ArrayList<>();

            // ---------------------------
            // NOTE: 원래는 L2(SlideRepetition) 기반으로 perPatternCount를 채우도록 되어 있었습니다.
            // 요청에 따라 L2 기반 블록은 임시로 주석 처리하고, 대신 전역 패턴(map: patternOffsetMap)
            // 을 사용해 L1(전역 단어/패턴 오프셋) 기준으로 슬라이드별 perPatternCount를 채웁니다.
            // 필요 시 L2 기반 로직을 복원하면 됩니다.
            // ---------------------------
            /*
            // repetitionMap에서 이 슬라이드의 SlideRepetition 가져오기
            List<RepetitiveTextAnalysisService.SlideRepetition> slideReps = repetitionMap.get(slideIndex1Based);
            if (slideReps != null) {
                for (RepetitiveTextAnalysisService.SlideRepetition sr : slideReps) {
                    String pattern = sr.getPattern();
                    List<RepetitiveTextAnalysisService.Offset> offsets = sr.getOffsets();

                    if (offsets != null && !offsets.isEmpty()) {
                        perPatternCount.put(pattern, offsets.size());

                        // Offset → OffsetDto 변환
                        for (RepetitiveTextAnalysisService.Offset o : offsets) {
                            OffsetDto dto = OffsetDto.builder()
                                    .slideIndex(o.getSlideIndex())
                                    .begin(o.getBegin())
                                    .end(o.getEnd())
                                    .text(o.getText())
                                    .build();
                            repOffsets.add(dto);
                        }
                    } else {
                        // offset 정보가 없으면 count만 사용
                        perPatternCount.put(pattern, sr.getCount());
                    }
                }
            }
            */

            // L1 precomputed slidePatternCounts/Offsets에서 슬라이드별 패턴 카운트 및 오프셋 추가
            Map<String, List<OffsetDto>> patternsForSlide = slidePatternOffsetsByPattern.getOrDefault(slideIndex1Based,
                    Collections.emptyMap());
            if (patternsForSlide != null && !patternsForSlide.isEmpty()) {
                for (Map.Entry<String, List<OffsetDto>> e : patternsForSlide.entrySet()) {
                    String pattern = e.getKey();
                    List<OffsetDto> offs = e.getValue();
                    if (offs == null || offs.isEmpty())
                        continue;
                    perPatternCount.put(pattern, offs.size());
                    for (OffsetDto off : offs) {
                        repOffsets.add(OffsetDto.builder()
                                .slideIndex(off.getSlideIndex())
                                .begin(off.getBegin())
                                .end(off.getEnd())
                                .text(off.getText())
                                .build());
                    }
                }
                log.info("  • perPatternCount populated from precomputed L1 results for slide {}: {}", slideIndex1Based,
                        perPatternCount);
            }

            int totalRepeatCount = repOffsets.size();
            log.debug("[Slide {}] repetition offsets = {}, patterns = {}", slideIndex1Based, totalRepeatCount,
                    perPatternCount);

            if (totalRepeatCount >= 2) {
                Map<String, Integer> repeatMapTop = perPatternCount.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                        // .limit(3)
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (a, b) -> a,
                                LinkedHashMap::new));

                // db 저장용
                String repeatDetailStr = repeatMapTop.entrySet().stream()
                        .map(e -> e.getKey() + "(" + e.getValue() + "회)")
                        .collect(Collectors.joining(", "));
                slideFeedback.setRepeatDetail(repeatDetailStr);
                slideFeedback.setRepeatCount(totalRepeatCount);

                IssueDto.IssueDtoBuilder repBuilder = IssueDto.builder()
                        .issueType("REPETITION")
                        .repeatCount(totalRepeatCount)
                        .repeatDetail(repeatMapTop);

                if (!repOffsets.isEmpty()) {
                    repBuilder.offsets(repOffsets);
                }

                try {
                    Map<String, String> r2 = openAIFeedbackService.generateRepetitionFeedback(
                            String.valueOf(slideIndex1Based),
                            (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                            totalRepeatCount,
                            new ArrayList<>(repeatMapTop.keySet()));
                    if (r2 != null && r2.containsKey("repetition")) {
                        repBuilder.comment(r2.get("repetition"));
                    }
                } catch (Exception e) {
                    log.warn("반복 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", slideIndex1Based, e.getMessage());
                }

                issuesList.add(repBuilder.build());
                if (slideFeedback.getIssueType() == null) {
                    slideFeedback.setIssueType(IssueType.REPETITION);
                }
                hasIssue = true;
            }

            // 5. 정확도 정보 (대본이 있을 때만)
            if (accuracyResults != null && i < accuracyResults.size()) {
                ScriptAccuracyService.AccuracyAnalysisResult accuracyResult = accuracyResults.get(i);
                if (accuracyResult.isSuccess()) {
                    // 정확도가 낮은 경우만 이슈로 표시 (80% 미만)
                    if (accuracyResult.getAccuracyScore() < 80) {
                        // don't persist legacy error_count for ACCURACY (use IssueDto.similarity
                        // instead)
                        slideFeedback.setErrorCount(null);

                        IssueDto.IssueDtoBuilder accBuilder = IssueDto.builder()
                                .issueType("ACCURACY")
                                .similarity(accuracyResult.getScriptSimilarity());
                        try {
                            String expectedKeyPoints = "";
                            Map<String, String> a = openAIFeedbackService.generateAccuracyFeedback(
                                    String.valueOf(i + 1),
                                    (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                    expectedKeyPoints);
                            if (a != null && a.containsKey("accuracy"))
                                accBuilder.comment(a.get("accuracy"));
                        } catch (Exception e) {
                            log.warn("정확도 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                        }
                        // Offsets are intentionally omitted for ACCURACY issues.
                        // ScriptAccuracyService does not provide reliable offsets and
                        // we avoid adding slide-local offsets here to keep ACCURACY
                        // issues focused on similarity only.
                        issuesList.add(accBuilder.build());
                        if (slideFeedback.getIssueType() == null)
                            slideFeedback.setIssueType(IssueType.ACCURACY);
                        hasIssue = true;
                    }
                } else {
                    // 주의: 이전에는 전역 accuracy 결과(global accuracy)를 사용하여
                    // 슬라이드별 ACCURACY 이슈를 폴백으로 생성했음.
                    // 이 동작은 슬라이드별 정확도를 잘못 대표할 수 있으므로 제거합니다.
                    log.debug("슬라이드 {}에 대해 슬라이드별 정확도 결과 없음 - 전역 accuracy 폴백 사용하지 않음", i + 1);
                }
            }

            // 모든 슬라이드에 대해 SlideFeedback 엔티티를 저장합니다. 이슈가 없더라도
            // 프론트엔드에서 슬라이드 정보를 렌더링할 수 있도록 빈 이슈 리스트를 저장합니다.
            // 우선 이슈별 코멘트가 수집되었는지 확인하여 전체 코멘트로 결합
            String overallComment = issuesList.stream()
                    .map(IssueDto::getComment)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" "));

            // 이슈가 있고 전체 코멘트가 수집되지 않았다면 통합 생성기로 폴백(재시도 포함)
            if (hasIssue && (overallComment == null || overallComment.isBlank())) {
                String transcript = (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "";
                // 감지된 이슈 타입 집합만 전달하도록 변경: null을 넘기지 않음
                // only include issue types that do not yet have a generated comment
                java.util.Set<String> detectedTypes = issuesList.stream()
                        .filter(it -> it != null && (it.getComment() == null || it.getComment().isBlank()))
                        .map(IssueDto::getIssueType)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                // fallback: slideFeedback에 주 이슈가 설정되어 있으면 포함
                if (detectedTypes.isEmpty() && slideFeedback.getIssueType() != null)
                    detectedTypes.add(slideFeedback.getIssueType().name());

                if (!detectedTypes.isEmpty()) {
                    overallComment = generateSlideComment(i, transcript, detectedTypes, fillerResults, silenceResults,
                            accuracyResults, spmResults, repetitionMap);
                    if (overallComment == null || overallComment.isBlank()) {
                        log.warn("OpenAI 통합 코멘트가 비어있음(slide={} types={}) - 1회 재시도합니다.", i + 1,
                                detectedTypes);
                        overallComment = generateSlideComment(i, transcript, detectedTypes, fillerResults,
                                silenceResults, accuracyResults, spmResults, repetitionMap);
                        if (overallComment == null || overallComment.isBlank()) {
                            log.warn("재시도 후에도 OpenAI 코멘트가 비어있음(slide={} types={}) - 폴백 메시지를 저장합니다.",
                                    i + 1, detectedTypes);
                            overallComment = AI_FALLBACK_COMMENT;
                        }
                    }
                }
            }
            if (overallComment == null || overallComment.isBlank()) {
                // 모든 경로에서 코멘트가 없을 경우 폴백 메시지를 저장합니다.
                overallComment = AI_FALLBACK_COMMENT;
            }
            slideFeedback.setComment(overallComment);
            // issuesList를 JSON으로 저장 (빈 리스트인 경우에도 '[]'가 되도록 안전 직렬화 사용)
            slideFeedback.setIssues(toJsonSafe(issuesList));
            slideFeedbackRepository.save(slideFeedback);
            log.info("    • 슬라이드 {} 피드백 저장 완료 - issueType: {}", i + 1, slideFeedback.getIssueType());
        }
    }

    /**
     * 주 책임: 슬라이드 단위로 통합(또는 이슈 기반) 코멘트를 생성하기 위해 OpenAI를 호출하고 결과 텍스트(문장)를 반환.
     * issue == null이면 여러 이슈를 순서대로 시도하며 각각 AI 호출하여 코멘트를 누적(silence,
     * repetition,accuracy, pace, filler).
     * issue != null이면 해당 이슈 유형에 대해서만 AI 호출 수행.
     * 중복성: saveSlideAnalysis 내부에서도 각 이슈에 대해 이미 OpenAI 호출을 하고, generateSlideComment가
     * 재시도 혹은 통합용으로 동일한(또는 유사한) AI 호출을 수행할 수 있음 → 동일 슬라이드에 대해 AI 호출이 중복될 가능성(성능·비용
     * 측면).
     */
    private String generateSlideComment(int i, String transcript, java.util.Set<String> issueTypes,
            List<FillerService.SlideFillerDto> fillerResults,
            List<List<SilenceDetectionService.SilenceInterval>> silenceResults,
            List<ScriptAccuracyService.AccuracyAnalysisResult> accuracyResults,
            List<AudioAnalysisService.SlideSpmResult> spmResults,
            Map<Integer, List<RepetitiveTextAnalysisService.SlideRepetition>> repetitionMap) {
        try {
            if (issueTypes == null || issueTypes.isEmpty())
                return null;

            StringBuilder commentBuilder = new StringBuilder();

            // 우선순위 유지: SILENCE -> REPETITION -> ACCURACY -> SPEED(PACE) -> FILLER
            List<String> priority = java.util.List.of("SILENCE", "REPETITION", "ACCURACY", "SPEED", "FILLER");

            for (String t : priority) {
                if (!issueTypes.contains(t))
                    continue;

                switch (t) {
                    case "SILENCE":
                        if (silenceResults != null && i < silenceResults.size()) {
                            List<SilenceDetectionService.SilenceInterval> sils = silenceResults.get(i);
                            if (sils != null && !sils.isEmpty()) {
                                int silenceCount = sils.size();
                                double totalSilence = sils.stream()
                                        .mapToDouble(SilenceDetectionService.SilenceInterval::getDuration).sum();
                                try {
                                    Map<String, String> h = openAIFeedbackService.generateSilenceFeedback(
                                            String.valueOf(i + 1), transcript, silenceCount, totalSilence);
                                    if (h != null && h.containsKey("silence"))
                                        commentBuilder.append(h.get("silence")).append(" ");
                                } catch (Exception e) {
                                    log.warn("공백 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                                }
                            }
                        }
                        break;
                    case "REPETITION":
                        List<RepetitiveTextAnalysisService.SlideRepetition> reps = repetitionMap.get(i);
                        if (reps != null && !reps.isEmpty()) {
                            int totalRep = reps.stream()
                                    .mapToInt(RepetitiveTextAnalysisService.SlideRepetition::getCount).sum();
                            List<String> patterns = reps.stream()
                                    .map(RepetitiveTextAnalysisService.SlideRepetition::getPattern).distinct().toList();
                            try {
                                Map<String, String> r = openAIFeedbackService.generateRepetitionFeedback(
                                        String.valueOf(i + 1), transcript, totalRep, patterns);
                                if (r != null && r.containsKey("repetition"))
                                    commentBuilder.append(r.get("repetition")).append(" ");
                            } catch (Exception e) {
                                log.warn("반복 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                            }
                        }
                        break;
                    case "ACCURACY":
                        if (accuracyResults != null && i < accuracyResults.size()) {
                            ScriptAccuracyService.AccuracyAnalysisResult acc = accuracyResults.get(i);
                            if (acc != null && acc.isSuccess()) {
                                String expectedKeyPoints = "";
                                try {
                                    Map<String, String> a = openAIFeedbackService.generateAccuracyFeedback(
                                            String.valueOf(i + 1), transcript, expectedKeyPoints);
                                    if (a != null && a.containsKey("accuracy"))
                                        commentBuilder.append(a.get("accuracy")).append(" ");
                                } catch (Exception e) {
                                    log.warn("정확도 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                                }
                            }
                        }
                        break;
                    case "SPEED":
                        if (spmResults != null && i < spmResults.size()) {
                            AudioAnalysisService.SlideSpmResult spmRes = spmResults.get(i);
                            if (spmRes != null) {
                                double currentWpm = spmRes.getSpm();
                                double idealWpm = 290.0;
                                try {
                                    Map<String, String> p = openAIFeedbackService.generatePaceFeedback(
                                            String.valueOf(i + 1), transcript, currentWpm, idealWpm);
                                    if (p != null && p.containsKey("pace"))
                                        commentBuilder.append(p.get("pace")).append(" ");
                                } catch (Exception e) {
                                    log.warn("속도 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                                }
                            }
                        }
                        break;
                    case "FILLER":
                        if (fillerResults != null && i < fillerResults.size()) {
                            FillerService.SlideFillerDto fillerDto = fillerResults.get(i);
                            int totalFillers = fillerDto.getFillerCounts().values().stream().mapToInt(Integer::intValue)
                                    .sum();
                            List<String> fillers = fillerDto.getFillerCounts().keySet().stream().limit(10).toList();
                            try {
                                Map<String, String> r = openAIFeedbackService.generateFillerFeedback(
                                        String.valueOf(i + 1), transcript, totalFillers, fillers);
                                if (r != null && r.containsKey("filler"))
                                    commentBuilder.append(r.get("filler")).append(" ");
                            } catch (Exception e) {
                                log.warn("필러 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                            }
                        }
                        break;
                    default:
                        // 알 수 없는 타입은 무시
                        break;
                }
            }

            return commentBuilder.length() > 0 ? commentBuilder.toString().trim() : null;
        } catch (Exception e) {
            log.warn("OpenAI 기반 슬라이드 코멘트 생성 실패: {}", e.getMessage());
            log.error("OpenAI 기반 슬라이드 코멘트 생성 중 예외 발생 (issueTypes={}, slideIndex={}, transcriptLen={}): {}",
                    issueTypes, i + 1, transcript == null ? 0 : Math.min(transcript.length(), 200), e.getMessage(), e);
            // 폴백 메시지를 반환하여 프론트엔드에 빈 문자열이 노출되는 상황을 방지합니다.
            return AI_FALLBACK_COMMENT;
        }
    }

    @Deprecated
    public List<List<IssueDto>> loadSlideIssuesForSession(Long sessionId) {
        if (sessionId == null)
            return Collections.emptyList();

        try {
            Optional<Feedback> fbOpt = feedbackRepository.findByPracticeSessionSessionId(sessionId);
            if (fbOpt.isEmpty())
                return Collections.emptyList();
            Feedback fb = fbOpt.get();

            List<SlideFeedback> sfs = slideFeedbackRepository.findByFeedbackIdOrderBySlideNumber(fb.getFeedbackId());
            List<List<IssueDto>> out = new ArrayList<>();
            if (sfs == null || sfs.isEmpty())
                return out;

            for (SlideFeedback sf : sfs) {
                String issuesJson = sf.getIssues();
                if (issuesJson == null || issuesJson.isBlank()) {
                    out.add(Collections.emptyList());
                    continue;
                }
                try {
                    IssueDto[] arr = objectMapper.readValue(issuesJson, IssueDto[].class);
                    out.add(Arrays.asList(arr));
                } catch (Exception e) {
                    log.warn("슬라이드 이슈 JSON 파싱 실패: slide={} err={}", sf.getSlideNumber(), e.getMessage());
                    out.add(Collections.emptyList());
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("세션의 슬라이드 이슈 불러오기 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

}

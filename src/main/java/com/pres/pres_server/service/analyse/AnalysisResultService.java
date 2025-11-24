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
import com.pres.pres_server.dto.practice.IssueDto;
import com.pres.pres_server.dto.practice.OffsetDto;

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
    private final SlideSegmentExtractor slideSegmentExtractor;
    private final ScriptAccuracyService scriptAccuracyService;
    private final OpenAIFeedbackService openAIFeedbackService;
    private final SpeechSpeedService speechSpeedService;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

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

        // 9. 전체 요약(Overall) 생성: 슬라이드 피드백의 코멘트들을 모아 커밋 이후에 요약 요청 및 저장
        try {
            List<com.pres.pres_server.domain.SlideFeedback> savedSlides = slideFeedbackRepository
                    .findByFeedbackIdOrderBySlideNumber(savedFeedback.getFeedbackId());
            StringBuilder allComments = new StringBuilder();
            for (com.pres.pres_server.domain.SlideFeedback sf : savedSlides) {
                String issuesJson = sf.getIssues();
                if (issuesJson == null || issuesJson.isBlank())
                    continue;
                try {
                    java.util.List<IssueDto> issues = objectMapper.readValue(issuesJson, objectMapper.getTypeFactory()
                            .constructCollectionType(java.util.List.class, IssueDto.class));
                    for (IssueDto it : issues) {
                        if (it != null && it.getComment() != null && !it.getComment().isBlank()) {
                            if (allComments.length() > 0)
                                allComments.append(" ");
                            allComments.append(it.getComment().trim());
                        }
                    }
                } catch (Exception ex) {
                    log.warn("슬라이드 이슈 JSON 파싱 실패(전체요약용) - slideFeedbackId={}: {}", sf.getId(), ex.getMessage());
                }
            }

            final String aggregated = allComments.toString();
            if (!aggregated.isBlank()) {
                // 트랜잭션이 커밋된 이후에 OpenAI 호출 및 Feedback 업데이트를 수행하도록 등록합니다.
                final Long fbId = savedFeedback.getFeedbackId();
                final Long sessionIdVal = session.getSessionId();
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    String overall = openAIFeedbackService
                                            .generateOverallFeedback(String.valueOf(sessionIdVal), aggregated);
                                    if (overall != null && !overall.isBlank()) {
                                        // 커밋 이후 별도 트랜잭션으로 Feedback 업데이트
                                        org.springframework.transaction.support.TransactionTemplate tt = new org.springframework.transaction.support.TransactionTemplate(
                                                transactionManager);
                                        tt.executeWithoutResult(status -> {
                                            try {
                                                java.util.Optional<Feedback> fopt = feedbackRepository.findById(fbId);
                                                if (fopt.isPresent()) {
                                                    Feedback f = fopt.get();
                                                    f.setOverallComment(overall);
                                                    feedbackRepository.save(f);
                                                    log.info("  • Overall AI 요약 저장 완료 (afterCommit)");
                                                } else {
                                                    log.warn("Feedback not found for overall save: {}", fbId);
                                                }
                                            } catch (Exception e) {
                                                log.warn("Overall 저장 중 오류(트랜잭션) : {}", e.getMessage());
                                            }
                                        });
                                    } else {
                                        log.warn("  • Overall AI 요약 생성 실패 또는 빈값 (afterCommit)");
                                    }
                                } catch (Exception e) {
                                    log.warn("Overall 요약 생성 중 오류(후처리): {}", e.getMessage());
                                }
                            }
                        });
            }
        } catch (Exception e) {
            log.warn("Overall 요약 생성 등록 중 오류: {}", e.getMessage());
        }

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

        // 전체 STT 기준에서 각 슬라이드 텍스트의 시작 인덱스를 계산 (전역->슬라이드 오프셋 매핑용)
        String fullStt = analysisResult.getFullSttText();
        List<Integer> slideStartIndices = slideSegmentExtractor.computeSlideStartOffsets(fullStt, slideSttTexts);

        // 반복 분석 결과에서 제공되는 글로벌 Offsets를 패턴별로 수집하여 슬라이드 로컬 Offsets로 변환
        Map<String, List<OffsetDto>> patternOffsetMap = new HashMap<>();
        RepetitiveTextAnalysisService.RepetitionAnalysisResult globalRep = analysisResult.getRepetitionResult();
        if (globalRep != null && globalRep.isSuccess()) {
            // n-gram patterns
            if (globalRep.getNGramPatterns() != null) {
                for (RepetitiveTextAnalysisService.RepetitivePattern rp : globalRep.getNGramPatterns()) {
                    List<RepetitiveTextAnalysisService.Offset> offs = rp.getOffsets();
                    if (offs == null)
                        continue;
                    for (RepetitiveTextAnalysisService.Offset o : offs) {
                        java.util.Optional<OffsetDto> dto = slideSegmentExtractor.convertGlobalOffsetToSlideOffset(o,
                                slideStartIndices,
                                slideSttTexts);
                        dto.ifPresent(
                                d -> patternOffsetMap.computeIfAbsent(rp.getPattern(), k -> new ArrayList<>()).add(d));
                    }
                }
            }
            // word repetitions (single-word patterns)
            if (globalRep.getWordRepetitions() != null) {
                for (RepetitiveTextAnalysisService.WordRepetition wr : globalRep.getWordRepetitions()) {
                    List<RepetitiveTextAnalysisService.Offset> offs = wr.getOffsets();
                    if (offs == null)
                        continue;
                    for (RepetitiveTextAnalysisService.Offset o : offs) {
                        java.util.Optional<OffsetDto> dto = slideSegmentExtractor.convertGlobalOffsetToSlideOffset(o,
                                slideStartIndices,
                                slideSttTexts);
                        dto.ifPresent(
                                d -> patternOffsetMap.computeIfAbsent(wr.getWord(), k -> new ArrayList<>()).add(d));
                    }
                }
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
                                spmResult.getSpm(), 130.0);
                        if (p != null && p.containsKey("pace"))
                            speedBuilder.comment(p.get("pace"));
                    } catch (Exception e) {
                        log.warn("속도 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                    }
                    issuesList.add(speedBuilder.build());
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
                        Map<String, String> r = openAIFeedbackService.generateRepetitionFeedback(String.valueOf(i + 1),
                                (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                totalFillers, fillers);
                        if (r != null && r.containsKey("repetition"))
                            fillerBuilder.comment(r.get("repetition"));
                    } catch (Exception e) {
                        log.warn("필러 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                    }
                    issuesList.add(fillerBuilder.build());
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

                    IssueDto.IssueDtoBuilder silenceBuilder = IssueDto.builder()
                            .issueType("SILENCE")
                            .silenceCount(silenceCount);
                    try {
                        Map<String, String> h = openAIFeedbackService.generateHesitationFeedback(String.valueOf(i + 1),
                                (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                silenceCount, totalDuration);
                        if (h != null && h.containsKey("hesitation"))
                            silenceBuilder.comment(h.get("hesitation"));
                    } catch (Exception e) {
                        log.warn("공백 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                    }
                    issuesList.add(silenceBuilder.build());
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

                    IssueDto.IssueDtoBuilder repBuilder = IssueDto.builder()
                            .issueType("REPETITION")
                            .repeatCount(totalRepeatCount)
                            .repeatDetail(repeatMapTop);
                    // 반복 패턴에 대한 오프셋 수집
                    try {
                        List<String> patterns = slideRepetitions.stream()
                                .map(RepetitiveTextAnalysisService.SlideRepetition::getPattern)
                                .distinct().toList();
                        List<OffsetDto> repOffsets = new ArrayList<>();
                        for (String p : patterns) {
                            List<OffsetDto> mapped = patternOffsetMap.get(p);
                            if (mapped != null && !mapped.isEmpty())
                                repOffsets.addAll(mapped);
                        }
                        // 폴백: 글로벌 매핑이 없으면 슬라이드 텍스트에서 substring 기준으로 찾음
                        if (repOffsets.isEmpty()) {
                            String slideText = (slideSttTexts != null && i < slideSttTexts.size())
                                    ? slideSttTexts.get(i)
                                    : "";
                            repOffsets = slideSegmentExtractor.collectOffsetsForSlide(slideText, patterns, i + 1);
                        }
                        if (!repOffsets.isEmpty()) {
                            repBuilder.offsets(repOffsets);
                        }
                    } catch (Exception ex) {
                        log.warn("반복 오프셋 수집 중 오류 - slide {}: {}", i + 1, ex.getMessage());
                    }
                    try {
                        List<String> patterns = slideRepetitions.stream()
                                .map(RepetitiveTextAnalysisService.SlideRepetition::getPattern).distinct().toList();
                        Map<String, String> r2 = openAIFeedbackService.generateRepetitionFeedback(String.valueOf(i + 1),
                                (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                totalRepeatCount, patterns);
                        if (r2 != null && r2.containsKey("repetition"))
                            repBuilder.comment(r2.get("repetition"));
                    } catch (Exception e) {
                        log.warn("반복 관련 OpenAI 코멘트 생성 실패 - slide {}: {}", i + 1, e.getMessage());
                    }
                    issuesList.add(repBuilder.build());
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

                        IssueDto.IssueDtoBuilder accBuilder = IssueDto.builder()
                                .issueType("ACCURACY")
                                .errorCount(slideFeedback.getErrorCount());
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
                        try {
                            List<OffsetDto> accOffsets = new ArrayList<>();
                            // Prefer offsets computed by ScriptAccuracyService if present
                            try {
                                List<TextOffset> provided = accuracyResult.getOffsets();
                                if (provided != null && !provided.isEmpty()) {
                                    for (TextOffset o : provided) {
                                        int slideIdx = o.getSlideIndex() > 0 ? o.getSlideIndex() : (i + 1);
                                        accOffsets.add(OffsetDto.builder()
                                                .begin(o.getBegin())
                                                .end(o.getEnd())
                                                .slideIndex(slideIdx)
                                                .text(o.getText())
                                                .build());
                                    }
                                }
                            } catch (Exception ignore) {
                            }

                            // Fallback: search slide STT text for missing keywords
                            if (accOffsets.isEmpty()) {
                                String slideText = (slideSttTexts != null && i < slideSttTexts.size())
                                        ? slideSttTexts.get(i)
                                        : "";
                                List<String> missing = accuracyResult.getMissingKeywords();
                                accOffsets = slideSegmentExtractor.collectOffsetsForSlide(slideText, missing, i + 1);
                            }

                            if (!accOffsets.isEmpty()) {
                                accBuilder.offsets(accOffsets);
                            }
                        } catch (Exception ex) {
                            log.warn("정확도 오프셋 수집 중 오류 - slide {}: {}", i + 1, ex.getMessage());
                        }
                        issuesList.add(accBuilder.build());
                        if (slideFeedback.getIssueType() == null)
                            slideFeedback.setIssueType("ACCURACY");
                        hasIssue = true;
                    }
                } else {
                    // 슬라이드별 정확도 결과가 없는 경우, 전체 정확도 결과에서 누락 키워드를
                    // 슬라이드 텍스트별로 찾아 폴백으로 ACCURACY 이슈를 생성합니다.
                    try {
                        ScriptAccuracyService.AccuracyAnalysisResult globalAcc = analysisResult.getAccuracyResult();
                        if (globalAcc != null && globalAcc.isSuccess()) {
                            List<String> missingGlobal = globalAcc.getMissingKeywords();
                            if (missingGlobal != null && !missingGlobal.isEmpty()) {
                                String slideText = (slideSttTexts != null && i < slideSttTexts.size())
                                        ? slideSttTexts.get(i)
                                        : "";
                                List<OffsetDto> accOffsets = slideSegmentExtractor.collectOffsetsForSlide(slideText,
                                        missingGlobal, i + 1);
                                if (accOffsets != null && !accOffsets.isEmpty()) {
                                    slideFeedback.setErrorCount(accOffsets.size());
                                    IssueDto accIssue = IssueDto.builder()
                                            .issueType("ACCURACY")
                                            .errorCount(slideFeedback.getErrorCount())
                                            .offsets(accOffsets)
                                            .build();
                                    issuesList.add(accIssue);
                                    if (slideFeedback.getIssueType() == null)
                                        slideFeedback.setIssueType("ACCURACY");
                                    hasIssue = true;
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("정확도 폴백 오프셋 수집 중 오류 - slide {}: {}", i + 1, ex.getMessage());
                    }
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
                // 코멘트가 전혀 없다면 null 로 남기지 말고 빈 문자열로 둡니다.
                overallComment = "";
            }

            slideFeedback.setComment(overallComment);

            // issuesList를 JSON으로 저장 (빈 리스트인 경우에도 '[]'가 되도록 안전 직렬화 사용)
            slideFeedback.setIssues(toJsonSafe(issuesList));

            slideFeedbackRepository.save(slideFeedback);
            log.info("    • 슬라이드 {} 피드백 저장 완료 - issueType: {}", i + 1, slideFeedback.getIssueType());
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

    /**
     * 저장된 세션 ID로부터 슬라이드별 IssueDto 리스트를 복원하여 반환
     * 각 슬라이드마다 List<IssueDto>를 요소로 가지는 리스트를 반환한다.
     */
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

    /**
     * AnalysisResult로부터 DB에 저장하지 않고 슬라이드별 IssueDto 리스트를 생성하여 반환합니다.
     * 테스트용 혹은 임시 미저장 피드백을 구성할 때 사용합니다.
     */
    public List<List<IssueDto>> buildSlideIssuesFromAnalysis(AudioAnalysisService.AnalysisResult analysisResult) {
        if (analysisResult == null)
            return Collections.emptyList();

        AudioAnalysisService.SlideAnalysisResult slideAnalysis = analysisResult.getSlideAnalysis();
        if (slideAnalysis == null || slideAnalysis.isEmpty())
            return Collections.emptyList();

        List<FillerService.SlideFillerDto> fillerResults = slideAnalysis.getFillerResults();
        List<List<SilenceDetectionService.SilenceInterval>> silenceResults = slideAnalysis.getSilenceResults();
        List<ScriptAccuracyService.AccuracyAnalysisResult> accuracyResults = slideAnalysis.getAccuracyResults();
        List<AudioAnalysisService.SlideSpmResult> spmResults = slideAnalysis.getSpmResults();
        List<RepetitiveTextAnalysisService.SlideRepetition> repetitionResults = slideAnalysis.getRepetitionResults();
        List<String> slideSttTexts = slideAnalysis.getSlideSttTexts();

        int slideCount = fillerResults != null ? fillerResults.size() : 0;
        List<List<IssueDto>> out = new ArrayList<>();
        if (slideCount == 0)
            return out;

        // repetition map
        Map<Integer, List<RepetitiveTextAnalysisService.SlideRepetition>> repetitionMap = new HashMap<>();
        if (repetitionResults != null) {
            for (RepetitiveTextAnalysisService.SlideRepetition rep : repetitionResults) {
                repetitionMap.computeIfAbsent(rep.getSlideIndex(), k -> new ArrayList<>()).add(rep);
            }
        }

        String fullStt = analysisResult.getFullSttText();
        List<Integer> slideStartIndices = slideSegmentExtractor.computeSlideStartOffsets(fullStt, slideSttTexts);

        // global repetition -> patternOffsetMap
        Map<String, List<OffsetDto>> patternOffsetMap = new HashMap<>();
        RepetitiveTextAnalysisService.RepetitionAnalysisResult globalRep = analysisResult.getRepetitionResult();
        if (globalRep != null && globalRep.isSuccess()) {
            if (globalRep.getNGramPatterns() != null) {
                for (RepetitiveTextAnalysisService.RepetitivePattern rp : globalRep.getNGramPatterns()) {
                    List<RepetitiveTextAnalysisService.Offset> offs = rp.getOffsets();
                    if (offs == null)
                        continue;
                    for (RepetitiveTextAnalysisService.Offset o : offs) {
                        java.util.Optional<OffsetDto> dto = slideSegmentExtractor.convertGlobalOffsetToSlideOffset(o,
                                slideStartIndices, slideSttTexts);
                        dto.ifPresent(
                                d -> patternOffsetMap.computeIfAbsent(rp.getPattern(), k -> new ArrayList<>()).add(d));
                    }
                }
            }
            if (globalRep.getWordRepetitions() != null) {
                for (RepetitiveTextAnalysisService.WordRepetition wr : globalRep.getWordRepetitions()) {
                    List<RepetitiveTextAnalysisService.Offset> offs = wr.getOffsets();
                    if (offs == null)
                        continue;
                    for (RepetitiveTextAnalysisService.Offset o : offs) {
                        java.util.Optional<OffsetDto> dto = slideSegmentExtractor.convertGlobalOffsetToSlideOffset(o,
                                slideStartIndices, slideSttTexts);
                        dto.ifPresent(
                                d -> patternOffsetMap.computeIfAbsent(wr.getWord(), k -> new ArrayList<>()).add(d));
                    }
                }
            }
        }

        for (int i = 0; i < slideCount; i++) {
            List<IssueDto> issuesList = new ArrayList<>();

            // 1. SPM
            if (spmResults != null && i < spmResults.size()) {
                AudioAnalysisService.SlideSpmResult spmResult = spmResults.get(i);
                int spmVal = spmResult.getSpm();
                if (!speechSpeedService.isOptimalSpeed(spmVal)) {
                    IssueDto.IssueDtoBuilder speedBuilder = IssueDto.builder()
                            .issueType("SPEED")
                            .spmUser(spmResult.getSpm())
                            .spmAverage(290);
                    // try to get pace comment via OpenAI (best-effort)
                    try {
                        Map<String, String> p = openAIFeedbackService.generatePaceFeedback(String.valueOf(i + 1),
                                (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                spmResult.getSpm(), 130.0);
                        if (p != null && p.containsKey("pace"))
                            speedBuilder.comment(p.get("pace"));
                    } catch (Exception e) {
                        log.warn("속도 관련 OpenAI 코멘트 생성 실패 (in-memory) - slide {}: {}", i + 1, e.getMessage());
                    }
                    issuesList.add(speedBuilder.build());
                }
            }

            // 2. filler
            if (fillerResults != null && i < fillerResults.size()) {
                FillerService.SlideFillerDto fillerDto = fillerResults.get(i);
                int totalFillers = fillerDto.getFillerCounts().values().stream().mapToInt(Integer::intValue).sum();
                if (totalFillers > 0) {
                    IssueDto.IssueDtoBuilder fillerBuilder = IssueDto.builder()
                            .issueType("FILLER")
                            .fillerCount(totalFillers)
                            .fillerDetail(fillerDto.getFillerCounts());
                    try {
                        List<OffsetDto> fillerOffsets = new ArrayList<>();
                        try {
                            List<TextOffset> pre = fillerDto.getOffsets();
                            if (pre != null && !pre.isEmpty()) {
                                fillerOffsets = pre.stream()
                                        .map(o -> OffsetDto.builder().begin(o.getBegin()).end(o.getEnd())
                                                .slideIndex(o.getSlideIndex()).text(o.getText()).build())
                                        .collect(Collectors.toList());
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
                        if (fillerOffsets != null && !fillerOffsets.isEmpty())
                            fillerBuilder.offsets(fillerOffsets);
                    } catch (Exception ex) {
                        log.warn("필러 오프셋 수집 중 오류 (in-memory) - slide {}: {}", i + 1, ex.getMessage());
                    }
                    try {
                        List<String> fillers = fillerDto.getFillerCounts().keySet().stream().limit(10).toList();
                        Map<String, String> r = openAIFeedbackService.generateRepetitionFeedback(String.valueOf(i + 1),
                                (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                totalFillers, fillers);
                        if (r != null && r.containsKey("repetition"))
                            fillerBuilder.comment(r.get("repetition"));
                    } catch (Exception e) {
                        log.warn("필러 관련 OpenAI 코멘트 생성 실패 (in-memory) - slide {}: {}", i + 1, e.getMessage());
                    }
                    issuesList.add(fillerBuilder.build());
                }
            }

            // 3. silence
            if (silenceResults != null && i < silenceResults.size()) {
                List<SilenceDetectionService.SilenceInterval> silences = silenceResults.get(i);
                if (silsNotEmpty(silences)) {
                    int silenceCount = silences.size();
                    IssueDto.IssueDtoBuilder silenceBuilder = IssueDto.builder()
                            .issueType("SILENCE")
                            .silenceCount(silenceCount);
                    try {
                        Map<String, String> h = openAIFeedbackService.generateHesitationFeedback(String.valueOf(i + 1),
                                (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                silenceCount, silences.stream()
                                        .mapToDouble(SilenceDetectionService.SilenceInterval::getDuration).sum());
                        if (h != null && h.containsKey("hesitation"))
                            silenceBuilder.comment(h.get("hesitation"));
                    } catch (Exception e) {
                        log.warn("공백 관련 OpenAI 코멘트 생성 실패 (in-memory) - slide {}: {}", i + 1, e.getMessage());
                    }
                    issuesList.add(silenceBuilder.build());
                }
            }

            // 4. repetition
            List<RepetitiveTextAnalysisService.SlideRepetition> slideRepetitions = repetitionMap.get(i + 1);
            if (slideRepetitions != null && !slideRepetitions.isEmpty()) {
                int totalRepeatCount = slideRepetitions.stream()
                        .mapToInt(RepetitiveTextAnalysisService.SlideRepetition::getCount).sum();
                if (totalRepeatCount >= 1) {
                    IssueDto.IssueDtoBuilder repBuilder = IssueDto.builder()
                            .issueType("REPETITION")
                            .repeatCount(totalRepeatCount);
                    try {
                        List<String> patterns = slideRepetitions.stream()
                                .map(RepetitiveTextAnalysisService.SlideRepetition::getPattern).distinct().toList();
                        List<OffsetDto> repOffsets = new ArrayList<>();
                        for (String p : patterns) {
                            List<OffsetDto> mapped = patternOffsetMap.get(p);
                            if (mapped != null && !mapped.isEmpty())
                                repOffsets.addAll(mapped);
                        }
                        if (repOffsets.isEmpty()) {
                            String slideText = (slideSttTexts != null && i < slideSttTexts.size())
                                    ? slideSttTexts.get(i)
                                    : "";
                            repOffsets = slideSegmentExtractor.collectOffsetsForSlide(slideText, patterns, i + 1);
                        }
                        if (!repOffsets.isEmpty())
                            repBuilder.offsets(repOffsets);
                    } catch (Exception ex) {
                        log.warn("반복 오프셋 수집 중 오류 (in-memory) - slide {}: {}", i + 1, ex.getMessage());
                    }
                    try {
                        List<String> patterns = slideRepetitions.stream()
                                .map(RepetitiveTextAnalysisService.SlideRepetition::getPattern).distinct().toList();
                        Map<String, String> r2 = openAIFeedbackService.generateRepetitionFeedback(String.valueOf(i + 1),
                                (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "",
                                totalRepeatCount, patterns);
                        if (r2 != null && r2.containsKey("repetition"))
                            repBuilder.comment(r2.get("repetition"));
                    } catch (Exception e) {
                        log.warn("반복 관련 OpenAI 코멘트 생성 실패 (in-memory) - slide {}: {}", i + 1, e.getMessage());
                    }
                    issuesList.add(repBuilder.build());
                }
            }

            // 5. accuracy: use slide-level if present, otherwise fallback to global
            // missingKeywords
            if (accuracyResults != null && i < accuracyResults.size()) {
                ScriptAccuracyService.AccuracyAnalysisResult accuracyResult = accuracyResults.get(i);
                if (accuracyResult.isSuccess() && accuracyResult.getAccuracyScore() < 80) {
                    try {
                        List<OffsetDto> accOffsets = new ArrayList<>();
                        try {
                            List<TextOffset> provided = accuracyResult.getOffsets();
                            if (provided != null && !provided.isEmpty()) {
                                for (TextOffset o : provided)
                                    accOffsets.add(OffsetDto.builder().begin(o.getBegin()).end(o.getEnd())
                                            .slideIndex(o.getSlideIndex() > 0 ? o.getSlideIndex() : (i + 1))
                                            .text(o.getText()).build());
                            }
                        } catch (Exception ignore) {
                        }
                        if (accOffsets.isEmpty()) {
                            String slideText = (slideSttTexts != null && i < slideSttTexts.size())
                                    ? slideSttTexts.get(i)
                                    : "";
                            List<String> missing = accuracyResult.getMissingKeywords();
                            accOffsets = slideSegmentExtractor.collectOffsetsForSlide(slideText, missing, i + 1);
                        }
                        if (!accOffsets.isEmpty()) {
                            IssueDto acc = IssueDto.builder().issueType("ACCURACY").errorCount(accOffsets.size())
                                    .offsets(accOffsets).build();
                            issuesList.add(acc);
                        }
                    } catch (Exception ex) {
                        log.warn("정확도 오프셋 수집 중 오류 (in-memory) - slide {}: {}", i + 1, ex.getMessage());
                    }
                }
            } else {
                try {
                    ScriptAccuracyService.AccuracyAnalysisResult globalAcc = analysisResult.getAccuracyResult();
                    if (globalAcc != null && globalAcc.isSuccess()) {
                        List<String> missingGlobal = globalAcc.getMissingKeywords();
                        if (missingGlobal != null && !missingGlobal.isEmpty()) {
                            String slideText = (slideSttTexts != null && i < slideSttTexts.size())
                                    ? slideSttTexts.get(i)
                                    : "";
                            List<OffsetDto> accOffsets = slideSegmentExtractor.collectOffsetsForSlide(slideText,
                                    missingGlobal, i + 1);
                            if (accOffsets != null && !accOffsets.isEmpty()) {
                                issuesList.add(IssueDto.builder().issueType("ACCURACY").errorCount(accOffsets.size())
                                        .offsets(accOffsets).build());
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("정확도 폴백 오프셋 수집 중 오류 (in-memory) - slide {}: {}", i + 1, ex.getMessage());
                }
            }

            out.add(issuesList);
        }

        return out;
    }

    private boolean silsNotEmpty(List<SilenceDetectionService.SilenceInterval> sils) {
        return sils != null && !sils.isEmpty();
    }

    /**
     * 분석 결과(메모리)를 기반으로 프론트용 SlideFeedbackDto 리스트를 생성합니다.
     * DB에 저장하지 않고 테스트 응답에서 사용하기 위한 헬퍼입니다.
     */
    public List<com.pres.pres_server.dto.practice.SlideFeedbackDto> buildSlideFeedbackDtosFromAnalysis(
            AudioAnalysisService.AnalysisResult analysisResult) {
        List<com.pres.pres_server.dto.practice.SlideFeedbackDto> out = new ArrayList<>();
        if (analysisResult == null)
            return out;

        AudioAnalysisService.SlideAnalysisResult slideAnalysis = analysisResult.getSlideAnalysis();
        if (slideAnalysis == null || slideAnalysis.isEmpty())
            return out;

        List<FillerService.SlideFillerDto> fillerResults = slideAnalysis.getFillerResults();
        List<List<SilenceDetectionService.SilenceInterval>> silenceResults = slideAnalysis.getSilenceResults();
        List<ScriptAccuracyService.AccuracyAnalysisResult> accuracyResults = slideAnalysis.getAccuracyResults();
        List<AudioAnalysisService.SlideSpmResult> spmResults = slideAnalysis.getSpmResults();
        List<RepetitiveTextAnalysisService.SlideRepetition> repetitionResults = slideAnalysis.getRepetitionResults();
        List<String> slideSttTexts = slideAnalysis.getSlideSttTexts();
        List<SlideSegmentExtractor.SlideInterval> intervals = slideAnalysis.getIntervals();

        int slideCount = fillerResults != null ? fillerResults.size() : 0;

        // 전체 STT 기반에서 각 슬라이드 시작 인덱스 계산 (for repetition global offsets mapping)
        String fullStt = analysisResult.getFullSttText();
        List<Integer> slideStartIndices = slideSegmentExtractor.computeSlideStartOffsets(fullStt, slideSttTexts);

        // repetition global -> slide-local offset map
        Map<String, List<OffsetDto>> patternOffsetMap = new HashMap<>();
        RepetitiveTextAnalysisService.RepetitionAnalysisResult globalRep = analysisResult.getRepetitionResult();
        if (globalRep != null && globalRep.isSuccess()) {
            if (globalRep.getNGramPatterns() != null) {
                for (RepetitiveTextAnalysisService.RepetitivePattern rp : globalRep.getNGramPatterns()) {
                    List<RepetitiveTextAnalysisService.Offset> offs = rp.getOffsets();
                    if (offs == null)
                        continue;
                    for (RepetitiveTextAnalysisService.Offset o : offs) {
                        java.util.Optional<OffsetDto> dto = slideSegmentExtractor.convertGlobalOffsetToSlideOffset(o,
                                slideStartIndices,
                                slideSttTexts);
                        dto.ifPresent(
                                d -> patternOffsetMap.computeIfAbsent(rp.getPattern(), k -> new ArrayList<>()).add(d));
                    }
                }
            }
            if (globalRep.getWordRepetitions() != null) {
                for (RepetitiveTextAnalysisService.WordRepetition wr : globalRep.getWordRepetitions()) {
                    List<RepetitiveTextAnalysisService.Offset> offs = wr.getOffsets();
                    if (offs == null)
                        continue;
                    for (RepetitiveTextAnalysisService.Offset o : offs) {
                        java.util.Optional<OffsetDto> dto = slideSegmentExtractor.convertGlobalOffsetToSlideOffset(o,
                                slideStartIndices,
                                slideSttTexts);
                        dto.ifPresent(
                                d -> patternOffsetMap.computeIfAbsent(wr.getWord(), k -> new ArrayList<>()).add(d));
                    }
                }
            }
        }

        Map<Integer, List<RepetitiveTextAnalysisService.SlideRepetition>> repetitionMap = new HashMap<>();
        if (repetitionResults != null) {
            for (RepetitiveTextAnalysisService.SlideRepetition rep : repetitionResults) {
                repetitionMap.computeIfAbsent(rep.getSlideIndex(), k -> new ArrayList<>()).add(rep);
            }
        }

        for (int i = 0; i < slideCount; i++) {
            Integer slideNumber = i + 1;
            String slideText = (slideSttTexts != null && i < slideSttTexts.size()) ? slideSttTexts.get(i) : "";
            Double ts = (intervals != null && i < intervals.size()) ? intervals.get(i).getStartTime() : null;

            List<IssueDto> issuesList = new ArrayList<>();

            // SPEED
            if (spmResults != null && i < spmResults.size()) {
                AudioAnalysisService.SlideSpmResult spmResult = spmResults.get(i);
                if (spmResult != null && !speechSpeedService.isOptimalSpeed(spmResult.getSpm())) {
                    IssueDto speed = IssueDto.builder()
                            .issueType("SPEED")
                            .spmUser(spmResult.getSpm())
                            .spmAverage(290)
                            .build();
                    issuesList.add(speed);
                }
            }

            // FILLER
            if (fillerResults != null && i < fillerResults.size()) {
                FillerService.SlideFillerDto fillerDto = fillerResults.get(i);
                int totalFillers = fillerDto.getFillerCounts().values().stream().mapToInt(Integer::intValue).sum();
                if (totalFillers > 0) {
                    IssueDto.IssueDtoBuilder fb = IssueDto.builder()
                            .issueType("FILLER")
                            .fillerCount(totalFillers)
                            .fillerDetail(fillerDto.getFillerCounts());

                    // offsets
                    List<OffsetDto> fillerOffsets = new ArrayList<>();
                    try {
                        List<TextOffset> provided = fillerDto.getOffsets();
                        if (provided != null && !provided.isEmpty()) {
                            fillerOffsets = provided.stream().map(o -> OffsetDto.builder()
                                    .begin(o.getBegin()).end(o.getEnd()).slideIndex(o.getSlideIndex()).text(o.getText())
                                    .build())
                                    .collect(Collectors.toList());
                        }
                    } catch (Exception ignore) {
                    }
                    if (fillerOffsets.isEmpty()) {
                        fillerOffsets = slideSegmentExtractor.collectOffsetsForSlide(slideText,
                                new ArrayList<>(fillerDto.getFillerCounts().keySet()), slideNumber);
                    }
                    if (!fillerOffsets.isEmpty())
                        fb.offsets(fillerOffsets);

                    issuesList.add(fb.build());
                }
            }

            // SILENCE
            if (silenceResults != null && i < silenceResults.size()) {
                List<SilenceDetectionService.SilenceInterval> sils = silenceResults.get(i);
                if (sils != null && !sils.isEmpty()) {
                    int silenceCount = sils.size();
                    IssueDto sil = IssueDto.builder().issueType("SILENCE").silenceCount(silenceCount).build();
                    issuesList.add(sil);
                }
            }

            // REPETITION
            List<RepetitiveTextAnalysisService.SlideRepetition> slideReps = repetitionMap.get(slideNumber);
            if (slideReps != null && !slideReps.isEmpty()) {
                int totalRepeatCount = slideReps.stream()
                        .mapToInt(RepetitiveTextAnalysisService.SlideRepetition::getCount).sum();
                if (totalRepeatCount >= 1) {
                    Map<String, Integer> repeatMapTop = slideReps.stream()
                            .sorted(Comparator.comparingInt(RepetitiveTextAnalysisService.SlideRepetition::getCount)
                                    .reversed())
                            .limit(3)
                            .collect(Collectors.toMap(RepetitiveTextAnalysisService.SlideRepetition::getPattern,
                                    RepetitiveTextAnalysisService.SlideRepetition::getCount, (a, b) -> a,
                                    LinkedHashMap::new));

                    IssueDto.IssueDtoBuilder rb = IssueDto.builder().issueType("REPETITION")
                            .repeatCount(totalRepeatCount).repeatDetail(repeatMapTop);
                    List<String> patterns = slideReps.stream()
                            .map(RepetitiveTextAnalysisService.SlideRepetition::getPattern).distinct().toList();
                    List<OffsetDto> repOffsets = new ArrayList<>();
                    for (String p : patterns) {
                        List<OffsetDto> mapped = patternOffsetMap.get(p);
                        if (mapped != null)
                            repOffsets.addAll(mapped);
                    }
                    if (repOffsets.isEmpty()) {
                        repOffsets = slideSegmentExtractor.collectOffsetsForSlide(slideText, patterns, slideNumber);
                    }
                    if (!repOffsets.isEmpty())
                        rb.offsets(repOffsets);
                    issuesList.add(rb.build());
                }
            }

            // ACCURACY
            if (accuracyResults != null && i < accuracyResults.size()) {
                ScriptAccuracyService.AccuracyAnalysisResult acc = accuracyResults.get(i);
                if (acc != null && acc.isSuccess() && acc.getAccuracyScore() < 80) {
                    IssueDto.IssueDtoBuilder ab = IssueDto.builder().issueType("ACCURACY")
                            .errorCount(acc.getTotalKeywordCount() - acc.getMatchedKeywordCount());
                    List<OffsetDto> accOffsets = new ArrayList<>();
                    try {
                        List<TextOffset> provided = acc.getOffsets();
                        if (provided != null && !provided.isEmpty()) {
                            accOffsets = provided.stream()
                                    .map(o -> OffsetDto.builder().begin(o.getBegin()).end(o.getEnd())
                                            .slideIndex(o.getSlideIndex()).text(o.getText()).build())
                                    .collect(Collectors.toList());
                        }
                    } catch (Exception ignore) {
                    }
                    if (accOffsets.isEmpty()) {
                        List<String> missing = acc.getMissingKeywords();
                        accOffsets = slideSegmentExtractor.collectOffsetsForSlide(slideText, missing, slideNumber);
                    }
                    if (!accOffsets.isEmpty())
                        ab.offsets(accOffsets);
                    issuesList.add(ab.build());
                }
            }

            com.pres.pres_server.dto.practice.SlideFeedbackDto dto = com.pres.pres_server.dto.practice.SlideFeedbackDto
                    .builder()
                    .slideNumber(slideNumber)
                    .timestampSeconds(ts)
                    .slideText(slideText)
                    .issues(issuesList)
                    .build();
            out.add(dto);
        }

        return out;
    }
}

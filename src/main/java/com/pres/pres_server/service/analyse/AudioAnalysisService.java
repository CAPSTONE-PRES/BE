package com.pres.pres_server.service.analyse;

import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.dto.analyse.WindowDto;
import com.pres.pres_server.repository.CueCardRepository;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.service.WhisperService;
import com.pres.pres_server.service.analyse.AudioProcessingService.AudioFile;
import com.pres.pres_server.service.analyse.AudioProcessingService.AudioWindow;
import com.pres.pres_server.service.analyse.dto.SlideTransition;
import com.pres.pres_server.service.analyse.dto.WhisperSegment;
import com.pres.pres_server.service.analyse.utils.SlideSegmentExtractor;
import com.pres.pres_server.service.analyse.utils.SlideSegmentExtractor.SlideInterval;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 오디오 분석 오케스트레이션 서비스
 * - 전체 분석 플로우 조율
 * - 개별 분석 서비스 호출 및 결과 조합
 */
@Service
@RequiredArgsConstructor
public class AudioAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(AudioAnalysisService.class);
    private static final double WINDOW_SEC = 30.0;

    // Core services
    private final AudioProcessingService audioProcessingService;
    private final WhisperService whisperService;
    private final SlideSegmentExtractor slideSegmentExtractor;

    // Analysis services
    private final FillerService fillerService;
    private final SpeechSpeedService speechSpeedService;
    private final RepetitiveTextAnalysisService repetitiveTextAnalysisService;
    private final SilenceDetectionService silenceDetectionService;
    private final ScriptAccuracyService scriptAccuracyService;

    // Repositories
    private final PresentationFileRepository presentationFileRepository;
    private final CueCardRepository cueCardRepository;

    /**
     * 오디오 분석 (MultipartFile) - 주 진입점
     */
    public AnalysisResult analyzeAudio(
            MultipartFile audioFile,
            Long projectId,
            List<SlideTransition> slideTransitions,
            List<String> slideScripts) throws Exception {

        log.info("[AudioAnalysis] Started: file='{}', size={}, projectId={}",
                audioFile.getOriginalFilename(), audioFile.getSize(), projectId);

        AudioFile convertedAudio = null;
        try {
            convertedAudio = audioProcessingService.convertToWav(audioFile);
            return performAnalysis(convertedAudio, projectId, slideTransitions, slideScripts);
        } finally {
            if (convertedAudio != null) {
                audioProcessingService.cleanup(convertedAudio);
            }
        }
    }

    /**
     * 오디오 분석 (파일 경로)
     */
    public AnalysisResult analyzeAudio(
            String filePath,
            Long projectId,
            List<SlideTransition> slideTransitions) throws Exception {

        log.info("[AudioAnalysis] Started: filePath='{}', projectId={}", filePath, projectId);

        AudioFile convertedAudio = null;
        try {
            convertedAudio = audioProcessingService.convertToWav(filePath);
            return performAnalysis(convertedAudio, projectId, slideTransitions, null);
        } finally {
            if (convertedAudio != null) {
                audioProcessingService.cleanup(convertedAudio);
            }
        }
    }

    /**
     * 핵심 분석 로직
     */
    private AnalysisResult performAnalysis(
            AudioFile audioFile,
            Long projectId,
            List<SlideTransition> slideTransitions,
            List<String> slideScripts) throws Exception {

        log.info("  - Audio duration: {} sec", audioFile.getDurationSeconds());

        // 1. Whisper STT (타임스탬프 포함)
        WhisperService.TranscriptionResult sttResult = whisperService
                .transcribeWithTimestamps(audioFile.getFile(), true);
        List<WhisperSegment> segments = sttResult.getSegments();
        log.info("  - Whisper segments: {}", segments != null ? segments.size() : 0);

        // 2. 윈도우 기반 분석 (30초 단위)
        List<AudioWindow> audioWindows = audioProcessingService
                .splitIntoWindows(audioFile, WINDOW_SEC);
        List<WindowDto> windowResults = analyzeWindows(audioWindows);
        log.info("  - Window analysis: {} windows", windowResults.size());

        // 3. 전체 STT 텍스트 생성
        String fullSttText = windowResults.stream()
                .filter(w -> "SUCCESS".equals(w.getStatus()))
                .map(WindowDto::getTranscript)
                .collect(Collectors.joining(" "));
        log.info("  - Full STT text: {} chars", fullSttText.length());

        // 4. 전체 기반 분석
        RepetitiveTextAnalysisService.RepetitionAnalysisResult repetitionResult = analyzeRepetition(fullSttText,
                segments, slideTransitions);

        SilenceDetectionService.SilenceStatistics silenceStats = silenceDetectionService.calculateStatistics(
                silenceDetectionService.detectSilences(segments));

        ScriptAccuracyService.AccuracyAnalysisResult accuracyResult = analyzeAccuracy(fullSttText, projectId);

        // 5. 슬라이드별 분석 (옵션)
        SlideAnalysisResult slideAnalysis = (slideTransitions != null && !slideTransitions.isEmpty())
                ? performSlideAnalysis(segments, audioFile.getDurationSeconds(),
                        slideTransitions, slideScripts)
                : SlideAnalysisResult.empty();

        log.info("[AudioAnalysis] Complete: {} windows, {} slides",
                windowResults.size(),
                slideTransitions != null ? slideTransitions.size() : 0);

        // 6. 결과 조합
        return AnalysisResult.builder()
                .windows(windowResults)
                .totalDurationSeconds(audioFile.getDurationSeconds())
                .fullSttText(fullSttText)
                .repetitionResult(repetitionResult)
                .silenceStats(silenceStats)
                .accuracyResult(accuracyResult)
                .slideAnalysis(slideAnalysis)
                .build();
    }

    /**
     * 슬라이드별 분석 수행
     */
    private SlideAnalysisResult performSlideAnalysis(
            List<WhisperSegment> segments,
            double totalDuration,
            List<SlideTransition> transitions,
            List<String> slideScripts) {

        log.info("  - Slide analysis started: {} slides", transitions.size());

        // 슬라이드 구간 생성
        List<SlideInterval> intervals = slideSegmentExtractor
                .createSlideIntervals(transitions, totalDuration);

        // 슬라이드별 segment 분할
        List<List<WhisperSegment>> slideSegmentsList = slideSegmentExtractor
                .splitSegmentsBySlides(segments, intervals);

        // 슬라이드별 STT 텍스트 추출
        List<String> slideSttTexts = slideSegmentExtractor
                .extractSlideSttTexts(segments, intervals);

        // 1) 필러 분석
        List<FillerService.SlideFillerDto> fillerResults = fillerService
                .countFillersBySlides(slideSttTexts);
        log.info("    • Filler analysis: {} slides", fillerResults.size());

        // 2) 침묵 분석
        List<List<SilenceDetectionService.SilenceInterval>> silenceResults = silenceDetectionService
                .detectSilencesBySlides(slideSegmentsList);
        log.info("    • Silence analysis: {} slides", silenceResults.size());

        // 3) 정확도 분석 (대본이 있을 때만)
        List<ScriptAccuracyService.AccuracyAnalysisResult> accuracyResults = Collections.emptyList();

        if (slideScripts != null && slideScripts.size() == slideSttTexts.size()) {
            accuracyResults = scriptAccuracyService
                    .analyzeAccuracyBySlides(slideScripts, slideSttTexts);
            log.info("    • Accuracy analysis: {} slides", accuracyResults.size());
        }

        // 4) SPM 분석 (슬라이드별)
        List<SlideSpmResult> spmResults = analyzeSlideSpm(slideSttTexts, intervals);
        log.info("    • SPM analysis: {} slides", spmResults.size());

        // 5) 반복 어휘 분석 (슬라이드별) - 전체 분석에서 추출
        // 전체 STT 텍스트로 분석 수행
        String fullSttText = slideSttTexts.stream()
                .filter(text -> text != null && !text.trim().isEmpty())
                .collect(Collectors.joining(" "));

        RepetitiveTextAnalysisService.RepetitionAnalysisResult repetitionAnalysis = repetitiveTextAnalysisService
                .analyzeRepetition(
                        fullSttText,
                        transitions,
                        segments);

        List<RepetitiveTextAnalysisService.SlideRepetition> repetitionResults = repetitionAnalysis
                .getSlideRepetitions();
        log.info("    • Repetition analysis: {} slides", repetitionResults.size());

        return SlideAnalysisResult.builder()
                .fillerResults(fillerResults)
                .silenceResults(silenceResults)
                .accuracyResults(accuracyResults)
                .spmResults(spmResults)
                .repetitionResults(repetitionResults)
                .slideSttTexts(slideSttTexts)
                .intervals(intervals)
                .build();
    }

    /**
     * 슬라이드별 SPM 분석
     */
    private List<SlideSpmResult> analyzeSlideSpm(List<String> slideSttTexts, List<SlideInterval> intervals) {
        List<SlideSpmResult> results = new ArrayList<>();

        for (int i = 0; i < slideSttTexts.size(); i++) {
            String text = slideSttTexts.get(i);
            SlideInterval interval = intervals.get(i);

            if (text == null || text.trim().isEmpty()) {
                continue;
            }

            // 슬라이드 구간의 시간(초) 계산
            double durationSeconds = interval.getEndTime() - interval.getStartTime();

            if (durationSeconds <= 0) {
                continue;
            }

            // SPM 계산
            int syllableCount = speechSpeedService.countKoreanSyllables(text);
            int spm = speechSpeedService.calculateSpm(syllableCount, durationSeconds);
            int spmScore = speechSpeedService.mapSpmToScore(spm);

            results.add(SlideSpmResult.builder()
                    .slideNumber(i + 1)
                    .spm(spm)
                    .spmScore(spmScore)
                    .build());
        }

        return results;
    }

    /**
     * 윈도우별 분석
     */
    private List<WindowDto> analyzeWindows(List<AudioWindow> audioWindows) {
        List<WindowDto> results = new ArrayList<>();

        for (AudioWindow window : audioWindows) {
            try {
                log.debug("    - Analyzing window {} ({}-{} sec)",
                        window.getWindowIndex(),
                        window.getStartTime(),
                        window.getStartTime() + window.getDuration());

                WindowDto result = analyzeSingleWindow(window);
                results.add(result);

            } catch (Exception e) {
                log.error("    × Window {} failed: {}",
                        window.getWindowIndex(), e.getMessage(), e);

                WindowDto failedResult = new WindowDto(
                        window.getStartTime(),
                        window.getStartTime() + window.getDuration(),
                        "FAILED",
                        toUserErrorMessage(e));
                results.add(failedResult);

            } finally {
                window.cleanup();
            }
        }

        return results;
    }

    /**
     * 단일 윈도우 분석
     */
    private WindowDto analyzeSingleWindow(AudioWindow window) throws Exception {
        // STT
        String text = whisperService.transcribe(window.getFile());
        if (text == null || text.trim().isEmpty()) {
            text = "";
        }

        // 필러워드
        Map<String, Integer> fillerCounts = fillerService.countFillersByRegex(text);

        // SPM 계산
        int syllableCount = speechSpeedService.countKoreanSyllables(text);
        int spm = speechSpeedService.calculateSpm(syllableCount, window.getDuration());
        int spmScore = speechSpeedService.mapSpmToScore(spm);

        return new WindowDto(
                window.getStartTime(),
                window.getStartTime() + window.getDuration(),
                text,
                fillerCounts,
                spm,
                spmScore);
    }

    /**
     * 반복 어휘 분석
     */
    private RepetitiveTextAnalysisService.RepetitionAnalysisResult analyzeRepetition(
            String fullSttText,
            List<WhisperSegment> segments,
            List<SlideTransition> slideTransitions) {

        if (fullSttText == null || fullSttText.trim().isEmpty()) {
            log.warn("  - No STT text, skipping repetition analysis");
            return RepetitiveTextAnalysisService.RepetitionAnalysisResult
                    .failed("No STT text");
        }

        try {
            RepetitiveTextAnalysisService.RepetitionAnalysisResult result;

            if (segments != null && slideTransitions != null && !slideTransitions.isEmpty()) {
                result = repetitiveTextAnalysisService.analyzeRepetition(
                        fullSttText, slideTransitions, segments);
            } else {
                result = repetitiveTextAnalysisService.analyzeRepetition(fullSttText);
            }

            if (result.isSuccess()) {
                log.info("  - Repetition analysis: score={}, patterns={}",
                        result.getRepetitionScore(),
                        result.getNGramPatterns().size());
            }

            return result;

        } catch (Exception e) {
            log.error("  - Repetition analysis failed", e);
            return RepetitiveTextAnalysisService.RepetitionAnalysisResult
                    .failed("Exception: " + e.getMessage());
        }
    }

    /**
     * 정확도 분석
     */
    private ScriptAccuracyService.AccuracyAnalysisResult analyzeAccuracy(
            String fullSttText,
            Long projectId) {

        if (projectId == null) {
            log.info("  - No projectId, skipping accuracy analysis");
            return ScriptAccuracyService.AccuracyAnalysisResult
                    .defaultResult("No projectId provided");
        }

        if (fullSttText == null || fullSttText.trim().isEmpty()) {
            log.warn("  - No STT text, skipping accuracy analysis");
            return ScriptAccuracyService.AccuracyAnalysisResult
                    .defaultResult("No STT text generated");
        }

        try {
            Optional<PresentationFile> presentationFileOpt = presentationFileRepository
                    .findByProject_ProjectId(projectId);

            if (presentationFileOpt.isEmpty()) {
                log.info("  - No presentation file for projectId={}", projectId);
                return ScriptAccuracyService.AccuracyAnalysisResult
                        .defaultResult("No presentation file found");
            }

            Long fileId = presentationFileOpt.get().getFileId();
            List<CueCard> cueCards = cueCardRepository
                    .findByPresentationFile_FileIdOrderBySlideNumberAscModeAscSectionNumberAsc(fileId);

            if (cueCards.isEmpty()) {
                log.info("  - No cue cards for fileId={}", fileId);
                return ScriptAccuracyService.AccuracyAnalysisResult
                        .defaultResult("No cue cards found");
            }

            String fullScript = cueCards.stream()
                    .map(CueCard::getContent)
                    .filter(content -> content != null && !content.trim().isEmpty())
                    .collect(Collectors.joining(" "));

            if (fullScript.isEmpty()) {
                log.warn("  - Script is empty");
                return ScriptAccuracyService.AccuracyAnalysisResult
                        .defaultResult("Script is empty");
            }

            ScriptAccuracyService.AccuracyAnalysisResult result = scriptAccuracyService
                    .analyzeAccuracy(fullScript, fullSttText);

            if (result.isSuccess()) {
                log.info("  - Accuracy: score={}, similarity={}, keywords={}/{}",
                        result.getAccuracyScore(),
                        String.format("%.2f", result.getScriptSimilarity()),
                        result.getMatchedKeywordCount(),
                        result.getTotalKeywordCount());
            }

            return result;

        } catch (Exception e) {
            log.error("  - Accuracy analysis failed", e);
            return ScriptAccuracyService.AccuracyAnalysisResult
                    .defaultResult("Analysis failed: " + e.getMessage());
        }
    }

    /**
     * 예외를 사용자 친화적 메시지로 변환
     */
    private String toUserErrorMessage(Exception e) {
        String message = e.getMessage();

        if (e instanceof java.io.IOException) {
            if (message != null && message.toLowerCase().contains("timeout")) {
                return "음성 인식 서비스 응답 시간이 초과되었습니다.";
            }
            return "네트워크 오류가 발생했습니다.";
        }

        if (e instanceof IllegalArgumentException) {
            return "오디오 파일 형식에 문제가 있습니다.";
        }

        if (e instanceof NullPointerException) {
            return "필수 데이터가 누락되었습니다.";
        }

        if (message != null) {
            if (message.contains("Whisper") || message.contains("transcribe")) {
                return "음성 인식 중 오류가 발생했습니다.";
            }
        }

        return "일시적인 오류가 발생했습니다.";
    }

    /**
     * 분석 결과 (불변 객체)
     */
    @lombok.Getter
    @lombok.Builder
    public static class AnalysisResult {
        private final List<WindowDto> windows;
        private final double totalDurationSeconds;
        private final String fullSttText;

        private final RepetitiveTextAnalysisService.RepetitionAnalysisResult repetitionResult;
        private final SilenceDetectionService.SilenceStatistics silenceStats;
        private final ScriptAccuracyService.AccuracyAnalysisResult accuracyResult;

        @lombok.Builder.Default
        private final SlideAnalysisResult slideAnalysis = SlideAnalysisResult.empty();
    }

    /**
     * 슬라이드별 분석 결과
     */
    @lombok.Getter
    @lombok.Builder
    public static class SlideAnalysisResult {
        @lombok.Builder.Default
        private final List<FillerService.SlideFillerDto> fillerResults = Collections.emptyList();

        @lombok.Builder.Default
        private final List<List<SilenceDetectionService.SilenceInterval>> silenceResults = Collections.emptyList();

        @lombok.Builder.Default
        private final List<ScriptAccuracyService.AccuracyAnalysisResult> accuracyResults = Collections.emptyList();

        @lombok.Builder.Default
        private final List<SlideSpmResult> spmResults = Collections.emptyList();

        @lombok.Builder.Default
        private final List<RepetitiveTextAnalysisService.SlideRepetition> repetitionResults = Collections.emptyList();

        @lombok.Builder.Default
        private final List<String> slideSttTexts = Collections.emptyList();

        @lombok.Builder.Default
        private final List<SlideSegmentExtractor.SlideInterval> intervals = Collections.emptyList();

        public static SlideAnalysisResult empty() {
            return SlideAnalysisResult.builder().build();
        }

        public boolean isEmpty() {
            return fillerResults.isEmpty()
                    && silenceResults.isEmpty()
                    && accuracyResults.isEmpty()
                    && spmResults.isEmpty()
                    && repetitionResults.isEmpty();
        }
    }

    /**
     * 슬라이드별 SPM 결과
     */
    @lombok.Getter
    @lombok.Builder
    public static class SlideSpmResult {
        private final int slideNumber;
        private final int spm; // 사용자 SPM
        private final int spmScore; // SPM 점수 (0-100)
    }
}
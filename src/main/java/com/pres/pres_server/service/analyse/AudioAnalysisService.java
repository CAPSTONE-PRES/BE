package com.pres.pres_server.service.analyse;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 오디오 분석 전체 플로우를 조율하는 서비스
 * 모든 분석을 수행하고 결과 반환 (DB 저장은 AnalysisResultService에서 담당)
 */
@Service
@RequiredArgsConstructor
public class AudioAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(AudioAnalysisService.class);
    private static final double WINDOW_SEC = 30.0; // 30초 윈도우

    private final AudioProcessingService audioProcessingService;
    private final WhisperService whisperService;
    private final FillerService fillerService;
    private final SpeechSpeedService speechSpeedService;
    private final RepetitiveTextAnalysisService repetitiveTextAnalysisService;
    private final SilenceDetectionService silenceDetectionService;
    private final ScriptAccuracyService scriptAccuracyService;
    private final PresentationFileRepository presentationFileRepository;
    private final CueCardRepository cueCardRepository;
    private final ObjectMapper objectMapper;

    /**
     * 슬라이드별 분석 플로우 (slideTransitions, WhisperSegment 기반)
     * 
     * @param segments         WhisperSegment 리스트
     * @param slideTransitions 슬라이드 전환 정보
     * @return 슬라이드별 필러 분석 결과 리스트
     */
    private List<FillerService.SlideFillerDto> analyzeFillersBySlides(List<WhisperSegment> segments,
            List<SlideTransition> slideTransitions) {
        List<String> slideTexts = new ArrayList<>();
        if (slideTransitions == null || slideTransitions.isEmpty() || segments == null || segments.isEmpty()) {
            return new ArrayList<>();
        }
        List<SlideTransition> sortedTransitions = new ArrayList<>(slideTransitions);
        sortedTransitions.sort(Comparator.comparingDouble(SlideTransition::getTimestamp));
        for (int i = 0; i < sortedTransitions.size(); i++) {
            double start = sortedTransitions.get(i).getTimestamp();
            double end = (i + 1 < sortedTransitions.size()) ? sortedTransitions.get(i + 1).getTimestamp()
                    : Double.POSITIVE_INFINITY;
            StringBuilder sb = new StringBuilder();
            for (WhisperSegment seg : segments) {
                double segMid = (seg.getStart() + seg.getEnd()) / 2.0;
                if (segMid >= start && segMid < end) {
                    if (sb.length() > 0)
                        sb.append(" ");
                    sb.append(seg.getText());
                }
            }
            slideTexts.add(sb.toString());
        }
        return fillerService.countFillersBySlides(slideTexts);
    }

    /**
     * 저장된 오디오 파일 경로를 사용한 전체 분석
     * 
     * @param filePath  저장된 오디오 파일 경로
     * @param projectId 프로젝트 ID (정확도 분석용, null 가능)
     * @return 모든 분석 결과
     * @throws Exception 변환, 분할, 분석 중 오류 발생 시
     */
    public AnalysisResult analyzeAudio(String filePath, Long projectId) throws Exception {
        log.info("[AudioAnalysis] Analysis started: filePath='{}', projectId={}", filePath, projectId);
        AudioFile convertedAudio = null;
        try {
            convertedAudio = audioProcessingService.convertToWav(filePath);
            return analyzeAudioInternal(convertedAudio, projectId, filePath);
        } finally {
            if (convertedAudio != null) {
                audioProcessingService.cleanup(convertedAudio);
            }
        }
    }

    /**
     * 오디오 파일 전체 분석
     * 
     * @param audioFile 분석할 오디오 파일
     * @param projectId 프로젝트 ID (정확도 분석용, null 가능)
     * @return 모든 분석 결과
     * @throws Exception 변환, 분할, 분석 중 오류 발생 시
     */
    public AnalysisResult analyzeAudio(MultipartFile audioFile, Long projectId) throws Exception {
        log.info("[AudioAnalysis] Analysis started: originalName='{}', size={} bytes, projectId={}",
                audioFile.getOriginalFilename(), audioFile.getSize(), projectId);
        AudioFile convertedAudio = null;
        try {
            convertedAudio = audioProcessingService.convertToWav(audioFile);
            return analyzeAudioInternal(convertedAudio, projectId, convertedAudio.getFile().getAbsolutePath());
        } finally {
            if (convertedAudio != null) {
                audioProcessingService.cleanup(convertedAudio);
            }
        }
    }

    /**
     * analyzeAudio의 공통 로직을 분리한 내부 메서드
     * 
     * @param convertedAudio 변환된 오디오 파일
     * @param projectId      프로젝트 ID
     * @param silencePath    공백 분석에 사용할 오디오 파일 경로
     * @return 모든 분석 결과
     * @throws Exception 분석 중 오류 발생 시
     */
    private AnalysisResult analyzeAudioInternal(AudioFile convertedAudio, Long projectId, String silencePath)
            throws Exception {
        log.info("  - Audio conversion complete: duration={} sec", convertedAudio.getDurationSeconds());

        // 2. 30초 윈도우로 분할
        List<AudioWindow> audioWindows = audioProcessingService.splitIntoWindows(convertedAudio, WINDOW_SEC);
        log.info("  - Window split complete: {} windows ({} sec each)", audioWindows.size(), WINDOW_SEC);

        // 3. 각 윈도우 분석 (STT + 필러 + SPM)
        List<WindowDto> windowResults = analyzeWindows(audioWindows);

        // 4. 전체 STT 텍스트 생성
        String fullSttText = windowResults.stream()
                .filter(w -> "SUCCESS".equals(w.getStatus()))
                .map(WindowDto::getTranscript)
                .reduce((a, b) -> a + " " + b)
                .orElse("");

        // 5. 추가 분석 수행 (반복, 공백, 정확도)
        RepetitiveTextAnalysisService.RepetitionAnalysisResult repetitionResult = analyzeRepetition(fullSttText);
        SilenceDetectionService.SilenceStatistics silenceStats = analyzeSilence(silencePath);
        ScriptAccuracyService.AccuracyAnalysisResult accuracyResult = analyzeAccuracy(fullSttText, projectId);

        // 6. 분석 결과 요약

        log.info("[AudioAnalysis] Analysis complete: total {} windows",
                windowResults.size());

        return new AnalysisResult(
                windowResults,
                convertedAudio.getDurationSeconds(),
                fullSttText,
                repetitionResult,
                silenceStats,
                accuracyResult);
    }

    /**
     * 여러 윈도우를 순차적으로 분석
     * 
     * @param audioWindows 분석할 윈도우 리스트
     * @return 윈도우별 분석 결과
     */
    private List<WindowDto> analyzeWindows(List<AudioWindow> audioWindows) {
        List<WindowDto> results = new ArrayList<>();

        for (AudioWindow window : audioWindows) {
            try {
                log.info("    - Analysing window {} ({} - {} sec)",
                        window.getWindowIndex(),
                        window.getStartTime(),
                        window.getStartTime() + window.getDuration());

                WindowDto result = analyzeSingleWindow(window);
                results.add(result);
                if ("SUCCESS".equals(result.getStatus())
                        && (result.getTranscript() == null || result.getTranscript().trim().isEmpty())) {
                    log.warn("    • SUCCESS, but transcript is empty (window index: {})", window.getWindowIndex());
                }

                log.info("    - Window {} analysis complete: spm={}, fillers={}",
                        window.getWindowIndex(), result.getSpm(), result.getFillers().size());

            } catch (Exception e) {
                // 실패: 해당 윈도우만 건너뛰고 실패 정보 기록

                // 백엔드 로그: 개발자용 상세 정보
                log.error("❌ window {} analysing failed - exception type: {}, message: {}",
                        window.getWindowIndex(),
                        e.getClass().getSimpleName(),
                        e.getMessage(),
                        e);

                // 프론트엔드: 사용자 친화적 메시지
                String userFriendlyMessage = toUserErrorMessage(e);

                WindowDto failedResult = new WindowDto(
                        window.getStartTime(),
                        window.getStartTime() + window.getDuration(),
                        "FAILED",
                        userFriendlyMessage);

                results.add(failedResult);

            } finally {
                // 윈도우 임시 파일 정리
                window.cleanup();
            }
        }

        return results;
    }

    /**
     * 단일 윈도우 분석
     * 
     * @param window 분석할 윈도우
     * @return 분석 결과
     * @throws Exception 분석 중 오류 발생 시
     */
    private WindowDto analyzeSingleWindow(AudioWindow window) throws Exception {
        // 1. Whisper → text 추출
        String text = whisperService.transcribe(window.getFile());
        if (text == null || text.trim().isEmpty()) {
            log.warn("      - STT is empty: windowIndex: {}, file: {}", window.getWindowIndex(),
                    window.getFile().getName());
            text = "";
        }
        log.debug("      - STT: {}", text);

        // 2. 필러워드 카운트
        Map<String, Integer> fillerCounts = fillerService.countFillersByRegex(text);
        log.debug("      - Filler count: {}", fillerCounts);

        // 3. 한글 음절 개수 세기
        int syllableCount = speechSpeedService.countKoreanSyllables(text);

        // 4. SPM 계산
        int spm = speechSpeedService.calculateSpm(syllableCount, window.getDuration());
        log.debug("      - SPM: {} (syllables: {})", spm, syllableCount);

        // 5. SPM 점수 매핑
        int spmScore = speechSpeedService.mapSpmToScore(spm);
        log.debug("      - SPM score: {}", spmScore);

        // 6. 결과 DTO 생성
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
     * 
     * @param fullSttText 전체 STT 텍스트
     * @return 반복 분석 결과
     */
    private RepetitiveTextAnalysisService.RepetitionAnalysisResult analyzeRepetition(String fullSttText) {
        if (fullSttText == null || fullSttText.trim().isEmpty()) {
            log.warn("  - No STT text, skipping repetition analysis");
            return RepetitiveTextAnalysisService.RepetitionAnalysisResult
                    .failed("No STT text, skipping repetition analysis");
        }

        try {
            RepetitiveTextAnalysisService.RepetitionAnalysisResult result = repetitiveTextAnalysisService
                    .analyzeRepetition(fullSttText);

            if (result.isSuccess()) {
                log.info("  - Repetition analysis complete: score={}, N-gram={}",
                        result.getRepetitionScore(),
                        result.getNGramPatterns().size());
            } else {
                log.warn("  - Repetition analysis failed, using default. error: {}", result.getErrorMessage());
            }

            return result;
        } catch (Exception e) {
            log.error("  • 반복 어휘 분석 중 오류 발생", e);
            return RepetitiveTextAnalysisService.RepetitionAnalysisResult.failed("반복 분석 중 예외 발생: " + e.getMessage());
        }
    }

    /**
     * 슬라이드 전환 타임스탬프를 함께 전달받는 분석 오버로드
     */
    public AnalysisResult analyzeAudio(String filePath, Long projectId,
            List<SlideTransition> slideTransitions) throws Exception {
        log.info("[AudioAnalysis] Analysis (with slide timestamps) started: filePath='{}', projectId={}", filePath,
                projectId);

        AudioFile convertedAudio = null;

        try {
            convertedAudio = audioProcessingService.convertToWav(filePath);

            // Whisper with timestamps to obtain segments
            WhisperService.TranscriptionResult transResult = whisperService
                    .transcribeWithTimestamps(convertedAudio.getFile(), true);
            List<WhisperSegment> segments = transResult.getSegments();

            // 기존 윈도우 분할 및 per-window 분석
            List<AudioWindow> audioWindows = audioProcessingService.splitIntoWindows(convertedAudio, WINDOW_SEC);
            List<WindowDto> windowResults = analyzeWindows(audioWindows);

            String fullSttText = windowResults.stream()
                    .filter(w -> "SUCCESS".equals(w.getStatus()))
                    .map(WindowDto::getTranscript)
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");

            // 슬라이드별 필러 분석
            List<FillerService.SlideFillerDto> slideFillerResults = analyzeFillersBySlides(segments, slideTransitions);

            // 슬라이드별 STT 텍스트 추출
            List<String> slideSttTexts = new ArrayList<>();
            if (slideTransitions != null && segments != null) {
                List<SlideTransition> sortedTransitions = new ArrayList<>(slideTransitions);
                sortedTransitions.sort(Comparator.comparingDouble(SlideTransition::getTimestamp));
                for (int i = 0; i < sortedTransitions.size(); i++) {
                    double start = sortedTransitions.get(i).getTimestamp();
                    double end = (i + 1 < sortedTransitions.size()) ? sortedTransitions.get(i + 1).getTimestamp()
                            : Double.POSITIVE_INFINITY;
                    StringBuilder sb = new StringBuilder();
                    for (WhisperSegment seg : segments) {
                        double segMid = (seg.getStart() + seg.getEnd()) / 2.0;
                        if (segMid >= start && segMid < end) {
                            if (sb.length() > 0)
                                sb.append(" ");
                            sb.append(seg.getText());
                        }
                    }
                    slideSttTexts.add(sb.toString());
                }
            }

            // (예시) 슬라이드별 대본 리스트는 외부에서 받아온다고 가정
            List<String> slideScripts = new ArrayList<>(); // TODO: 실제 대본 리스트 매핑 필요

            // 슬라이드별 정확도 분석
            List<ScriptAccuracyService.AccuracyAnalysisResult> slideAccuracyResults = null;
            if (slideScripts.size() == slideSttTexts.size() && !slideScripts.isEmpty()) {
                slideAccuracyResults = scriptAccuracyService.analyzeAccuracyBySlides(slideScripts, slideSttTexts);
            }

            // (기존 반복어휘/공백/정확도 분석은 전체 기준, 필요시 슬라이드별로 확장)
            RepetitiveTextAnalysisService.RepetitionAnalysisResult repetitionResult;
            if (segments != null) {
                repetitionResult = repetitiveTextAnalysisService.analyzeRepetition(fullSttText, slideTransitions,
                        segments);
            } else {
                repetitionResult = repetitiveTextAnalysisService.analyzeRepetition(fullSttText, slideTransitions);
            }

            SilenceDetectionService.SilenceStatistics silenceStats = silenceDetectionService.calculateStatistics(
                    silenceDetectionService.detectSilences(segments));

            ScriptAccuracyService.AccuracyAnalysisResult accuracyResult = analyzeAccuracy(fullSttText, projectId);

            // AnalysisResult에 슬라이드별 필러/정확도 분석 결과 추가
            return new AnalysisResult(
                    windowResults,
                    convertedAudio.getDurationSeconds(),
                    fullSttText,
                    repetitionResult,
                    silenceStats,
                    accuracyResult,
                    slideFillerResults,
                    slideAccuracyResults);

        } finally {
            if (convertedAudio != null) {
                audioProcessingService.cleanup(convertedAudio);
            }
        }
    }

    public AnalysisResult analyzeAudio(MultipartFile audioFile, Long projectId,
            List<SlideTransition> slideTransitions, List<String> slideScripts) throws Exception {
        log.info("[AudioAnalysis] Analysis (with slides) started: originalName='{}', size={} bytes, projectId={}",
                audioFile.getOriginalFilename(), audioFile.getSize(), projectId);

        AudioFile convertedAudio = null;

        try {
            convertedAudio = audioProcessingService.convertToWav(audioFile);

            WhisperService.TranscriptionResult transResult = whisperService
                    .transcribeWithTimestamps(convertedAudio.getFile(), true);
            List<WhisperSegment> segments = transResult.getSegments();

            List<AudioWindow> audioWindows = audioProcessingService.splitIntoWindows(convertedAudio, WINDOW_SEC);
            List<WindowDto> windowResults = analyzeWindows(audioWindows);

            String fullSttText = windowResults.stream()
                    .filter(w -> "SUCCESS".equals(w.getStatus()))
                    .map(WindowDto::getTranscript)
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");

            List<SlideTransition> effectiveTransitions = slideTransitions;
            if (segments != null && slideTransitions != null && !slideTransitions.isEmpty()) {
                effectiveTransitions = new ArrayList<>(slideTransitions);
                effectiveTransitions.sort(Comparator.comparingDouble(SlideTransition::getTimestamp));
            }

            // 슬라이드별 STT 텍스트 추출 및 WhisperSegment 분할
            List<String> slideSttTexts = new ArrayList<>();
            List<List<WhisperSegment>> slideSegmentsList = new ArrayList<>();
            if (effectiveTransitions != null && segments != null) {
                List<SlideTransition> sortedTransitions = new ArrayList<>(effectiveTransitions);
                sortedTransitions.sort(Comparator.comparingDouble(SlideTransition::getTimestamp));
                for (int i = 0; i < sortedTransitions.size(); i++) {
                    double start = sortedTransitions.get(i).getTimestamp();
                    double end = (i + 1 < sortedTransitions.size()) ? sortedTransitions.get(i + 1).getTimestamp()
                            : Double.POSITIVE_INFINITY;
                    StringBuilder sb = new StringBuilder();
                    List<WhisperSegment> slideSegs = new ArrayList<>();
                    for (WhisperSegment seg : segments) {
                        double segMid = (seg.getStart() + seg.getEnd()) / 2.0;
                        if (segMid >= start && segMid < end) {
                            if (sb.length() > 0)
                                sb.append(" ");
                            sb.append(seg.getText());
                            slideSegs.add(seg);
                        }
                    }
                    slideSttTexts.add(sb.toString());
                    slideSegmentsList.add(slideSegs);
                }
            }

            // 슬라이드별 정확도 분석
            List<ScriptAccuracyService.AccuracyAnalysisResult> slideAccuracyResults = null;
            if (slideScripts != null && slideScripts.size() == slideSttTexts.size() && !slideScripts.isEmpty()) {
                slideAccuracyResults = scriptAccuracyService.analyzeAccuracyBySlides(slideScripts, slideSttTexts);
            }

            // 슬라이드별 필러 분석
            List<FillerService.SlideFillerDto> slideFillerResults = analyzeFillersBySlides(segments,
                    effectiveTransitions);

            // 슬라이드별 침묵(공백) 분석
            List<List<SilenceDetectionService.SilenceInterval>> slideSilenceResults = null;
            if (slideSegmentsList != null && !slideSegmentsList.isEmpty()) {
                slideSilenceResults = silenceDetectionService.detectSilencesBySlides(slideSegmentsList);
            }

            RepetitiveTextAnalysisService.RepetitionAnalysisResult repetitionResult;
            if (segments != null) {
                repetitionResult = repetitiveTextAnalysisService.analyzeRepetition(fullSttText, effectiveTransitions,
                        segments);
            } else {
                repetitionResult = repetitiveTextAnalysisService.analyzeRepetition(fullSttText, effectiveTransitions);
            }

            SilenceDetectionService.SilenceStatistics silenceStats = silenceDetectionService.calculateStatistics(
                    silenceDetectionService.detectSilences(segments));

            ScriptAccuracyService.AccuracyAnalysisResult accuracyResult = analyzeAccuracy(fullSttText, projectId);

            log.info("✅ 분석 완료, {}개 윈도우 (sessionId: null)", windowResults.size());

            return new AnalysisResult(
                    windowResults,
                    convertedAudio.getDurationSeconds(),
                    fullSttText,
                    repetitionResult,
                    silenceStats,
                    accuracyResult,
                    slideFillerResults,
                    slideAccuracyResults,
                    slideSilenceResults);

        } finally {
            if (convertedAudio != null) {
                audioProcessingService.cleanup(convertedAudio);
            }
        }
    }

    /**
     * 공백 분석
     * 
     * @param audioFilePath 오디오 파일 경로
     * @return 공백 분석 통계
     */
    private SilenceDetectionService.SilenceStatistics analyzeSilence(String audioFilePath) {
        try {
            // 1. WAV 변환
            AudioFile audioFile = audioProcessingService.convertToWav(audioFilePath);
            if (audioFile == null || audioFile.getFile() == null) {
                log.warn("  - WAV conversion failed, skipping silence analysis");
                return createEmptyStatistics();
            }

            // 2. Whisper API 호출 (timestamp 포함)
            WhisperService.TranscriptionResult result = whisperService.transcribeWithTimestamps(
                    audioFile.getFile(), true);

            // 3. segments 검증
            if (result == null || result.getSegments() == null || result.getSegments().isEmpty()) {
                log.warn("  - Whisper segments are empty, cannot detect silence");
                return createEmptyStatistics();
            }

            // 4. 공백 감지
            List<SilenceDetectionService.SilenceInterval> silences = silenceDetectionService
                    .detectSilences(result.getSegments());

            // 5. 통계 계산
            SilenceDetectionService.SilenceStatistics stats = silenceDetectionService.calculateStatistics(silences);

            log.info("  - Silence detection complete: count={}, totalDuration={}s",
                    stats.getSilenceCount(), String.format("%.2f", stats.getTotalSilenceDuration()));

            return stats;

        } catch (Exception e) {
            log.error("  - Error during silence detection", e);
            return createEmptyStatistics();
        }
    }

    /**
     * 발표 정확도 분석
     * 
     * @param fullSttText 전체 STT 텍스트
     * @param projectId   프로젝트 ID (null 가능)
     * @return 정확도 분석 결과
     */
    private ScriptAccuracyService.AccuracyAnalysisResult analyzeAccuracy(String fullSttText, Long projectId) {

        if (projectId == null) {
            log.info("  - No projectId, skipping accuracy analysis");
            return ScriptAccuracyService.AccuracyAnalysisResult
                    .defaultResult("No projectId provided. Please specify a valid projectId for accuracy analysis.");
        }

        if (fullSttText == null || fullSttText.trim().isEmpty()) {
            log.warn("  - No STT text, skipping accuracy analysis");
            return ScriptAccuracyService.AccuracyAnalysisResult
                    .defaultResult(
                            "No speech-to-text result was generated. Please check the audio quality or try again.");
        }

        try {
            Optional<PresentationFile> presentationFileOpt = presentationFileRepository
                    .findByProject_ProjectId(projectId);

            if (presentationFileOpt.isEmpty()) {
                log.info("  - No presentation file found for projectId={}, skipping accuracy analysis", projectId);
                return ScriptAccuracyService.AccuracyAnalysisResult
                        .defaultResult(
                                "No presentation file found for the given projectId. Please check if the project has an uploaded presentation file.");
            }

            Long fileId = presentationFileOpt.get().getFileId();
            java.util.List<CueCard> cueCards = cueCardRepository
                    .findByPresentationFile_FileIdOrderBySlideNumberAscModeAscSectionNumberAsc(fileId);

            if (cueCards.isEmpty()) {
                log.info("  - No cue cards found for fileId={}, skipping accuracy analysis", fileId);
                return ScriptAccuracyService.AccuracyAnalysisResult
                        .defaultResult(
                                "No cue cards found for the presentation file. Please check if the script (cue cards) is registered for this presentation.");
            }

            String fullScript = cueCards.stream()
                    .map(CueCard::getContent)
                    .filter(content -> content != null && !content.trim().isEmpty())
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");

            if (fullScript.isEmpty()) {
                log.warn("  - Script is empty, skipping accuracy analysis");
                return ScriptAccuracyService.AccuracyAnalysisResult
                        .defaultResult(
                                "The script for this presentation is empty. Please check the cue card contents.");
            }

            ScriptAccuracyService.AccuracyAnalysisResult accuracyResult = scriptAccuracyService
                    .analyzeAccuracy(fullScript, fullSttText);

            if (accuracyResult.isSuccess()) {
                try {
                    objectMapper.writeValueAsString(accuracyResult.getMissingKeywords());
                    log.info("  - Accuracy score: {} (similarity: {}, matched keywords: {}/{})",
                            accuracyResult.getAccuracyScore(),
                            String.format("%.2f", accuracyResult.getScriptSimilarity()),
                            accuracyResult.getMatchedKeywordCount(), accuracyResult.getTotalKeywordCount());
                } catch (Exception e) {
                    log.warn("  - Failed to convert missing keywords to JSON", e);
                }
                return accuracyResult;
            } else {
                log.warn("  - Accuracy analysis failed, returning default result");
                return ScriptAccuracyService.AccuracyAnalysisResult
                        .defaultResult("Accuracy analysis failed due to insufficient script or STT data.");
            }
        } catch (Exception e) {
            log.error("  - Error during accuracy analysis", e);
            if (e instanceof org.springframework.dao.DataAccessException) {
                return ScriptAccuracyService.AccuracyAnalysisResult
                        .defaultResult("Database error occurred while fetching script data. Please try again later.");
            }
            return ScriptAccuracyService.AccuracyAnalysisResult
                    .defaultResult("An unexpected error occurred during accuracy analysis: " + e.getMessage());
        }
    }

    /**
     * 빈 공백 통계 객체 생성
     */
    private SilenceDetectionService.SilenceStatistics createEmptyStatistics() {
        return SilenceDetectionService.SilenceStatistics.builder()
                .silenceCount(0)
                .totalSilenceDuration(0.0)
                .averageSilenceDuration(0.0)
                .longestSilence(0.0)
                .success(false)
                .build();
    }

    /**
     * 예외를 사용자 친화적인 메시지로 변환
     * 
     * @param e 발생한 예외
     * @return 사용자에게 표시할 메시지
     */
    private String toUserErrorMessage(Exception e) {
        String message = e.getMessage();

        // 1. IOException 계열 - 네트워크/파일 문제
        if (e instanceof java.io.IOException) {
            if (message != null && message.toLowerCase().contains("timeout")) {
                return "음성 인식 서비스 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.";
            }
            return "네트워크 오류가 발생했습니다. 인터넷 연결을 확인하고 다시 시도해주세요.";
        }

        // 2. IllegalArgumentException 계열 - 잘못된 데이터
        if (e instanceof IllegalArgumentException) {
            return "오디오 파일 형식에 문제가 있습니다. 올바른 파일을 업로드해주세요.";
        }

        // 3. NullPointerException - 데이터 누락
        if (e instanceof NullPointerException) {
            return "필수 데이터가 누락되었습니다. 파일을 다시 확인해주세요.";
        }

        // 4. 메시지로 판단
        if (message != null) {
            if (message.contains("Whisper") || message.contains("transcribe")) {
                return "음성 인식 중 오류가 발생했습니다. 오디오 품질을 확인하고 다시 시도해주세요.";
            }
            if (message.contains("filler") || message.contains("필러")) {
                return "필러워드 분석 중 오류가 발생했습니다.";
            }
            if (message.contains("SPM") || message.contains("speech")) {
                return "발화 속도 분석 중 오류가 발생했습니다.";
            }
        }

        // 5. 기본 메시지
        return "일시적인 오류가 발생했습니다. 계속 문제가 발생하면 관리자에게 문의해주세요.";
    }

    /**
     * 분석 결과를 담는 내부 클래스
     * 모든 분석 결과를 포함 (윈도우, 반복, 공백, 정확도)
     */
    public static class AnalysisResult {
        // 슬라이드별 분석 없는 경우를 위한 오버로드
        public AnalysisResult(
                List<WindowDto> windows,
                double totalDurationSeconds,
                String fullSttText,
                RepetitiveTextAnalysisService.RepetitionAnalysisResult repetitionResult,
                SilenceDetectionService.SilenceStatistics silenceStats,
                ScriptAccuracyService.AccuracyAnalysisResult accuracyResult) {
            this(windows, totalDurationSeconds, fullSttText, repetitionResult, silenceStats, accuracyResult, null, null,
                    null);
        }

        // 슬라이드별 분석 일부만 있는 경우를 위한 오버로드
        public AnalysisResult(
                List<WindowDto> windows,
                double totalDurationSeconds,
                String fullSttText,
                RepetitiveTextAnalysisService.RepetitionAnalysisResult repetitionResult,
                SilenceDetectionService.SilenceStatistics silenceStats,
                ScriptAccuracyService.AccuracyAnalysisResult accuracyResult,
                List<FillerService.SlideFillerDto> slideFillerResults,
                List<ScriptAccuracyService.AccuracyAnalysisResult> slideAccuracyResults) {
            this(windows, totalDurationSeconds, fullSttText, repetitionResult, silenceStats, accuracyResult,
                    slideFillerResults, slideAccuracyResults, null);
        }

        private final List<WindowDto> windows;
        private final double totalDurationSeconds;
        private final String fullSttText;

        // 추가 분석 결과들
        private final RepetitiveTextAnalysisService.RepetitionAnalysisResult repetitionResult;
        private final SilenceDetectionService.SilenceStatistics silenceStats;
        private final ScriptAccuracyService.AccuracyAnalysisResult accuracyResult;

        // 슬라이드별 필러 분석 결과
        private final List<FillerService.SlideFillerDto> slideFillerResults;
        // 슬라이드별 정확도 분석 결과
        private final List<ScriptAccuracyService.AccuracyAnalysisResult> slideAccuracyResults;
        // 슬라이드별 침묵(공백) 분석 결과
        private final List<List<SilenceDetectionService.SilenceInterval>> slideSilenceResults;

        public AnalysisResult(
                List<WindowDto> windows,
                double totalDurationSeconds,
                String fullSttText,
                RepetitiveTextAnalysisService.RepetitionAnalysisResult repetitionResult,
                SilenceDetectionService.SilenceStatistics silenceStats,
                ScriptAccuracyService.AccuracyAnalysisResult accuracyResult,
                List<FillerService.SlideFillerDto> slideFillerResults,
                List<ScriptAccuracyService.AccuracyAnalysisResult> slideAccuracyResults,
                List<List<SilenceDetectionService.SilenceInterval>> slideSilenceResults) {
            this.windows = windows;
            this.totalDurationSeconds = totalDurationSeconds;
            this.fullSttText = fullSttText;
            this.repetitionResult = repetitionResult;
            this.silenceStats = silenceStats;
            this.accuracyResult = accuracyResult;
            this.slideFillerResults = slideFillerResults;
            this.slideAccuracyResults = slideAccuracyResults;
            this.slideSilenceResults = slideSilenceResults;
        }

        public List<WindowDto> getWindows() {
            return windows;
        }

        public double getTotalDurationSeconds() {
            return totalDurationSeconds;
        }

        public String getFullSttText() {
            return fullSttText;
        }

        public RepetitiveTextAnalysisService.RepetitionAnalysisResult getRepetitionResult() {
            return repetitionResult;
        }

        public SilenceDetectionService.SilenceStatistics getSilenceStats() {
            return silenceStats;
        }

        public ScriptAccuracyService.AccuracyAnalysisResult getAccuracyResult() {
            return accuracyResult;
        }

        public List<FillerService.SlideFillerDto> getSlideFillerResults() {
            return slideFillerResults;
        }

        public List<ScriptAccuracyService.AccuracyAnalysisResult> getSlideAccuracyResults() {
            return slideAccuracyResults;
        }

        public List<List<SilenceDetectionService.SilenceInterval>> getSlideSilenceResults() {
            return slideSilenceResults;
        }
    }
}

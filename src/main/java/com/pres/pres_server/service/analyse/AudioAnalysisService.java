package com.pres.pres_server.service.analyse;

import com.pres.pres_server.dto.analyse.WindowDto;
import com.pres.pres_server.service.WhisperService;
import com.pres.pres_server.service.analyse.AudioProcessingService.AudioFile;
import com.pres.pres_server.service.analyse.AudioProcessingService.AudioWindow;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 오디오 분석 전체 플로우를 조율하는 서비스
 * 오디오 파일을 받아서 변환 → 분할 → 각 윈도우 분석 → 결과 반환까지의
 * 전체 프로세스를 관리
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

    /**
     * 저장된 오디오 파일 경로를 사용한 전체 분석
     * 
     * @param filePath 저장된 오디오 파일 경로
     * @return 윈도우별 분석 결과 리스트
     * @throws Exception 변환, 분할, 분석 중 오류 발생 시
     */
    public AnalysisResult analyzeAudio(String filePath) throws Exception {
        log.info("▶ 오디오 분석 시작: filePath='{}'", filePath);

        AudioFile convertedAudio = null;

        try {
            // 1. 오디오 파일 변환 (16kHz mono WAV)
            convertedAudio = audioProcessingService.convertToWav(filePath);
            log.info("  • 오디오 변환 완료: duration={} sec", convertedAudio.getDurationSeconds());

            // 2. 30초 윈도우로 분할
            List<AudioWindow> audioWindows = audioProcessingService.splitIntoWindows(convertedAudio, WINDOW_SEC);
            log.info("  • 윈도우 분할 완료: {} 개 ({} sec each)", audioWindows.size(), WINDOW_SEC);

            // 3. 각 윈도우 분석
            List<WindowDto> windowResults = analyzeWindows(audioWindows);

            // 4. 분석 결과 요약
            long successCount = windowResults.stream()
                    .filter(w -> "SUCCESS".equals(w.getStatus()))
                    .count();
            long failCount = windowResults.stream()
                    .filter(w -> "FAILED".equals(w.getStatus()))
                    .count();

            log.info("✅ 오디오 분석 완료: 총 {} 윈도우, 성공 {}, 실패 {}",
                    windowResults.size(), successCount, failCount);

            return new AnalysisResult(windowResults, convertedAudio.getDurationSeconds());

        } finally {
            // 변환된 오디오 파일 정리
            if (convertedAudio != null) {
                audioProcessingService.cleanup(convertedAudio);
            }
        }
    }

    /**
     * 오디오 파일 전체 분석
     * 
     * @param audioFile 분석할 오디오 파일
     * @return 윈도우별 분석 결과 리스트
     * @throws Exception 변환, 분할, 분석 중 오류 발생 시
     */
    public AnalysisResult analyzeAudio(MultipartFile audioFile) throws Exception {
        log.info("▶ 오디오 분석 시작: originalName='{}', size={} bytes",
                audioFile.getOriginalFilename(), audioFile.getSize());

        AudioFile convertedAudio = null;

        try {
            // 1. 오디오 파일 변환 (16kHz mono WAV)
            convertedAudio = audioProcessingService.convertToWav(audioFile);
            log.info("  • 오디오 변환 완료: duration={} sec", convertedAudio.getDurationSeconds());

            // 2. 30초 윈도우로 분할
            List<AudioWindow> audioWindows = audioProcessingService.splitIntoWindows(convertedAudio, WINDOW_SEC);
            log.info("  • 윈도우 분할 완료: {} 개 ({} sec each)", audioWindows.size(), WINDOW_SEC);

            // 3. 각 윈도우 분석
            List<WindowDto> windowResults = analyzeWindows(audioWindows);

            // 4. 분석 결과 요약
            long successCount = windowResults.stream()
                    .filter(w -> "SUCCESS".equals(w.getStatus()))
                    .count();
            long failCount = windowResults.stream()
                    .filter(w -> "FAILED".equals(w.getStatus()))
                    .count();

            log.info("✅ 오디오 분석 완료: 총 {} 윈도우, 성공 {}, 실패 {}",
                    windowResults.size(), successCount, failCount);

            return new AnalysisResult(windowResults, convertedAudio.getDurationSeconds());

        } finally {
            // 변환된 오디오 파일 정리
            if (convertedAudio != null) {
                audioProcessingService.cleanup(convertedAudio);
            }
        }
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
                log.info("    • 윈도우 {} 분석 중 ({} - {} sec)",
                        window.getWindowIndex(),
                        window.getStartTime(),
                        window.getStartTime() + window.getDuration());

                WindowDto result = analyzeSingleWindow(window);
                results.add(result);

                log.info("    • 윈도우 {} 분석 완료: spm={}, fillers={}",
                        window.getWindowIndex(), result.getSpm(), result.getFillers().size());

            } catch (Exception e) {
                // 실패: 해당 윈도우만 건너뛰고 실패 정보 기록

                // 백엔드 로그: 개발자용 상세 정보
                log.error("❌ 윈도우 {} 분석 실패 - 예외 타입: {}, 메시지: {}",
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
        log.debug("      - STT 결과: {}", text);

        // 2. 필러워드 카운트
        Map<String, Integer> fillerCounts = fillerService.countFillersByRegex(text);
        log.debug("      - 필러 카운트: {}", fillerCounts);

        // 3. 한글 음절 개수 세기
        int syllableCount = speechSpeedService.countKoreanSyllables(text);

        // 4. SPM 계산
        int spm = speechSpeedService.calculateSpm(syllableCount, window.getDuration());
        log.debug("      - SPM: {} (음절: {})", spm, syllableCount);

        // 5. SPM 점수 매핑
        int spmScore = speechSpeedService.mapSpmToScore(spm);
        log.debug("      - SPM 점수: {}", spmScore);

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
     */
    public static class AnalysisResult {
        private final List<WindowDto> windows;
        private final double totalDurationSeconds;

        public AnalysisResult(List<WindowDto> windows, double totalDurationSeconds) {
            this.windows = windows;
            this.totalDurationSeconds = totalDurationSeconds;
        }

        public List<WindowDto> getWindows() {
            return windows;
        }

        public double getTotalDurationSeconds() {
            return totalDurationSeconds;
        }
    }
}

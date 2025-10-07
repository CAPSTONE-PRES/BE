package com.pres.pres_server.controller;

import com.pres.pres_server.dto.WindowDto;
import com.pres.pres_server.service.WhisperService;
import com.pres.pres_server.service.analyse.AudioProcessingService;
import com.pres.pres_server.service.analyse.AudioProcessingService.AudioFile;
import com.pres.pres_server.service.analyse.AudioProcessingService.AudioWindow;
import com.pres.pres_server.service.analyse.FillerService;
import com.pres.pres_server.service.analyse.SpeechSpeedService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173", // 허용할 출처
        allowedHeaders = "*")
public class AnalyseController {
    private static final Logger log = LoggerFactory.getLogger(AnalyseController.class);
    private static final double WINDOW_SEC = 30.0; // 30초 윈도우

    private final WhisperService whisper;
    private final FillerService filler;
    private final SpeechSpeedService speedService;
    private final AudioProcessingService audioProcessingService;

    public AnalyseController(
            WhisperService whisper,
            FillerService filler,
            SpeechSpeedService speedService,
            AudioProcessingService audioProcessingService) {
        this.whisper = whisper;
        this.filler = filler;
        this.speedService = speedService;
        this.audioProcessingService = audioProcessingService;
    }

    @PostMapping(value = "/analyse", consumes = "multipart/form-data")
    public ResponseEntity<List<WindowDto>> analyse(@RequestPart("audio") MultipartFile audioFile) {
        List<WindowDto> windows = new ArrayList<>();
        AudioFile convertedAudio = null;

        try {
            log.info("▶ Received upload: originalName='{}', size={} bytes",
                    audioFile.getOriginalFilename(), audioFile.getSize());

            // 1) 오디오 파일 변환 (16kHz mono WAV)
            convertedAudio = audioProcessingService.convertToWav(audioFile);

            // 2) 30초 윈도우로 분할
            List<AudioWindow> audioWindows = audioProcessingService.splitIntoWindows(convertedAudio, WINDOW_SEC);
            log.info("  • Splitting into {} windows ({} sec each)", audioWindows.size(), WINDOW_SEC);

            // 3) 각 윈도우 분석
            for (AudioWindow window : audioWindows) {
                try {
                    log.info("    • Analyzing window {} ({} - {} sec)",
                            window.getWindowIndex(),
                            window.getStartTime(),
                            window.getStartTime() + window.getDuration());

                    // Whisper → text 추출
                    String text = whisper.transcribe(window.getFile());
                    log.info("    • Whisper result (window {}): {}", window.getWindowIndex(), text);

                    // Filler count
                    Map<String, Integer> counts = filler.countFillersByRegex(text);
                    log.info("    • Filler counts (window {}): {}", window.getWindowIndex(), counts);

                    // 한글 음절 개수 세기
                    int syllableCount = speedService.countKoreanSyllables(text);

                    // SPM 계산 (윈도우 길이 사용)
                    int spm = speedService.calculateSpm(syllableCount, window.getDuration());
                    log.info("    • Window {}: syllables={}, spm={}", window.getWindowIndex(), syllableCount, spm);

                    // SPM 점수 매핑
                    int spmScore = speedService.mapSpmToScore(spm);
                    log.info("    • Window {}: spmScore={}", window.getWindowIndex(), spmScore);

                    // 결과 DTO에 담아서 리스트에 추가
                    windows.add(new WindowDto(
                            window.getStartTime(),
                            window.getStartTime() + window.getDuration(),
                            text,
                            counts,
                            spm,
                            spmScore));

                } finally {
                    // 윈도우 임시 파일 정리
                    window.cleanup();
                }
            }

            log.info("✅ analyse complete, returning {} windows", windows.size());
            return ResponseEntity.ok(windows);

        } catch (Exception e) {
            log.error("❌ analyse failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ArrayList<>());

        } finally {
            // 변환된 오디오 파일 정리
            if (convertedAudio != null) {
                audioProcessingService.cleanup(convertedAudio);
            }
        }
    }
}

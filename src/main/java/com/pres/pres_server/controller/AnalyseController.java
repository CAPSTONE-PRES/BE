package com.pres.pres_server.controller;

import com.pres.pres_server.dto.WindowDto;
import com.pres.pres_server.service.WhisperService;
import com.pres.pres_server.service.analyse.FillerService;
import com.pres.pres_server.service.analyse.SpeechSpeedService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.sound.sampled.*;
import java.io.File;
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

    public AnalyseController(
            WhisperService whisper,
            FillerService filler,
            SpeechSpeedService speedService) {
        this.whisper = whisper;
        this.filler = filler;
        this.speedService = speedService;
    }

    @PostMapping(value = "/analyse", consumes = "multipart/form-data")
    public ResponseEntity<List<WindowDto>> analyse(@RequestPart("audio") MultipartFile audioFile) {
        List<WindowDto> windows = new ArrayList<>();
        try {
            log.info("▶ Received upload: originalName='{}', size={} bytes",
                    audioFile.getOriginalFilename(), audioFile.getSize());

            // 1) 임시 입력 파일 저장
            String orig = audioFile.getOriginalFilename();
            String ext = (orig != null && orig.contains("."))
                    ? orig.substring(orig.lastIndexOf("."))
                    : ".wav";
            File tempInput = File.createTempFile("in_", ext);
            audioFile.transferTo(tempInput);
            log.info("  • Saved temp input: {}", tempInput.getAbsolutePath());

            // 2) ffmpeg 변환: 16kHz mono WAV
            File tempWav = File.createTempFile("wavout_", ".wav");
            String ffmpeg = "C:\\Program Files\\ffmpeg-7.1.1-essentials_build\\bin\\ffmpeg.exe";
            String cmd = String.format(
                    "%s -y -i %s -ar 16000 -ac 1 %s",
                    ffmpeg, tempInput.getAbsolutePath(), tempWav.getAbsolutePath());
            log.info("  • Running ffmpeg convert: {}", cmd);
            Process p1 = Runtime.getRuntime().exec(cmd);
            int c1 = p1.waitFor();
            if (c1 != 0) {
                log.warn("  • ffmpeg conversion exited with code {}", c1);
            } else {
                log.info("  • Converted to WAV: {}", tempWav.getAbsolutePath());
            }
            tempInput.delete();

            // 3) 전체 길이(sec) 계산
            AudioInputStream ais = AudioSystem.getAudioInputStream(tempWav);
            AudioFormat fmt = ais.getFormat();
            double totalSec = ais.getFrameLength() / fmt.getFrameRate();
            ais.close();
            log.info("  • Total audio duration: {:.2f} sec", totalSec);

            // 4) 30초 윈도우 분할 → Whisper → filler 카운트 → spm 계산
            int numWins = (int) Math.ceil(totalSec / WINDOW_SEC);
            log.info("  • Splitting into {} windows ({} sec each)", numWins, WINDOW_SEC);

            for (int i = 0; i < numWins; i++) {
                double start = i * WINDOW_SEC;
                double dur = Math.min(WINDOW_SEC, totalSec - start);
                if (dur <= 0)
                    break;

                // 윈도우별로 자를 임시 파일 생성
                File winFile = File.createTempFile("win_" + i + "_", ".wav");
                String cutCmd = String.format(
                        "%s -y -ss %.2f -i %s -t %.2f %s",
                        ffmpeg, start, tempWav.getAbsolutePath(),
                        dur, winFile.getAbsolutePath());
                log.info("    • ffmpeg split window {}: {}", i, cutCmd);
                Process p2 = Runtime.getRuntime().exec(cutCmd);
                int c2 = p2.waitFor();
                log.info("    • split exit code: {}", c2);

                // 5) Whisper → text 얻기
                log.info("    • calling Whisper for window {} ({} - {} sec)", i, start, start + dur);
                String text = whisper.transcribe(winFile);
                log.info("    • Whisper result (window {}): {}", i, text);

                // 6) Filler count (기존 방식 그대로)
                Map<String, Integer> counts = filler.countFillersByRegex(text);
                log.info("    • Filler counts (window {}): {}", i, counts);

                // 7) “한글 음절” 개수 세기
                int syllableCount = speedService.countKoreanSyllables(text);

                // 8) SPM 계산 (윈도우 길이를 사용)
                int spm = speedService.calculateSpm(syllableCount, dur);
                log.info("    • Window {}: syllables={}, spm={}", i, syllableCount, spm);

                // 9) SPM 점수 매핑
                int spmScore = speedService.mapSpmToScore(spm);
                log.info("    • Window {}: spmScore={}", i, spmScore);

                // 10) 결과 DTO에 담아서 리스트에 추가
                windows.add(new WindowDto(
                        start,
                        start + dur,
                        text,
                        counts,
                        spm,
                        spmScore));

                // 임시 윈도우 파일 삭제
                winFile.delete();
            }

            // 11) 최종
            tempWav.delete();
            log.info("✅ analyse complete, returning {} windows", windows.size());
            return ResponseEntity.ok(windows);

        } catch (Exception e) {
            log.error("❌ analyse failed", e);
            // 예외 발생 시 500과 함께 상세 메시지를 던집니다.
            e.printStackTrace(); // ← 콘솔 오류 출력

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ArrayList<>()); // 또는 Collections.emptyList()
        }
    }
}

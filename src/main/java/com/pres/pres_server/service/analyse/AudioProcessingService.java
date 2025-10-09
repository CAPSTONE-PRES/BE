package com.pres.pres_server.service.analyse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class AudioProcessingService {
    private static final Logger log = LoggerFactory.getLogger(AudioProcessingService.class);

    @Value("${ffmpeg.path:C:\\Program Files\\ffmpeg-7.1.1-essentials_build\\bin\\ffmpeg.exe}")
    private String ffmpegPath;

    // Audio 파일 정보를 담는 내부 클래스
    public static class AudioFile {
        private final File file;
        private final double durationSeconds;

        public AudioFile(File file, double durationSeconds) {
            this.file = file;
            this.durationSeconds = durationSeconds;
        }

        public File getFile() {
            return file;
        }

        public double getDurationSeconds() {
            return durationSeconds;
        }
    }

    // Audio 윈도우 정보를 담는 내부 클래스
    public static class AudioWindow {
        private final File file;
        private final double startTime;
        private final double duration;
        private final int windowIndex;

        public AudioWindow(File file, double startTime, double duration, int windowIndex) {
            this.file = file;
            this.startTime = startTime;
            this.duration = duration;
            this.windowIndex = windowIndex;
        }

        public File getFile() {
            return file;
        }

        public double getStartTime() {
            return startTime;
        }

        public double getDuration() {
            return duration;
        }

        public int getWindowIndex() {
            return windowIndex;
        }

        // audio window 내부 임시 파일 정리
        public void cleanup() {
            if (file != null && file.exists()) {
                boolean deleted = file.delete();
                if (!deleted) {
                    log.warn("임시 파일 삭제 실패: {}", file.getAbsolutePath());
                }
            }
        }
    }

    /**
     * 저장된 파일 경로를 사용하여 16kHz mono WAV로 변환하고 메타데이터 추출
     * 
     * @param filePath 저장된 오디오 파일 경로
     * @return 변환된 오디오 파일 정보
     * @throws IOException          파일 처리 실패 시
     * @throws InterruptedException ffmpeg 프로세스 중단 시
     */
    public AudioFile convertToWav(String filePath) throws IOException, InterruptedException {
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("파일 경로가 비어있습니다.");
        }

        File inputFile = new File(filePath);
        if (!inputFile.exists()) {
            throw new IOException("파일이 존재하지 않습니다: " + filePath);
        }

        log.info("▶ 오디오 변환 시작: filePath='{}', size={} bytes",
                filePath, inputFile.length());

        try {
            // ffmpeg로 16kHz mono WAV 변환
            File wavFile = convertWithFfmpeg(inputFile);
            log.info("  • WAV 변환 완료: {}", wavFile.getAbsolutePath());

            // 오디오 길이 추출
            double duration = extractDuration(wavFile);
            log.info("  • 오디오 길이: {:.2f}초", duration);

            return new AudioFile(wavFile, duration);

        } catch (Exception e) {
            log.error("오디오 변환 실패: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * MultipartFile을 16kHz mono WAV로 변환하고 메타데이터 추출
     * 
     * @param multipartFile 업로드된 오디오 파일
     * @return 변환된 오디오 파일 정보
     * @throws IOException          파일 처리 실패 시
     * @throws InterruptedException ffmpeg 프로세스 중단 시
     */
    public AudioFile convertToWav(MultipartFile multipartFile) throws IOException, InterruptedException {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("오디오 파일이 비어있습니다.");
        }

        log.info("▶ 오디오 변환 시작: originalName='{}', size={} bytes",
                multipartFile.getOriginalFilename(), multipartFile.getSize());

        // 1) 임시 입력 파일 저장
        File tempInput = createTempFile(multipartFile);
        log.info("  • 임시 입력 파일 저장: {}", tempInput.getAbsolutePath());

        try {
            // 2) ffmpeg로 16kHz mono WAV 변환
            File wavFile = convertWithFfmpeg(tempInput);
            log.info("  • WAV 변환 완료: {}", wavFile.getAbsolutePath());

            // 3) 오디오 길이 추출
            double duration = extractDuration(wavFile);
            log.info("  • 오디오 길이: {:.2f}초", duration);

            return new AudioFile(wavFile, duration);

        } finally {
            // 입력 임시 파일 정리
            tempInput.delete();
        }
    }

    /**
     * 오디오 파일을 지정된 길이의 윈도우로 분할
     * 
     * @param audioFile     분할할 오디오 파일
     * @param windowSeconds 윈도우 길이 (초)
     * @return 분할된 오디오 윈도우 리스트
     * @throws IOException          파일 처리 실패 시
     * @throws InterruptedException ffmpeg 프로세스 중단 시
     */
    public List<AudioWindow> splitIntoWindows(AudioFile audioFile, double windowSeconds)
            throws IOException, InterruptedException {

        if (audioFile == null || audioFile.getFile() == null) {
            throw new IllegalArgumentException("오디오 파일이 null입니다.");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("윈도우 길이는 0보다 커야 합니다.");
        }

        int numWindows = (int) Math.ceil(audioFile.getDurationSeconds() / windowSeconds);
        log.info("  • 윈도우 분할 시작: {} 개의 윈도우 ({} 초 단위)", numWindows, windowSeconds);

        List<AudioWindow> windows = new ArrayList<>();

        for (int i = 0; i < numWindows; i++) {
            double startTime = i * windowSeconds;
            double duration = Math.min(windowSeconds, audioFile.getDurationSeconds() - startTime);

            if (duration <= 0) {
                break;
            }

            AudioWindow window = splitWindow(audioFile.getFile(), startTime, duration, i);
            windows.add(window);

            log.info("    • 윈도우 {} 생성: {:.2f}초 ~ {:.2f}초",
                    i, startTime, startTime + duration);
        }

        log.info("  • 윈도우 분할 완료: {} 개", windows.size());
        return windows;
    }

    /**
     * 파라미터로 받은 오디오 파일 정리 (임시 파일 삭제)
     * 
     * @param audioFile 정리할 오디오 파일
     */
    public void cleanup(AudioFile audioFile) {
        if (audioFile != null && audioFile.getFile() != null && audioFile.getFile().exists()) {
            boolean deleted = audioFile.getFile().delete();
            if (!deleted) {
                log.warn("임시 파일 삭제 실패: {}", audioFile.getFile().getAbsolutePath());
            } else {
                log.debug("임시 파일 삭제 완료: {}", audioFile.getFile().getAbsolutePath());
            }
        }
    }

    // MultipartFile에서 임시 파일 생성
    private File createTempFile(MultipartFile multipartFile) throws IOException {
        String originalFilename = multipartFile.getOriginalFilename();
        String extension = extractExtension(originalFilename);

        File tempFile = File.createTempFile("audio_input_", extension);
        multipartFile.transferTo(tempFile);

        return tempFile;
    }

    // 파일명에서 확장자 추출
    private String extractExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf("."));
        }
        return ".wav"; // 기본값
    }

    // ffmpeg를 사용하여 16kHz mono WAV로 변환
    private File convertWithFfmpeg(File inputFile) throws IOException, InterruptedException {
        File outputFile = File.createTempFile("audio_converted_", ".wav");

        String command = String.format(
                "%s -y -i %s -ar 16000 -ac 1 %s",
                ffmpegPath,
                inputFile.getAbsolutePath(),
                outputFile.getAbsolutePath());

        log.debug("  • ffmpeg 명령: {}", command);

        Process process = Runtime.getRuntime().exec(command);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            log.warn("  • ffmpeg 변환 경고: exit code {}", exitCode);
        }

        return outputFile;
    }

    // WAV 파일에서 오디오 길이 추출
    private double extractDuration(File wavFile) throws IOException {
        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(wavFile)) {
            AudioFormat format = audioInputStream.getFormat();
            long frameLength = audioInputStream.getFrameLength();
            float frameRate = format.getFrameRate();

            return frameLength / frameRate;
        } catch (Exception e) {
            throw new IOException("오디오 길이 추출 실패: " + e.getMessage(), e);
        }
    }

    // 단일 윈도우 분할
    private AudioWindow splitWindow(File sourceFile, double startTime, double duration, int index)
            throws IOException, InterruptedException {

        File windowFile = File.createTempFile("audio_window_" + index + "_", ".wav");

        String command = String.format(
                "%s -y -ss %.2f -i %s -t %.2f %s",
                ffmpegPath,
                startTime,
                sourceFile.getAbsolutePath(),
                duration,
                windowFile.getAbsolutePath());

        log.debug("    • ffmpeg 윈도우 분할 명령: {}", command);

        Process process = Runtime.getRuntime().exec(command);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            log.warn("    • ffmpeg 분할 경고: exit code {}", exitCode);
        }

        return new AudioWindow(windowFile, startTime, duration, index);
    }
}

package com.pres.pres_server.service.analyse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class AudioProcessingService {
    private static final Logger log = LoggerFactory.getLogger(AudioProcessingService.class);

    // Constants
    private static final int SAMPLE_RATE = 16000; // 16kHz
    private static final int CHANNELS = 1; // mono
    private static final String WAV_EXTENSION = ".wav";
    private static final long FFMPEG_TIMEOUT_SECONDS = 60; // ffmpeg 타임아웃
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
            log.info("  • 오디오 길이: {}초", String.format("%.2f", duration));

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
            log.info("  • 오디오 길이: {}초", String.format("%.2f", duration));

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

            log.info("    • 윈도우 {} 생성: {}초 ~ {}초",
                    i, String.format("%.2f", startTime), String.format("%.2f", startTime + duration));
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
        File outputFile = File.createTempFile("audio_converted_", WAV_EXTENSION);

        try {
            // ProcessBuilder 사용 (deprecated 메서드 대체)
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-y",
                    "-i", inputFile.getAbsolutePath(),
                    "-ar", String.valueOf(SAMPLE_RATE),
                    "-ac", String.valueOf(CHANNELS),
                    outputFile.getAbsolutePath());

            processBuilder.redirectErrorStream(true); // stderr를 stdout으로 병합

            log.debug("  • ffmpeg 명령: {}", String.join(" ", processBuilder.command()));

            Process process = processBuilder.start();

            // 출력 로그 수집 (선택적 - 디버깅용)
            captureProcessOutput(process);

            // 타임아웃 적용
            boolean finished = process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                throw new IOException("ffmpeg 프로세스 타임아웃 (" + FFMPEG_TIMEOUT_SECONDS + "초 초과)");
            }

            // exitCode 검증 (0이 아니면 변환 실패)
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                throw new IOException("ffmpeg 변환 실패: exit code " + exitCode +
                        " (입력 파일이 손상되었거나 지원하지 않는 형식일 수 있습니다)");
            }

            // 출력 파일 존재 및 크기 검증
            if (!outputFile.exists()) {
                throw new IOException("ffmpeg 변환 완료되었으나 출력 파일이 생성되지 않았습니다");
            }

            if (outputFile.length() == 0) {
                outputFile.delete();
                throw new IOException("ffmpeg 변환 완료되었으나 출력 파일이 비어있습니다 (입력 파일 확인 필요)");
            }

            log.debug("  • ffmpeg 변환 성공: size={} bytes", outputFile.length());
            return outputFile;

        } catch (Exception e) {
            // 실패 시 임시 파일 정리
            if (outputFile != null && outputFile.exists()) {
                boolean deleted = outputFile.delete();
                if (!deleted) {
                    log.warn("  • 임시 파일 정리 실패: {}", outputFile.getAbsolutePath());
                }
            }
            throw e;
        }
    }

    // WAV 파일에서 오디오 길이 추출
    private double extractDuration(File wavFile) throws IOException {
        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(wavFile)) {
            AudioFormat format = audioInputStream.getFormat();
            long frameLength = audioInputStream.getFrameLength();
            float frameRate = format.getFrameRate();

            return frameLength / frameRate;
        } catch (Exception e) {
            log.warn("AudioSystem으로 길이 추출 실패, ffprobe로 폴백 시도: {}", e.getMessage());

            // ffprobe 폴백 시도
            try {
                String ffprobePath = ffmpegPath.replace("ffmpeg.exe", "ffprobe.exe");
                ProcessBuilder pb = new ProcessBuilder(
                        ffprobePath,
                        "-v", "error",
                        "-show_entries", "format=duration",
                        "-of", "default=noprint_wrappers=1:nokey=1",
                        wavFile.getAbsolutePath());
                pb.redirectErrorStream(true);
                Process p = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line = reader.readLine();
                    boolean finished = p.waitFor(10, TimeUnit.SECONDS);
                    if (!finished) {
                        p.destroyForcibly();
                        throw new IOException("ffprobe 타임아웃");
                    }

                    int exit = p.exitValue();
                    if (exit != 0 || line == null) {
                        throw new IOException("ffprobe 실행 실패 or 출력 없음 (exit=" + exit + ")");
                    }

                    double duration = Double.parseDouble(line.trim());
                    return duration;
                }
            } catch (Exception ex) {
                log.error("ffprobe 폴백 실패: {}", ex.getMessage());
                throw new IOException("오디오 길이 추출 실패 (지원되지 않는 WAV 형식 또는 내부 처리 오류). " +
                        "PCM 16kHz mono WAV로 변환 후 재시도해 주세요. 내부 오류: " + ex.getMessage(), ex);
            }
        }
    }

    // 단일 윈도우 분할
    private AudioWindow splitWindow(File sourceFile, double startTime, double duration, int index)
            throws IOException, InterruptedException {

        File windowFile = File.createTempFile("audio_window_" + index + "_", WAV_EXTENSION);

        try {
            // ProcessBuilder 사용 (deprecated 메서드 대체)
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-y",
                    "-ss", String.format("%.2f", startTime),
                    "-i", sourceFile.getAbsolutePath(),
                    "-t", String.format("%.2f", duration),
                    windowFile.getAbsolutePath());

            processBuilder.redirectErrorStream(true);

            log.debug("    • ffmpeg 윈도우 분할 명령: {}", String.join(" ", processBuilder.command()));

            Process process = processBuilder.start();

            // 타임아웃 적용
            boolean finished = process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                if (windowFile.exists()) {
                    windowFile.delete();
                }
                throw new IOException("ffmpeg 윈도우 분할 타임아웃 (윈도우 " + index + ")");
            }

            // exitCode 검증 (0이 아니면 분할 실패)
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                if (windowFile.exists()) {
                    windowFile.delete();
                }
                throw new IOException("ffmpeg 윈도우 분할 실패: exit code " + exitCode +
                        " (윈도우 " + index + ", 시작: " + startTime + "초)");
            }

            // 출력 파일 존재 및 크기 검증
            if (!windowFile.exists()) {
                throw new IOException("ffmpeg 분할 완료되었으나 윈도우 파일이 생성되지 않았습니다 (윈도우 " + index + ")");
            }

            if (windowFile.length() == 0) {
                windowFile.delete();
                throw new IOException("ffmpeg 분할 완료되었으나 윈도우 파일이 비어있습니다 (윈도우 " + index +
                        ", 시작: " + startTime + "초)");
            }

            log.debug("    • 윈도우 {} 분할 성공: size={} bytes", index, windowFile.length());
            return new AudioWindow(windowFile, startTime, duration, index);

        } catch (Exception e) {
            // 실패 시 임시 파일 정리
            if (windowFile != null && windowFile.exists()) {
                boolean deleted = windowFile.delete();
                if (!deleted) {
                    log.warn("    • 윈도우 임시 파일 정리 실패: {}", windowFile.getAbsolutePath());
                }
            }
            throw e;
        }
    }

    /**
     * 프로세스 출력 로그 수집 (디버깅용)
     * 별도 스레드에서 실행하여 메인 스레드 블로킹 방지
     */
    private void captureProcessOutput(Process process) {
        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("ffmpeg: {}", line);
                }
            } catch (IOException e) {
                // 프로세스가 강제 종료되면 정상적으로 발생할 수 있음
                log.trace("ffmpeg 출력 읽기 종료: {}", e.getMessage());
            }
        });

        outputThread.setName("ffmpeg-output-capture");
        outputThread.setDaemon(true); // 메인 스레드 종료 시 자동 종료
        outputThread.start();
    }
}

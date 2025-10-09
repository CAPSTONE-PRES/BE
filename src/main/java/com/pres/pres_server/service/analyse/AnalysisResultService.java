package com.pres.pres_server.service.analyse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pres.pres_server.domain.Feedback;
import com.pres.pres_server.domain.PracticeSession;
import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.SessionWindow;
import com.pres.pres_server.dto.analyse.WindowDto;
import com.pres.pres_server.repository.FeedbackRepository;
import com.pres.pres_server.repository.PracticeSessionRepository;
import com.pres.pres_server.repository.ProjectRepository;
import com.pres.pres_server.repository.SessionWindowRepository;
import com.pres.pres_server.service.WhisperService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 분석 결과 저장 서비스
 */
@Service
@RequiredArgsConstructor
public class AnalysisResultService {
    private static final Logger log = LoggerFactory.getLogger(AnalysisResultService.class);

    private final PracticeSessionRepository sessionRepository;
    private final SessionWindowRepository windowRepository;
    private final FeedbackRepository feedbackRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;
    private final WhisperService whisperService;
    private final SilenceDetectionService silenceDetectionService;
    private final AudioProcessingService audioProcessingService;

    /**
     * 분석 결과를 DB에 저장 (신규 세션 생성)
     * 
     * @param projectId     프로젝트 ID
     * @param windows       윈도우별 분석 결과 리스트
     * @param totalDuration 전체 오디오 길이 (초)
     * @return 저장된 PracticeSession ID
     */
    @Transactional
    public Long saveAnalysisResult(Long projectId, List<WindowDto> windows, double totalDuration) {
        log.info("▶ 분석 결과 저장 시작 - projectId: {}, windows: {}", projectId, windows.size());

        // 1. PracticeSession 생성 및 저장
        PracticeSession session = createPracticeSession(projectId, windows, totalDuration);
        session = sessionRepository.save(session);
        log.info("  • PracticeSession 저장 완료 - sessionId: {}", session.getSessionId());

        // 2. SessionWindow 리스트 생성 및 저장
        saveSessionWindows(session, windows);
        log.info("  • SessionWindow {} 개 저장 완료", windows.size());

        // 3. Feedback 계산 및 저장
        Feedback feedback = calculateAndSaveFeedback(session, windows);
        log.info("  • Feedback 저장 완료 - totalScore: {}, grade: {}",
                feedback.getTotalScore(), feedback.getGrade());

        log.info("✅ 분석 결과 저장 완료 - sessionId: {}", session.getSessionId());
        return session.getSessionId();
    }

    /**
     * 분석 결과를 DB에 저장 (기존 세션 업데이트)
     * 
     * @param session        기존 PracticeSession 엔티티
     * @param analysisResult 분석 결과
     */
    @Transactional
    public void saveAnalysisResult(PracticeSession session, AudioAnalysisService.AnalysisResult analysisResult) {
        log.info("▶ 분석 결과 업데이트 시작 - sessionId: {}, windows: {}",
                session.getSessionId(), analysisResult.getWindows().size());

        // 1. STT 텍스트 및 Duration 업데이트
        String fullText = analysisResult.getWindows().stream()
                .map(WindowDto::getTranscript)
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        session.updateSttText(fullText);
        session.updateDuration(analysisResult.getTotalDurationSeconds()); // 소수점 그대로
        sessionRepository.save(session);
        log.info("  • PracticeSession STT 및 Duration 업데이트 완료 - sessionId: {}, duration: {:.2f}초",
                session.getSessionId(), analysisResult.getTotalDurationSeconds());

        // 2. SessionWindow 리스트 생성 및 저장
        saveSessionWindows(session, analysisResult.getWindows());
        log.info("  • SessionWindow {} 개 저장 완료", analysisResult.getWindows().size());

        // 3. 공백 감지 (2.5초 이상)
        SilenceDetectionService.SilenceStatistics silenceStats = detectSilences(session.getAudioUrl());
        if (silenceStats.isSuccess()) {
            log.info("  • 공백 감지 완료 - count: {}, totalDuration: {}초",
                    silenceStats.getSilenceCount(), silenceStats.getTotalSilenceDuration());
        } else {
            log.warn("  • 공백 감지 실패 - 기본값 적용 (count=0, score=100)");
        }

        // 4. Feedback 계산 및 저장 (공백 정보 포함)
        Feedback feedback = calculateAndSaveFeedback(session, analysisResult.getWindows(), silenceStats);
        log.info("  • Feedback 저장 완료 - totalScore: {}, grade: {}, silenceScore: {}",
                feedback.getTotalScore(), feedback.getGrade(), feedback.getSilenceScore());

        log.info("✅ 분석 결과 업데이트 완료 - sessionId: {}", session.getSessionId());
    }

    /**
     * PracticeSession 엔티티 생성
     */
    private PracticeSession createPracticeSession(Long projectId, List<WindowDto> windows, double totalDuration) {
        // Project 조회
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다. projectId: " + projectId));

        // 전체 STT 텍스트 합치기
        String fullText = windows.stream()
                .map(WindowDto::getTranscript)
                .reduce((a, b) -> a + " " + b)
                .orElse("");

        // Builder 패턴으로 생성
        return PracticeSession.builder()
                .project(project)
                .sttText(fullText)
                .practicedAt(LocalDateTime.now())
                .durationSeconds(totalDuration) // 소수점 그대로 저장
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

            // 필러 카운트를 JSON으로 변환
            try {
                String fillerJson = objectMapper.writeValueAsString(windowDto.getFillers());
                window.setFillerCounts(fillerJson);
            } catch (Exception e) {
                log.warn("필러 카운트 JSON 변환 실패 - window {}", index, e);
                window.setFillerCounts("{}");
            }

            windowRepository.save(window);
            index++;
        }
    }

    /**
     * Feedback 계산 및 저장
     * TODO: 점수 계산 로직 개선 필요
     */
    private Feedback calculateAndSaveFeedback(PracticeSession session, List<WindowDto> windows) {
        return calculateAndSaveFeedback(session, windows, null);
    }

    /**
     * Feedback 계산 및 저장 (공백 정보 포함)
     */
    private Feedback calculateAndSaveFeedback(PracticeSession session, List<WindowDto> windows,
            SilenceDetectionService.SilenceStatistics silenceStats) {
        Feedback feedback = new Feedback();
        feedback.setPracticeSessionId(session);

        // 성공한 윈도우만 필터링
        List<WindowDto> successWindows = windows.stream()
                .filter(w -> "SUCCESS".equals(w.getStatus()))
                .toList();

        if (successWindows.isEmpty()) {
            // 모든 윈도우가 실패한 경우 기본값
            log.warn("모든 윈도우 분석 실패 - 기본 피드백 저장");
            feedback.setSpmScore(0);
            feedback.setFillerScore(0);
            feedback.setRepeatScore(0);
            feedback.setTotalScore(0);
            feedback.setGrade("F");

            // 공백 정보 (있으면 설정)
            if (silenceStats != null) {
                feedback.setSilenceCount(silenceStats.getSilenceCount());
                feedback.setTotalSilenceDuration(silenceStats.getTotalSilenceDuration());
                feedback.setSilenceScore(0);
                feedback.setSilenceAnalysisSuccess(silenceStats.isSuccess());
            }

            return feedbackRepository.save(feedback);
        }

        // SPM 점수 평균 계산 (성공한 윈도우만)
        double avgSpmScore = successWindows.stream()
                .mapToInt(WindowDto::getSpmScore)
                .average()
                .orElse(0.0);
        feedback.setSpmScore((int) Math.round(avgSpmScore));

        // 필러 점수 계산 (성공한 윈도우만)
        int totalFillers = successWindows.stream()
                .mapToInt(w -> w.getFillers().values().stream()
                        .mapToInt(Integer::intValue)
                        .sum())
                .sum();
        // 간단한 공식: 100 - (필러개수 * 2), 최소 0점
        int fillerScore = Math.max(0, 100 - (totalFillers * 2));
        feedback.setFillerScore(fillerScore);

        // Repeat 점수 (현재 분석 안 함 - 임시로 0)
        feedback.setRepeatScore(0);

        // 공백 점수 계산 (2.5초 이상 공백 기준)
        int silenceScore = 100;
        if (silenceStats != null) {
            feedback.setSilenceCount(silenceStats.getSilenceCount());
            feedback.setTotalSilenceDuration(silenceStats.getTotalSilenceDuration());
            feedback.setSilenceAnalysisSuccess(silenceStats.isSuccess());

            // 성공한 경우만 점수 계산
            if (silenceStats.isSuccess()) {
                silenceScore = Math.max(0, 100 - (silenceStats.getSilenceCount() * 10));
            } else {
                // 실패한 경우 만점 처리 (불이익 없음)
                silenceScore = 100;
            }
            feedback.setSilenceScore(silenceScore);
        } else {
            feedback.setSilenceCount(0);
            feedback.setTotalSilenceDuration(0.0);
            feedback.setSilenceScore(100);
            feedback.setSilenceAnalysisSuccess(false);
        }

        // 총점 계산 (SPM 40% + Filler 30% + Silence 30%)
        int totalScore = (int) Math.round(avgSpmScore * 0.4 + fillerScore * 0.3 + silenceScore * 0.3);
        feedback.setTotalScore(totalScore);

        // 등급 계산
        String grade = calculateGrade(totalScore);
        feedback.setGrade(grade);

        return feedbackRepository.save(feedback);
    }

    /**
     * 오디오 파일에서 공백 감지
     */
    private SilenceDetectionService.SilenceStatistics detectSilences(String audioUrl) {
        if (audioUrl == null || audioUrl.isEmpty()) {
            log.warn("audioUrl이 없어 공백 감지 생략");
            return createEmptyStatistics();
        }

        try {
            // 1. 오디오 파일 경로 추출
            String filePath = convertUrlToFilePath(audioUrl);

            // 파일 존재 여부 확인
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) {
                log.warn("오디오 파일이 존재하지 않음: {}", filePath);
                return createEmptyStatistics();
            }

            // 2. WAV 변환
            AudioProcessingService.AudioFile audioFile = audioProcessingService.convertToWav(filePath);
            if (audioFile == null || audioFile.getFile() == null) {
                log.warn("WAV 변환 결과가 null");
                return createEmptyStatistics();
            }

            // 3. Whisper API 호출 (timestamp 포함)
            WhisperService.TranscriptionResult result = whisperService.transcribeWithTimestamps(audioFile.getFile(),
                    true);

            // 4. segments 검증
            if (result == null || result.getSegments() == null || result.getSegments().isEmpty()) {
                log.warn("Whisper segments가 비어있어 공백 감지 불가");
                return createEmptyStatistics();
            }

            // 5. 공백 감지
            List<SilenceDetectionService.SilenceInterval> silences = silenceDetectionService
                    .detectSilences(result.getSegments());

            // 6. 통계 계산
            return silenceDetectionService.calculateStatistics(silences);

        } catch (java.io.IOException e) {
            log.error("파일 처리 중 오류 발생: {}", e.getMessage());
            return createEmptyStatistics();
        } catch (InterruptedException e) {
            log.error("오디오 변환 중단됨: {}", e.getMessage());
            Thread.currentThread().interrupt(); // Interrupted 상태 복원
            return createEmptyStatistics();
        } catch (IllegalArgumentException e) {
            log.error("잘못된 파라미터: {}", e.getMessage());
            return createEmptyStatistics();
        } catch (Exception e) {
            log.error("공백 감지 중 예상치 못한 오류: {}", e.getMessage(), e);
            return createEmptyStatistics();
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
                .success(false) // 실패했음을 명시
                .build();
    }

    /**
     * URL을 파일 경로로 변환
     * TODO: FileUploadService에 실제 구현 필요
     */
    private String convertUrlToFilePath(String audioUrl) {
        // 임시: URL이 파일 경로라고 가정 (실제로는 URL → 파일 시스템 경로 변환 로직 필요)
        // 예: http://localhost:8080/uploads/abc.m4a → /path/to/uploads/abc.m4a
        if (audioUrl.startsWith("http")) {
            // URL에서 파일명 추출
            String filename = audioUrl.substring(audioUrl.lastIndexOf("/") + 1);
            return "uploads/" + filename; // 실제 uploads 디렉토리 경로로 변경 필요
        }
        return audioUrl;
    }

    /**
     * 점수를 기반으로 등급 계산
     * TODO: 등급 기준 개선 필요
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
}

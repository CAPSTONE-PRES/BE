package com.pres.pres_server.service.analyse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pres.pres_server.domain.Feedback;
import com.pres.pres_server.domain.PracticeSession;
import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.SessionWindow;
import com.pres.pres_server.dto.WindowDto;
import com.pres.pres_server.repository.FeedbackRepository;
import com.pres.pres_server.repository.PracticeSessionRepository;
import com.pres.pres_server.repository.SessionWindowRepository;
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
    private final ObjectMapper objectMapper;

    /**
     * 분석 결과를 DB에 저장
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
     * PracticeSession 엔티티 생성
     */
    private PracticeSession createPracticeSession(Long projectId, List<WindowDto> windows, double totalDuration) {
        PracticeSession session = new PracticeSession();

        // Project 설정 (실제로는 ProjectRepository에서 조회해야 하지만 임시로 ID만 설정)
        Project project = new Project();
        project.setProjectId(projectId);
        session.setProjectId(project);

        // 전체 STT 텍스트 합치기
        String fullText = windows.stream()
                .map(WindowDto::getTranscript)
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        session.setSttText(fullText);

        session.setPracticedAt(LocalDateTime.now());
        // duration은 Time 타입이라 일단 null (나중에 수정 필요)
        session.setDuration(null);
        session.setAudioUrl(null); // 오디오 파일 저장 안 함

        return session;
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

        // 총점 계산 (임시: SPM 50% + Filler 50%)
        int totalScore = (int) Math.round(avgSpmScore * 0.5 + fillerScore * 0.5);
        feedback.setTotalScore(totalScore);

        // 등급 계산 (임시)
        String grade = calculateGrade(totalScore);
        feedback.setGrade(grade);

        return feedbackRepository.save(feedback);
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

package com.pres.pres_server.service.practice;

import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.Feedback;
import com.pres.pres_server.domain.PracticeSession;
import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.Project;
import com.pres.pres_server.dto.file.FileInfoDto;
import com.pres.pres_server.dto.practice.PracticeFeedbackDto;
import com.pres.pres_server.dto.practice.PracticeSessionStartDto;
import com.pres.pres_server.repository.CueCardRepository;
import com.pres.pres_server.repository.FeedbackRepository;
import com.pres.pres_server.repository.PracticeSessionRepository;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.ProjectRepository;
import com.pres.pres_server.service.analyse.AnalysisResultService;
import com.pres.pres_server.service.analyse.AudioAnalysisService;
import com.pres.pres_server.service.analyse.SilenceDetectionService;
import com.pres.pres_server.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 연습 세션 관리 서비스
 * 
 * 연습 시작/종료, 슬라이드 및 큐카드 조회를 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeSessionService {

    private final ProjectRepository projectRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PresentationFileRepository presentationFileRepository;
    private final CueCardRepository cueCardRepository;
    private final FeedbackRepository feedbackRepository;

    private final FileUploadService fileUploadService;
    private final AudioAnalysisService audioAnalysisService;
    private final AnalysisResultService analysisResultService;
    private final SilenceDetectionService silenceDetectionService;

    /**
     * 연습 세션 시작
     * 
     * @param projectId 연습할 프로젝트 ID
     * @return 세션 정보 + 슬라이드 + 큐카드
     */
    @Transactional
    public PracticeSessionStartDto startSession(Long projectId) {
        log.info("📝 연습 세션 시작 - projectId: {}", projectId);

        // 1. 프로젝트 존재 여부 확인
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다. projectId: " + projectId));

        // 2. PracticeSession 생성 (started_at만 기록, ended_at은 null)
        PracticeSession session = PracticeSession.builder()
                .project(project)
                .practicedAt(LocalDateTime.now()) // started_at 역할
                .build();

        session = practiceSessionRepository.save(session);
        log.info("✅ PracticeSession 생성 완료 - sessionId: {}", session.getSessionId());

        // 3. PresentationFile 조회 (ExtractedText와 함께 fetch join으로 조회)
        PresentationFile presentationFile = presentationFileRepository.findByProjectWithExtractedText(project)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트에 연결된 발표 파일이 없습니다. projectId: " + projectId));

        // 4. ExtractedText 조회 (실제 슬라이드 텍스트가 저장된 곳)
        if (presentationFile.getExtractedText() == null) {
            throw new IllegalArgumentException("발표 파일의 텍스트가 추출되지 않았습니다. fileId: " + presentationFile.getFileId());
        }

        List<String> slideTexts = presentationFile.getExtractedText().getSlideTexts();
        log.info("📄 PresentationFile 조회 완료 - fileId: {}, slides: {} 개",
                presentationFile.getFileId(),
                slideTexts != null ? slideTexts.size() : 0);

        // 5. CueCard 목록 조회 (파일에 연결된 큐카드들)
        List<CueCard> cueCards = cueCardRepository.findByPresentationFile(presentationFile);
        log.info("📇 CueCard 조회 완료 - {} 개", cueCards.size());

        // 6. slideNumber를 key로 하는 Map 생성 (빠른 조회)
        Map<Integer, CueCard> cueCardMap = cueCards.stream()
                .collect(Collectors.toMap(CueCard::getSlideNumber, card -> card));

        // 7. 슬라이드 정보 생성 (slideTexts 기준으로 순회)
        List<PracticeSessionStartDto.SlideInfo> slides = new ArrayList<>();

        if (slideTexts != null && !slideTexts.isEmpty()) {
            for (int i = 0; i < slideTexts.size(); i++) {
                int pageNumber = i + 1; // 1부터 시작
                CueCard cueCard = cueCardMap.get(pageNumber);

                PracticeSessionStartDto.SlideInfo slideInfo = PracticeSessionStartDto.SlideInfo.builder()
                        .pageNumber(pageNumber)
                        .slideText(slideTexts.get(i))
                        .imageUrl(generateSlideImageUrl(presentationFile, pageNumber)) // TODO: 이미지 URL 생성 방식 결정 필요
                        .cueCard(cueCard != null ? cueCard.getContent() : null)
                        .qrUrl(cueCard != null ? cueCard.getQrUrl() : null)
                        .build();

                slides.add(slideInfo);
            }
        }

        log.info("✅ 슬라이드 정보 생성 완료 - {} 개", slides.size());

        // 8. 응답 DTO 생성
        return PracticeSessionStartDto.builder()
                .sessionId(session.getSessionId())
                .projectId(projectId)
                .slides(slides)
                .build();
    }

    /**
     * 연습 세션 종료 및 오디오 분석
     * 
     * @param sessionId 종료할 세션 ID
     * @param audioFile 녹음된 오디오 파일
     * @return 완료된 세션 ID
     */
    @Transactional
    public Long endSession(Long sessionId, MultipartFile audioFile) throws Exception {
        log.info("🎬 연습 세션 종료 시작 - sessionId: {}, audioFile: {}",
                sessionId, audioFile.getOriginalFilename());

        // 1. 세션 존재 여부 확인
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다. sessionId: " + sessionId));

        // 2. 이미 종료된 세션인지 확인
        if (session.getAudioUrl() != null) {
            throw new IllegalStateException("이미 종료된 세션입니다. sessionId: " + sessionId);
        }

        // 3. 오디오 파일 저장
        FileInfoDto fileInfo = fileUploadService.saveFile(audioFile);
        log.info("💾 오디오 파일 저장 완료 - filePath: {}", fileInfo.getFilePath());

        // 4. 세션 정보 업데이트 (audioUrl 저장)
        session.updateAudioUrl(fileInfo.getFileUrl());
        practiceSessionRepository.save(session);
        log.info("✅ 세션 audioUrl 업데이트 완료 - audioUrl: {}", fileInfo.getFileUrl());

        // === 트랜잭션 1 종료 (파일 저장 + 세션 업데이트) ===

        // 5. 오디오 분석 및 결과 저장 (별도 트랜잭션)
        try {
            processAudioAnalysisAndSaveResult(sessionId, fileInfo.getFilePath());
        } catch (Exception e) {
            log.error("❌ 오디오 분석 실패 - sessionId: {}, error: {}", sessionId, e.getMessage(), e);
            // 분석 실패 시 저장된 파일 정리
            try {
                fileUploadService.deleteFile(fileInfo.getFilePath());
                log.info("🗑️ 분석 실패로 인한 파일 삭제 완료 - filePath: {}", fileInfo.getFilePath());
            } catch (Exception cleanupEx) {
                log.warn("⚠️ 파일 삭제 실패 - filePath: {}", fileInfo.getFilePath(), cleanupEx);
            }
            throw new RuntimeException("오디오 분석에 실패했습니다: " + e.getMessage(), e);
        }

        log.info("✅ 연습 세션 종료 완료 - sessionId: {}", sessionId);
        return sessionId;
    }

    /**
     * 오디오 분석 및 결과 저장 (별도 트랜잭션)
     * 
     * @param sessionId 세션 ID
     * @param filePath  오디오 파일 경로
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processAudioAnalysisAndSaveResult(Long sessionId, String filePath) throws Exception {
        log.info("🔍 오디오 분석 시작 - sessionId: {}, filePath: {}", sessionId, filePath);

        // 1. 세션 다시 조회 (새로운 트랜잭션이므로)
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다. sessionId: " + sessionId));

        // 2. 오디오 분석
        AudioAnalysisService.AnalysisResult analysisResult = audioAnalysisService.analyzeAudio(filePath);
        log.info("✅ 오디오 분석 완료 - sessionId: {}, windows: {}",
                sessionId, analysisResult.getWindows().size());

        // 3. 분석 결과 DB 저장
        log.info("💾 분석 결과 저장 시작 - sessionId: {}", sessionId);
        analysisResultService.saveAnalysisResult(session, analysisResult);
        log.info("✅ 분석 결과 저장 완료 - sessionId: {}", sessionId);
    }

    /**
     * 연습 세션 피드백 조회
     * 
     * @param sessionId 조회할 세션 ID
     * @return 피드백 정보
     */
    @Transactional(readOnly = true)
    public PracticeFeedbackDto getFeedback(Long sessionId) {
        log.info("📊 피드백 조회 시작 - sessionId: {}", sessionId);

        // 피드백 조회
        Feedback feedback = feedbackRepository.findByPracticeSessionIdSessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("피드백을 찾을 수 없습니다. sessionId: " + sessionId));

        log.info("✅ 피드백 조회 완료 - sessionId: {}, grade: {}", sessionId, feedback.getGrade());

        // DTO 변환 및 반환 (공백 정보 포함)
        return PracticeFeedbackDto.builder()
                .sessionId(sessionId)
                .feedbackId(feedback.getFeedbackId())
                .spmScore(feedback.getSpmScore())
                .fillerScore(feedback.getFillerScore())
                .repeatScore(feedback.getRepeatScore())
                .totalScore(feedback.getTotalScore())
                .grade(feedback.getGrade())
                .silenceCount(feedback.getSilenceCount())
                .totalSilenceDuration(feedback.getTotalSilenceDuration())
                .silenceScore(feedback.getSilenceScore())
                // silenceAnalysisSuccess는 사용자에게 노출하지 않음
                .build();
    }

    /**
     * 슬라이드 이미지 URL 생성 (TODO: 실제 구현 필요)
     * 
     * @param presentationFile 발표 파일
     * @param pageNumber       페이지 번호
     * @return 이미지 URL
     */
    private String generateSlideImageUrl(PresentationFile presentationFile, int pageNumber) {
        // TODO: 실제 이미지 저장 방식에 따라 구현 필요
        // 옵션 1: PDF를 이미지로 변환하여 저장된 경로 반환
        // 옵션 2: 프론트에서 PDF 렌더링하도록 PDF URL + 페이지 번호 반환
        // 옵션 3: 별도 이미지 저장 테이블 조회

        // 임시: fileUrl을 그대로 반환 (프론트에서 PDF 렌더링 가정)
        return presentationFile.getFileUrl() + "#page=" + pageNumber;
    }
}

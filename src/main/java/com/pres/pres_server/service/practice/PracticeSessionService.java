package com.pres.pres_server.service.practice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pres.pres_server.domain.Feedback;
import com.pres.pres_server.domain.SlideFeedback;
import com.pres.pres_server.domain.PracticeSession;
import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.Project;
import com.pres.pres_server.dto.file.FileInfoDto;
import com.pres.pres_server.dto.practice.PracticeFeedbackDto;
import com.pres.pres_server.dto.practice.SlideFeedbackDto;
import com.pres.pres_server.dto.practice.PracticeSessionStartDto;
import com.pres.pres_server.dto.qna.QnaComparisonDto;
import com.pres.pres_server.repository.FeedbackRepository;
import com.pres.pres_server.repository.SlideFeedbackRepository;
import com.pres.pres_server.repository.PracticeSessionRepository;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.ProjectRepository;
import com.pres.pres_server.service.analyse.AnalysisResultService;
import com.pres.pres_server.service.analyse.AudioAnalysisService;
import com.pres.pres_server.service.analyse.dto.SlideTransition;
import com.pres.pres_server.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

// 연습 세션 시작/종료, 슬라이드 및 큐카드 조회를 담당
@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeSessionService {

        private final ProjectRepository projectRepository;
        private final PracticeSessionRepository practiceSessionRepository;
        private final PresentationFileRepository presentationFileRepository;
        private final FeedbackRepository feedbackRepository;
        private final SlideFeedbackRepository slideFeedbackRepository;

        private final FileUploadService fileUploadService;
        private final AudioAnalysisService audioAnalysisService;
        private final AnalysisResultService analysisResultService;
        private final PracticeQnaService practiceQnaService;
        private final ObjectMapper objectMapper;

        /**
         * 연습 세션 시작
         * 
         * @param projectId 연습할 프로젝트 ID
         * @return 세션 정보 + 슬라이드 + 큐카드
         */
        @Transactional
        public PracticeSessionStartDto startSession(Long projectId) {
                log.info("연습 세션 시작 - projectId: {}", projectId);

                // 1. 프로젝트 확인
                Project project = projectRepository.findById(projectId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "프로젝트를 찾을 수 없습니다. projectId: " + projectId));

                // 2. PracticeSession 생성
                PracticeSession session = PracticeSession.builder()
                                .project(project)
                                .practicedAt(LocalDateTime.now())
                                .build();
                session = practiceSessionRepository.save(session);
                log.info("PracticeSession 생성 완료 - sessionId: {}", session.getSessionId());

                // 3. 프로젝트에 연결된 발표 파일 조회
                PresentationFile presentationFile = presentationFileRepository.findByProjectWithExtractedText(project)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "프로젝트에 연결된 발표 파일이 없습니다. projectId: " + projectId));

                Long fileId = presentationFile.getFileId();
                log.info("PresentationFile 조회 완료 - fileId: {}", fileId);

                // 4. 여기서는 더 이상 슬라이드/큐카드/qrUrl 리스트를 조립하지 않는다.
                // 프론트가 /images/{fileId}, /qr-info/{fileId} 를 따로 호출해서 쓴다.

                return PracticeSessionStartDto.builder()
                                .sessionId(session.getSessionId())
                                .projectId(projectId)
                                .fileId(fileId) // ★ DTO에 이 필드 추가 필요
                                // slides는 이제 굳이 안 내려도 됨. 프론트가 별도 API에서 받음.
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
        public Long endSession(Long sessionId, MultipartFile audioFile) {
                log.info("🎬 연습 세션 종료 시작 - sessionId: {}, audioFile: {}",
                                sessionId, audioFile.getOriginalFilename());

                // 1. 세션 존재 여부 확인
                PracticeSession session = practiceSessionRepository.findById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "세션을 찾을 수 없습니다. sessionId: " + sessionId));

                // 2. 이미 종료된 세션인지 확인
                if (session.getAudioUrl() != null) {
                        throw new IllegalStateException("이미 종료된 세션입니다. sessionId: " + sessionId);
                }

                // 3. 오디오 파일 저장
                FileInfoDto fileInfo = fileUploadService.saveFile(audioFile);
                log.info(" 오디오 파일 저장 완료 - filePath: {}", fileInfo.getFilePath());

                // 4. 세션 정보 업데이트 (audioUrl 저장)
                session.updateAudioUrl(fileInfo.getFileUrl());
                practiceSessionRepository.save(session);
                log.info(" 세션 audioUrl 업데이트 완료 - audioUrl: {}", fileInfo.getFileUrl());

                log.info(" 연습 세션 종료 완료 - sessionId: {}", sessionId);
                return sessionId;
        }

        /**
         * 오디오 분석 및 결과 저장 (별도 트랜잭션)
         * 
         * @param sessionId 세션 ID
         * @param filePath  오디오 파일 경로
         */
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        protected void processAudioAnalysisAndSaveResult(Long sessionId, String filePath,
                        List<SlideTransition> slideTransitions) throws Exception {
                log.info("🔍 오디오 분석 시작 - sessionId: {}, filePath: {}", sessionId, filePath);

                // 1. 세션 다시 조회 (새로운 트랜잭션이므로)
                PracticeSession session = practiceSessionRepository.findById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "세션을 찾을 수 없습니다. sessionId: " + sessionId));

                // 2. 오디오 분석 (projectId 전달)
                Long projectId = session.getProject() != null ? session.getProject().getProjectId() : null;
                AudioAnalysisService.AnalysisResult analysisResult = audioAnalysisService.analyzeAudio(filePath,
                                projectId,
                                slideTransitions);
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

                // 1. 발표 피드백 조회
                Feedback feedback = feedbackRepository.findByPracticeSessionSessionId(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "피드백을 찾을 수 없습니다. sessionId: " + sessionId));

                log.info("✅ 피드백 조회 완료 - sessionId: {}, grade: {}", sessionId, feedback.getGrade());

                // 2. QnA 비교 결과 조회 (있으면 포함)
                QnaComparisonDto qnaComparison = null;
                try {
                        qnaComparison = practiceQnaService.getComparison(sessionId);
                        log.info("✅ QnA 비교 결과 포함 - sessionId: {}, similarity: {}",
                                        sessionId, qnaComparison.getSimilarity());
                } catch (IllegalStateException e) {
                        // QnA 답변 제출 안 함 - null 유지
                        log.info("ℹ️ QnA 답변 없음 - sessionId: {}", sessionId);
                } catch (Exception e) {
                        // 기타 예외 - null 유지, 로그만 남김
                        log.warn("⚠️ QnA 비교 결과 조회 실패 - sessionId: {}, error: {}", sessionId, e.getMessage());
                }

                // 3. 슬라이드별 피드백 조회 및 변환
                List<SlideFeedbackDto> slideFeedbacks = getSlideFeedbacks(feedback.getFeedbackId());

                // 4. 전체 STT 텍스트 조회
                PracticeSession session = practiceSessionRepository.findById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "세션을 찾을 수 없습니다. sessionId: " + sessionId));

                // 5. DTO 변환 및 반환 (발표 피드백 + 슬라이드별 피드백 + QnA 결과)
                return PracticeFeedbackDto.builder()
                                .sessionId(sessionId)
                                .feedbackId(feedback.getFeedbackId())
                                .spmScore(feedback.getSpmScore())
                                .fillerScore(feedback.getFillerScore())
                                .repeatScore(feedback.getRepeatScore())
                                .accuracyScore(feedback.getAccuracyScore())
                                .totalScore(feedback.getTotalScore())
                                .grade(feedback.getGrade())
                                .slideFeedbacks(slideFeedbacks)
                                .fullSttText(session.getSttText())
                                .qnaComparison(qnaComparison) // QnA 있으면 포함, 없으면 null
                                .build();
        }

        /**
         * 슬라이드별 피드백 조회 및 DTO 변환
         */
        private List<SlideFeedbackDto> getSlideFeedbacks(Long feedbackId) {
                List<SlideFeedback> slideFeedbacks = slideFeedbackRepository
                                .findByFeedbackIdOrderBySlideNumber(feedbackId);

                return slideFeedbacks.stream()
                                .map(this::convertToDto)
                                .collect(Collectors.toList());
        }

        /**
         * SlideFeedback 엔티티를 DTO로 변환
         */
        private SlideFeedbackDto convertToDto(SlideFeedback entity) {
                Map<String, Integer> fillerDetail = null;
                if (entity.getFillerDetail() != null) {
                        try {
                                fillerDetail = objectMapper.readValue(
                                                entity.getFillerDetail(),
                                                new TypeReference<Map<String, Integer>>() {
                                                });
                        } catch (Exception e) {
                                log.warn("필러 상세 정보 JSON 파싱 실패 - slideNumber: {}", entity.getSlideNumber(), e);
                        }
                }

                return SlideFeedbackDto.builder()
                                .slideNumber(entity.getSlideNumber())
                                .timestampSeconds(entity.getTimestampSeconds())
                                .slideText(entity.getSlideText())
                                .issueType(entity.getIssueType())
                                .spmUser(entity.getSpmUser())
                                .spmAverage(entity.getSpmAverage())
                                .fillerCount(entity.getFillerCount())
                                .fillerDetail(fillerDetail)
                                .silenceCount(entity.getSilenceCount())
                                .totalSilenceDuration(entity.getTotalSilenceDuration())
                                .silenceScore(entity.getSilenceScore())
                                .repeatCount(entity.getRepeatCount())
                                .repeatDetail(entity.getRepeatDetail())
                                .errorCount(entity.getErrorCount())
                                .comment(entity.getComment())
                                .build();
        }

}

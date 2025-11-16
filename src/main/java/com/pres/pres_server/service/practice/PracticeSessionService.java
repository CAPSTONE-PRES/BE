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
import com.pres.pres_server.dto.practice.PracticeHistoryDto;
import com.pres.pres_server.dto.practice.SlideFeedbackDto;
import com.pres.pres_server.dto.practice.PracticeSessionStartDto;
import com.pres.pres_server.dto.practice.IssueDto;
import com.pres.pres_server.repository.FeedbackRepository;
import com.pres.pres_server.repository.SlideFeedbackRepository;
import com.pres.pres_server.repository.PracticeSessionRepository;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.ProjectRepository;
import com.pres.pres_server.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

        private final com.pres.pres_server.service.file.PresentationImageService presentationImageService;
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

                // QnA 비교 결과는 이 API 응답에 포함하지 않음 (프론트는 질문 단위 피드백 엔드포인트 사용)

                // 3. 슬라이드별 피드백 조회 및 변환
                PracticeSession session = practiceSessionRepository.findById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "세션을 찾을 수 없습니다. sessionId: " + sessionId));
                Long projectId = session.getProject().getProjectId();

                // 이미지 URL 매핑: fileId -> 페이지별 URL
                Long fileId = null;
                Map<Integer, String> slideToImageUrl = new HashMap<>();
                try {
                        var pfOpt = presentationFileRepository.findByProjectWithExtractedText(session.getProject());
                        if (pfOpt.isPresent()) {
                                fileId = pfOpt.get().getFileId();
                                try {
                                        List<String> urls = presentationImageService.getImageUrls(fileId);
                                        for (String u : urls) {
                                                // url ends with /page/{n}/image
                                                try {
                                                        String[] parts = u.split("/page/");
                                                        if (parts.length > 1) {
                                                                String rest = parts[1];
                                                                String pageStr = rest.split("/")[0];
                                                                int page = Integer.parseInt(pageStr);
                                                                slideToImageUrl.put(page, u);
                                                        }
                                                } catch (Exception ignore) {
                                                }
                                        }
                                } catch (Exception e) {
                                        log.info("프레젠테이션 이미지 URL 조회 실패: {}", e.getMessage());
                                }
                        }
                } catch (Exception ignore) {
                }

                // 3b. 슬라이드별 피드백 조회 및 변환
                List<SlideFeedbackDto> slideFeedbacks = getSlideFeedbacks(feedback.getFeedbackId(), slideToImageUrl);

                List<PracticeHistoryDto> history = feedbackRepository.findHistoryByProjectIdExcludingSession(projectId,
                                sessionId);

                // 5. DTO 변환 및 반환 (발표 피드백 + 슬라이드별 피드백 + QnA 결과)
                // AI 피드백은 AnalysisResultService에서 슬라이드별로 생성되어 DB에 저장됩니다.
                // 여기서는 저장된 슬라이드 피드백의 코멘트를 모아 aiFeedback 맵을 조립해서 반환합니다.
                Map<String, String> aiFeedback = new HashMap<>();

                // 1) 망설임(Hesitation): 슬라이드 이슈가 SILENCE이거나 silenceCount가 있는 경우의 comment 합치기
                String hesitation = slideFeedbacks.stream()
                                .filter(s -> "SILENCE".equals(s.getIssueType())
                                                || (s.getSilenceCount() != null && s.getSilenceCount() > 0))
                                .map(SlideFeedbackDto::getComment)
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining(" "));
                if (hesitation != null && !hesitation.isBlank())
                        aiFeedback.put("hesitation", hesitation);

                // 2) 반복(Repetition)
                String repetition = slideFeedbacks.stream()
                                .filter(s -> "REPETITION".equals(s.getIssueType())
                                                || (s.getRepeatCount() != null && s.getRepeatCount() > 0))
                                .map(SlideFeedbackDto::getComment)
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining(" "));
                if (repetition != null && !repetition.isBlank())
                        aiFeedback.put("repetition", repetition);

                // 3) 정확도(Accuracy)
                String accuracy = slideFeedbacks.stream()
                                .filter(s -> "ACCURACY".equals(s.getIssueType())
                                                || (s.getErrorCount() != null && s.getErrorCount() > 0))
                                .map(SlideFeedbackDto::getComment)
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining(" "));
                if (accuracy != null && !accuracy.isBlank())
                        aiFeedback.put("accuracy", accuracy);

                // 4) 속도(Pace / SPEED)
                String pace = slideFeedbacks.stream()
                                .filter(s -> "SPEED".equals(s.getIssueType())
                                                || (s.getSpmUser() != null
                                                                && (s.getSpmUser() < 250 || s.getSpmUser() > 330)))
                                .map(SlideFeedbackDto::getComment)
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining(" "));
                if (pace != null && !pace.isBlank())
                        aiFeedback.put("pace", pace);

                // 5) 전체 코멘트(옵션): 슬라이드 코멘트 전체를 합쳐서 overall로 둠 (필요시 프론트에서 사용)
                String overall = slideFeedbacks.stream()
                                .map(SlideFeedbackDto::getComment)
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining(" "));
                if (overall != null && !overall.isBlank())
                        aiFeedback.put("overall", overall);

                return PracticeFeedbackDto.builder()
                                .sessionId(sessionId)
                                .feedbackId(feedback.getFeedbackId())
                                .spmScore(feedback.getSpmScore())
                                .fillerScore(feedback.getFillerScore())
                                .silenceScore(feedback.getSilenceScore())
                                .repeatScore(feedback.getRepeatScore())
                                .accuracyScore(feedback.getAccuracyScore())
                                .totalScore(feedback.getTotalScore())
                                .grade(feedback.getGrade())
                                .totalDurationSeconds(session.getDurationSeconds())
                                .slideFeedbacks(slideFeedbacks)
                                // full STT text excluded from session feedback response
                                .history(history)
                                // QnA 비교 결과는 제외
                                .aiFeedback(aiFeedback)
                                .build();
        }

        /**
         * 슬라이드별 피드백 조회 및 DTO 변환
         */
        private List<SlideFeedbackDto> getSlideFeedbacks(Long feedbackId, Map<Integer, String> slideToImageUrl) {
                List<SlideFeedback> slideFeedbacks = slideFeedbackRepository
                                .findByFeedbackIdOrderBySlideNumber(feedbackId);

                return slideFeedbacks.stream()
                                .map(entity -> convertToDto(entity, slideToImageUrl))
                                .collect(Collectors.toList());
        }

        /**
         * SlideFeedback 엔티티를 DTO로 변환
         */
        private SlideFeedbackDto convertToDto(SlideFeedback entity, Map<Integer, String> slideToImageUrl) {
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

                // issues JSON -> List<IssueDto>
                java.util.List<IssueDto> issues = null;
                if (entity.getIssues() != null) {
                        try {
                                issues = objectMapper.readValue(entity.getIssues(),
                                                new TypeReference<java.util.List<IssueDto>>() {
                                                });
                        } catch (Exception e) {
                                log.warn("슬라이드 이슈 JSON 파싱 실패 - slideNumber: {}", entity.getSlideNumber(), e);
                        }
                }

                SlideFeedbackDto.SlideFeedbackDtoBuilder builder = SlideFeedbackDto.builder()
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
                                .issues(issues);

                // 썸네일 URL 채우기 (가능하면)
                if (slideToImageUrl != null) {
                        String url = slideToImageUrl.get(entity.getSlideNumber());
                        if (url != null) {
                                builder.thumbnailUrl(url);
                        }
                }

                return builder.build();
        }

}

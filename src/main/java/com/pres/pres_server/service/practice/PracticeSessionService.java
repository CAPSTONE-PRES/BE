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
        private final com.pres.pres_server.service.ai.OpenAIFeedbackService openAIFeedbackService;

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
                                        // 페이지 번호 -> 외부 이미지 URL 매핑을 가져와 직접 사용
                                        java.util.Map<Integer, String> urlMap = presentationImageService
                                                        .getImageUrlMap(fileId);
                                        if (urlMap != null && !urlMap.isEmpty()) {
                                                slideToImageUrl.putAll(urlMap);
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

                // 4. 전체 피드백 생성 (AI)
                // Determine lowest scoring issue among the five score fields: SPM(SPEED),
                // FILLER, REPETITION, SILENCE, ACCURACY
                String lowestIssueType = null;
                Integer lowestScore = null;
                Map<String, Integer> scoreMap = new LinkedHashMap<>();
                scoreMap.put("SPEED", feedback.getSpmScore());
                scoreMap.put("FILLER", feedback.getFillerScore());
                scoreMap.put("REPETITION", feedback.getRepeatScore());
                scoreMap.put("SILENCE", feedback.getSilenceScore());
                scoreMap.put("ACCURACY", feedback.getAccuracyScore());

                for (Map.Entry<String, Integer> e : scoreMap.entrySet()) {
                        Integer v = e.getValue();
                        if (v == null)
                                continue;
                        if (lowestScore == null || v < lowestScore) {
                                lowestScore = v;
                                lowestIssueType = e.getKey();
                        }
                }

                String lowestIssueLabel = lowestIssueType != null ? switch (lowestIssueType) {
                        case "SPEED" -> "말하기 속도";
                        case "FILLER" -> "불필요한 추임새";
                        case "REPETITION" -> "반복되는 어휘";
                        case "SILENCE" -> "침묵 사용";
                        case "ACCURACY" -> "발표 정확도";
                        default -> "발표 전반";
                } : "발표 전반";

                String overallFeedback = null;
                if (lowestIssueType != null) {
                        // Aggregate slide-level comments for the selected issue type
                        StringBuilder agg = new StringBuilder();
                        try {
                                for (SlideFeedbackDto s : slideFeedbacks) {
                                        if (s == null || s.getIssues() == null)
                                                continue;
                                        for (IssueDto it : s.getIssues()) {
                                                if (it == null || it.getIssueType() == null)
                                                        continue;
                                                if (lowestIssueType.equals(it.getIssueType())) {
                                                        if (it.getComment() != null && !it.getComment().isBlank()) {
                                                                if (agg.length() > 0)
                                                                        agg.append("\n");
                                                                agg.append("Slide ").append(s.getSlideNumber())
                                                                                .append(": ").append(it.getComment());
                                                        }
                                                }
                                        }
                                }
                        } catch (Exception ignore) {
                        }

                        String aggregatedComments = agg.length() > 0 ? agg.toString() : "";
                        try {
                                overallFeedback = openAIFeedbackService.generateOverallFeedback(
                                                String.valueOf(sessionId), lowestIssueLabel,
                                                aggregatedComments);
                        } catch (Exception e) {
                                log.warn("전체 AI 피드백 생성 실패 - sessionId={} reason={}", sessionId, e.getMessage());
                                overallFeedback = null;
                        }
                }

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
                                .overallFeedback(overallFeedback)
                                // QnA 비교 결과는 제외
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

                // Fallback: if issues is null, build IssueDto list from individual fields
                if (issues == null) {
                        issues = new ArrayList<>();

                        // SPEED
                        if (entity.getSpmUser() != null) {
                                issues.add(IssueDto.builder()
                                                .issueType("SPEED")
                                                .spmUser(entity.getSpmUser())
                                                .spmAverage(entity.getSpmAverage())
                                                .comment(entity.getComment())
                                                .build());
                        }

                        // FILLER
                        if (entity.getFillerCount() != null && entity.getFillerCount() > 0) {
                                issues.add(IssueDto.builder()
                                                .issueType("FILLER")
                                                .fillerCount(entity.getFillerCount())
                                                .fillerDetail(fillerDetail)
                                                .comment(entity.getComment())
                                                .build());
                        }

                        // SILENCE
                        if (entity.getSilenceCount() != null && entity.getSilenceCount() > 0) {
                                issues.add(IssueDto.builder()
                                                .issueType("SILENCE")
                                                .errorCount(entity.getSilenceCount())
                                                .comment(entity.getComment())
                                                .build());
                        }

                        // REPETITION
                        if (entity.getRepeatCount() != null && entity.getRepeatCount() > 0) {
                                // try parse repeatDetail into map if it looks like JSON
                                Map<String, Integer> repeatMap = null;
                                if (entity.getRepeatDetail() != null) {
                                        try {
                                                repeatMap = objectMapper.readValue(entity.getRepeatDetail(),
                                                                new TypeReference<Map<String, Integer>>() {
                                                                });
                                        } catch (Exception ignore) {
                                                // fallback: create simple map from comma-separated tokens
                                                try {
                                                        repeatMap = new LinkedHashMap<>();
                                                        String[] parts = entity.getRepeatDetail().split(",");
                                                        for (String p : parts) {
                                                                String key = p.trim();
                                                                if (!key.isEmpty())
                                                                        repeatMap.put(key, 1);
                                                        }
                                                } catch (Exception ignore2) {
                                                        repeatMap = null;
                                                }
                                        }
                                }
                                issues.add(IssueDto.builder()
                                                .issueType("REPETITION")
                                                .repeatCount(entity.getRepeatCount())
                                                .repeatDetail(repeatMap)
                                                .comment(entity.getComment())
                                                .build());
                        }

                        // ACCURACY
                        if (entity.getErrorCount() != null && entity.getErrorCount() > 0) {
                                issues.add(IssueDto.builder()
                                                .issueType("ACCURACY")
                                                .errorCount(entity.getErrorCount())
                                                .comment(entity.getComment())
                                                .build());
                        }
                }

                SlideFeedbackDto.SlideFeedbackDtoBuilder builder = SlideFeedbackDto.builder()
                                .slideNumber(entity.getSlideNumber())
                                .timestampSeconds(entity.getTimestampSeconds())
                                .slideText(entity.getSlideText())
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

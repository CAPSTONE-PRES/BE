package com.pres.pres_server.service.analyse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import com.pres.pres_server.service.analyse.dto.WhisperSegment;

/**
 * Whisper STT 결과를 기반으로 2.5초 이상의 공백(silence)을 감지하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SilenceDetectionService {

    private static final double SILENCE_THRESHOLD = 2.5; // 2.5초 이상만 감지

    /**
     * Whisper segments 사이의 공백 감지 (전체)
     *
     * @param segments Whisper API의 verbose_json 응답에서 추출한 segment 리스트
     * @return 2.5초 이상의 공백 구간 리스트
     */
    public List<SilenceInterval> detectSilences(List<WhisperSegment> segments) {
        List<SilenceInterval> silences = new ArrayList<>();

        if (segments == null || segments.size() < 2) {
            log.debug("segment가 {}개로 공백 감지 불가", segments == null ? 0 : segments.size());
            return silences; // segment가 1개 이하면 공백 없음
        }

        for (int i = 0; i < segments.size() - 1; i++) {
            WhisperSegment current = segments.get(i);
            WhisperSegment next = segments.get(i + 1);

            // null 체크
            if (current == null || next == null) {
                log.warn("segment {}번이 null입니다. 건너뜁니다.", i);
                continue;
            }

            double gap = next.getStart() - current.getEnd();

            // 음수 gap 체크 (시간 순서가 뒤바뀐 경우)
            if (gap < 0) {
                log.warn("segment {}~{} 시간 순서 오류: current.end={}, next.start={}",
                        i, i + 1, current.getEnd(), next.getStart());
                continue;
            }

            // 2.5초 이상 공백만 기록
            if (gap >= SILENCE_THRESHOLD) {
                silences.add(SilenceInterval.builder()
                        .startTime(current.getEnd())
                        .endTime(next.getStart())
                        .duration(gap)
                        .build());

                log.debug("공백 감지: {}초 ~ {}초 (길이: {}초)",
                        String.format("%.2f", current.getEnd()), String.format("%.2f", next.getStart()),
                        String.format("%.2f", gap));
            }
        }

        log.info("총 {}개의 공백 구간 감지 (2.5초 이상)", silences.size());
        return silences;
    }

    /**
     * 슬라이드별 Whisper segments 리스트에 대해 각 슬라이드별로 공백 감지
     * 
     * @param slidesSegments 슬라이드별 WhisperSegment 리스트 (각 슬라이드마다 1개의
     *                       List<WhisperSegment>)
     * @return 슬라이드별 공백 구간 리스트 (슬라이드 인덱스별로 List<SilenceInterval> 반환)
     */
    public List<List<SilenceInterval>> detectSilencesBySlides(List<List<WhisperSegment>> slidesSegments) {
        List<List<SilenceInterval>> result = new ArrayList<>();
        if (slidesSegments == null)
            return result;
        for (int i = 0; i < slidesSegments.size(); i++) {
            List<WhisperSegment> slideSegments = slidesSegments.get(i);
            List<SilenceInterval> silences = detectSilences(slideSegments);
            result.add(silences);
            log.info("슬라이드 {}: {}개의 공백 구간 감지", i, silences.size());
        }
        return result;
    }

    /**
     * 공백 통계 계산
     */
    public SilenceStatistics calculateStatistics(List<SilenceInterval> silences) {
        if (silences == null || silences.isEmpty()) {
            log.debug("공백 구간이 {}개로 통계 계산 불가", silences == null ? "null" : "0");
            return SilenceStatistics.builder()
                    .silenceCount(0)
                    .totalSilenceDuration(0.0)
                    .averageSilenceDuration(0.0)
                    .longestSilence(0.0)
                    .success(true) // 공백 0개도 정상 분석
                    .build();
        }

        double totalDuration = silences.stream()
                .filter(s -> s != null && s.getDuration() > 0) // null과 음수 필터링
                .mapToDouble(SilenceInterval::getDuration)
                .sum();

        double longestSilence = silences.stream()
                .filter(s -> s != null && s.getDuration() > 0)
                .mapToDouble(SilenceInterval::getDuration)
                .max()
                .orElse(0.0);

        int validCount = (int) silences.stream()
                .filter(s -> s != null && s.getDuration() > 0)
                .count();

        return SilenceStatistics.builder()
                .silenceCount(validCount)
                .totalSilenceDuration(totalDuration)
                .averageSilenceDuration(validCount > 0 ? totalDuration / validCount : 0.0)
                .longestSilence(longestSilence)
                .success(true) // 정상 계산 완료
                .build();
    }

    // WhisperSegment moved to
    // com.pres.pres_server.service.analyse.dto.WhisperSegment

    /**
     * 공백 구간 정보
     */
    @lombok.Data
    @lombok.Builder
    public static class SilenceInterval {
        private double startTime; // 공백 시작 시간
        private double endTime; // 공백 종료 시간
        private double duration; // 공백 길이 (초)
    }

    /**
     * 공백 통계
     */
    @lombok.Data
    @lombok.Builder
    public static class SilenceStatistics {
        private int silenceCount; // 공백 개수
        private double totalSilenceDuration; // 총 공백 시간
        private double averageSilenceDuration; // 평균 공백 시간
        private double longestSilence; // 최장 공백 시간
        private boolean success; // 분석 성공 여부
    }
}

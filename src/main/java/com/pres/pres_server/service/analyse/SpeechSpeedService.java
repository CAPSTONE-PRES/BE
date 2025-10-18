package com.pres.pres_server.service.analyse;

import java.util.NavigableMap;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SpeechSpeedService {

    private static final Logger log = LoggerFactory.getLogger(SpeechSpeedService.class);

    /**
     * 한글 완성형 유니코드 범위
     * - 시작: '가' (0xAC00 = 44032)
     * - 끝: '힣' (0xD7A3 = 55203)
     */
    private static final char KOREAN_SYLLABLE_START = 0xAC00;
    private static final char KOREAN_SYLLABLE_END = 0xD7A3;

    /**
     * SPM 계산 시 분당 변환 상수
     */
    private static final int SECONDS_PER_MINUTE = 60;

    /**
     * 점수 범위 상수
     */
    private static final int MIN_SCORE = 50;
    private static final int MAX_SCORE = 100;

    /**
     * SPM-점수 매핑 테이블
     * 
     * Key: SPM 최소값 (해당 값 이상), Value: 점수
     * TreeMap을 사용하여 자동 정렬 및 범위 검색 지원
     * <b>점수 분포:</b>
     * 
     * <li>330-370 SPM: 100점 (최적 속도)</li>
     * <li>300-330, 370-400 SPM: 90점</li>
     * <li>270-300, 400-420 SPM: 75점</li>
     * <li>240-270, 420-450 SPM: 60점</li>
     * <li>240 미만, 450 초과: 50점 (너무 느리거나 빠름)</li>
     */
    private static final NavigableMap<Integer, Integer> SPM_SCORE_MAP = new TreeMap<>();

    static {
        // SPM 값에 따른 점수 매핑 초기화
        // 낮은 SPM부터 높은 SPM 순서로 정의
        SPM_SCORE_MAP.put(0, MIN_SCORE); // 0~240: 너무 느림
        SPM_SCORE_MAP.put(240, 60); // 240~270: 느림
        SPM_SCORE_MAP.put(270, 75); // 270~300: 약간 느림
        SPM_SCORE_MAP.put(300, 90); // 300~330: 적정
        SPM_SCORE_MAP.put(330, MAX_SCORE); // 330~370: 최적
        SPM_SCORE_MAP.put(370, 90); // 370~400: 적정
        SPM_SCORE_MAP.put(400, 75); // 400~420: 약간 빠름
        SPM_SCORE_MAP.put(420, 60); // 420~450: 빠름
        SPM_SCORE_MAP.put(450, MIN_SCORE); // 450~: 너무 빠름
    }

    /**
     * text 문자열에서 “한글 음절 블록” 개수를 센다.
     * 한국어 음절 블록(AC00~D7A3)만 센 후, 반환.
     */
    private boolean isKoreanSyllable(char ch) {
        return ch >= KOREAN_SYLLABLE_START && ch <= KOREAN_SYLLABLE_END;
    }

    public int countKoreanSyllables(String text) {
        if (text == null || text.isEmpty()) {
            log.debug("빈 텍스트 입력 - 음절 수 0 반환");
            return 0;
        }
        int count = 0;
        for (char c : text.toCharArray()) {
            if (isKoreanSyllable(c)) {
                count++;
            }
        }
        log.debug("한글 음절 카운트: {} (텍스트 길이: {})", count, text.length());
        return count;
    }

    /**
     * 음절 개수와 시간으로 SPM(Syllables Per Minute) 계산
     * 
     * @param syllableCount 음절 개수 (0 이상)
     * @param windowSeconds 시간(초) (0 초과)
     * @return 분당 음절 수 (SPM)
     * @throws IllegalArgumentException windowSeconds가 0 이하인 경우
     */
    public int calculateSpm(int syllableCount, double windowSeconds) {
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException(
                    String.format("시간은 0보다 커야 합니다: %.2f", windowSeconds));
        }
        if (syllableCount < 0) {
            throw new IllegalArgumentException(
                    String.format("음절 수는 0 이상이어야 합니다: %d", syllableCount));
        }

        // SPM = (음절 수 / 시간(초)) * 60
        double rawSpm = (double) syllableCount / windowSeconds * SECONDS_PER_MINUTE;
        int spm = (int) Math.round(rawSpm);

        log.debug("SPM 계산: {} 음절 / {}초 = {} SPM",
                syllableCount, String.format("%.2f", windowSeconds), spm);

        return spm;
    }

    /**
     * SPM 값을 “점수(0~100)” 대역으로 매핑.
     * • 450 이상 → 50점 이하
     * • 420~450 → 60
     * • 400~420 → 75
     * • 370~400 → 90
     * • 330~370 → 100
     * • 300~330 → 90
     * • 270~300 → 75
     * • 240~270 → 60
     * 
     * @return 발표 속도 점수 (50~100)
     */
    public int mapSpmToScore(int spm) {
        if (spm < 0) {
            log.warn("음수 SPM 입력: {} - 최소 점수 반환", spm);
            return MIN_SCORE;
        }

        // TreeMap의 floorEntry를 사용하여 효율적인 범위 검색
        // spm 이하의 최대 키를 찾아 해당 점수 반환
        var entry = SPM_SCORE_MAP.floorEntry(spm);
        int score = (entry != null) ? entry.getValue() : MIN_SCORE;

        log.debug("SPM {} → 점수 {}", spm, score);
        return score;
    }

    /**
     * SPM 값이 최적 범위인지 확인
     * 
     * @param spm 분당 음절 수
     * @return 최적 범위(330-370)이면 true
     */
    public boolean isOptimalSpeed(int spm) {
        return spm >= 330 && spm <= 370;
    }

    /**
     * SPM 값의 속도 등급을 문자열로 반환
     * 
     * @param spm 분당 음절 수
     * @return 속도 등급 ("너무 느림", "느림", "적정", "빠름", "너무 빠름")
     */
    public String getSpeedLevel(int spm) {
        if (spm < 240)
            return "너무 느림";
        if (spm < 300)
            return "느림";
        if (spm < 370)
            return "적정";
        if (spm < 450)
            return "빠름";
        return "너무 빠름";
    }
}

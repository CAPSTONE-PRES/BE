package com.pres.pres_server.service.analyse;

import org.springframework.stereotype.Service;

@Service
public class SpeechSpeedService {

    /**
     * text 문자열에서 “한글 음절 블록” 개수를 센다.
     * 한국어 음절 블록(AC00~D7A3)만 센 후, 반환.
     */
    public int countKoreanSyllables(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (char c : text.toCharArray()) {
            // 유니코드 한글 글자(완성형) 범위: 0xAC00(44032) ~ 0xD7A3(55203)
            if (c >= 0xAC00 && c <= 0xD7A3) {
                count++;
            }
        }
        return count;
    }

    /**
     * 30초 윈도우 내 음절 개수가 주어졌을 때, 분당 SPM을 계산.
     * 일반 공식: (음절개수 / 윈도우길이초) * 60
     * 여기서는 윈도우길이가 30초로 고정되어 있어서 → (음절개수 * 60) / 30 = 음절개수 * 2
     */
    public int calculateSpm(int syllableCount, double windowSec) {
        if (windowSec <= 0) {
            return 0;
        }
        double rawSpm = (double) syllableCount / windowSec * 60.0;
        return (int) Math.round(rawSpm);
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
     * • 240 이하 → 50점 이하
     */
    // 점수 조정 필요
    public int mapSpmToScore(int spm) {
        if (spm > 450) {
            return 50;
        } else if (spm >= 420) { // 420 ≤ spm ≤ 450
            return 60;
        } else if (spm >= 400) { // 400 ≤ spm < 420
            return 75;
        } else if (spm >= 370) { // 370 ≤ spm < 400
            return 90;
        } else if (spm >= 330) { // 330 ≤ spm < 370
            return 100;
        } else if (spm >= 300) { // 300 ≤ spm < 330
            return 90;
        } else if (spm >= 270) { // 270 ≤ spm < 300
            return 75;
        } else if (spm >= 240) { // 240 ≤ spm < 270
            return 60;
        } else { // spm < 240
            return 50;
        }
    }
}

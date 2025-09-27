package com.pres.pres_server.service.analyse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FillerService {
    private static final Logger log = LoggerFactory.getLogger(FillerService.class);

    // “그”는 사전에 빼고, 실제 피드백할 필러 워드만 남긴다.
    // (단일 음절인 “음”, “어”, “아”와 두 음절 이상인 “뭐지”만 리스트에 둠)
    private static final Set<String> FILLERS = Set.of(
            "음",
            "어",
            "아",
            "뭐지");

    // 정규식 패턴을 미리 컴파일해두면, 매번 Pattern.compile하지 않아도 된다.
    // - "\\b뭐지\\b" 대신, 앞뒤가 공백 또는 텍스트 시작/끝인 경우로 처리.
    // (^|\\s)뭐지(\\s|$) 형태로 패턴을 만듦.
    private static final Pattern PAT_TTUMJI = Pattern.compile("(^|\\s)(뭐지)(?=\\s|$)");

    // 짧은 “음”, “어”, “아” 는 한글 경계를 잡기가 애매해서
    // “앞뒤가 공백 혹은 문장 시작/끝인 경우”로 패턴을 만든다.
    // 예: (^|\\s)(음)(?=\\s|$)
    private static final Pattern PAT_EUM = Pattern.compile("(^|\\s)(음)(?=\\s|$)");
    private static final Pattern PAT_EO = Pattern.compile("(^|\\s)(어)(?=\\s|$)");
    private static final Pattern PAT_AA = Pattern.compile("(^|\\s)(아)(?=\\s|$)");

    /**
     * 텍스트를 간단히 정규화한 뒤, 사전 목록에 있는 단어들이
     * 몇 회 등장했는지 세어 리턴한다.
     */
    public Map<String, Integer> countFillersByRegex(String text) {
        log.info("▶ countFillersByRegex: \"{}\"", text);
        Map<String, Integer> cnt = new HashMap<>();

        // 1) 텍스트 정규화: 모든 문장부호(마침표, 물음표, 쉼표, 줄바꿈 등)를 공백으로 바꾼다.
        // 필요하다면 따옴표, 쌍점(콜론) 등도 추가로 제거할 수 있다.
        String normalized = text
                // 마침표, 물음표, 느낌표, 줄임표 → 공백
                .replaceAll("[\\.\\?\\!…]", " ")
                // 쉼표, 콜론, 세미콜론, 따옴표 등 → 공백
                .replaceAll("[,;:\"“”·]", " ")
                // 탭/줄바꿈 등 모든 공백 문자를 단일 공백으로 치환
                .replaceAll("\\s+", " ")
                .trim();

        log.debug("    ▶ normalized: \"{}\"", normalized);

        // 2) “뭐지” 패턴 검사
        Matcher mTtu = PAT_TTUMJI.matcher(normalized);
        while (mTtu.find()) {
            cnt.merge("뭐지", 1, Integer::sum);
            log.debug("    → counted \"뭐지\" at index {}", mTtu.start());
        }

        // 3) “음” 패턴 검사
        Matcher mEm = PAT_EUM.matcher(normalized);
        while (mEm.find()) {
            cnt.merge("음", 1, Integer::sum);
            log.debug("    → counted \"음\" at index {}", mEm.start());
        }

        // 4) “어” 패턴 검사
        Matcher mEo = PAT_EO.matcher(normalized);
        while (mEo.find()) {
            cnt.merge("어", 1, Integer::sum);
            log.debug("    → counted \"어\" at index {}", mEo.start());
        }

        // 5) “아” 패턴 검사
        Matcher mAa = PAT_AA.matcher(normalized);
        while (mAa.find()) {
            cnt.merge("아", 1, Integer::sum);
            log.debug("    → counted \"아\" at index {}", mAa.start());
        }

        log.info("▶ countFillersByRegex 결과: {}", cnt);
        return cnt;
    }
}

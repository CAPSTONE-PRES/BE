// import java.util.*;
// import java.util.regex.*;

// public class test_filler_accuracy {
// // 개별 패턴들
// private static final Pattern PAT_EUM =
// Pattern.compile("(^|\\s)(음)(?=\\s|$)");
// private static final Pattern PAT_EO = Pattern.compile("(^|\\s)(어)(?=\\s|$)");
// private static final Pattern PAT_AA = Pattern.compile("(^|\\s)(아)(?=\\s|$)");
// private static final Pattern PAT_TTUMJI =
// Pattern.compile("(^|\\s)(뭐지)(?=\\s|$)");

// // 2) “뭐지” 패턴 검사
// Matcher mTtu = PAT_TTUMJI.matcher(normalized);while(mTtu.find())
// {
// cnt.merge("뭐지", 1, Integer::sum);
// log.debug(" → counted \"뭐지\" at index {}", mTtu.start());
// }

// // 3) “음” 패턴 검사
// Matcher mEm = PAT_EUM.matcher(normalized);while(mEm.find())
// {
// cnt.merge("음", 1, Integer::sum);
// log.debug(" → counted \"음\" at index {}", mEm.start());
// }

// // 4) “어” 패턴 검사
// Matcher mEo = PAT_EO.matcher(normalized);while(mEo.find())
// {
// cnt.merge("어", 1, Integer::sum);
// log.debug(" → counted \"어\" at index {}", mEo.start());
// }

// // 5) “아” 패턴 검사
// Matcher mAa = PAT_AA.matcher(normalized);while(mAa.find())
// {
// cnt.merge("아", 1, Integer::sum);
// log.debug(" → counted \"아\" at index {}", mAa.start());
// }

// // Map 패턴들
// private static final Map<String, Pattern> FILLER_PATTERNS = Map.of(
// "음", Pattern.compile("(^|\\s)(음)(?=\\s|$)"),
// "어", Pattern.compile("(^|\\s)(어)(?=\\s|$)"),
// "아", Pattern.compile("(^|\\s)(아)(?=\\s|$)"),
// "뭐지", Pattern.compile("(^|\\s)(뭐지)(?=\\s|$)"));

// public static Map<String, Integer> countIndividual(String text) {
// Map<String, Integer> cnt = new HashMap<>();

// Matcher mTtu = PAT_TTUMJI.matcher(text);
// while (mTtu.find()) cnt.merge("뭐지", 1, Integer::sum);

// Matcher mEm = PAT_EUM.matcher(text);
// while (mEm.find()) cnt.merge("음", 1, Integer::sum);

// Matcher mEo = PAT_EO.matcher(text);
// while (mEo.find()) cnt.merge("어", 1, Integer::sum);

// Matcher mAa = PAT_AA.matcher(text);
// while (mAa.find()) cnt.merge("아", 1, Integer::sum);

// return cnt;
// }

// public static Map<String, Integer> countWithMap(String text) {
// Map<String, Integer> cnt = new HashMap<>();

// for (Map.Entry<String, Pattern> entry : FILLER_PATTERNS.entrySet()) {
// String fillerWord = entry.getKey();
// Pattern pattern = entry.getValue();
// Matcher matcher = pattern.matcher(text);
// while (matcher.find()) {
// cnt.merge(fillerWord, 1, Integer::sum);
// }
// }

// return cnt;
// }

// public static void main(String[] args) {
// String[] testTexts = {
// "음 안녕하세요 어 오늘은 아 좋은 날이네요",
// "뭐지 이게 음 어떻게 된 거지 아 모르겠다",
// "음성인식이 음 잘 되나요 어 확인해보겠습니다",
// "아니 음 이건 어떻게 아 처리해야 할까요 뭐지"
// };

// for (String text : testTexts) {
// Map<String, Integer> result1 = countIndividual(text);
// Map<String, Integer> result2 = countWithMap(text);

// System.out.println("Text: " + text);
// System.out.println("Individual: " + result1);
// System.out.println("Map-based: " + result2);
// System.out.println("Same result: " + result1.equals(result2));
// System.out.println("---");
// }
// }
// }
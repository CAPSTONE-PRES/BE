package com.pres.pres_server.service.analyse.utils;

import kr.co.shineware.nlp.komoran.constant.DEFAULT_MODEL;
import kr.co.shineware.nlp.komoran.core.Komoran;
import kr.co.shineware.nlp.komoran.model.KomoranResult;
import kr.co.shineware.nlp.komoran.model.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class KomoranAnalyzer {
    private static volatile Komoran komoran;
    private static final Logger log = LoggerFactory.getLogger(KomoranAnalyzer.class);

    /**
     * Komoran lazy init (한 번만 시도, 실패 시 null 반환)
     */
    private static Komoran getKomoran() {
        if (komoran == null) {
            synchronized (KomoranAnalyzer.class) {
                if (komoran == null) {
                    try {
                        komoran = new Komoran(DEFAULT_MODEL.FULL);
                        log.info("✅ Komoran initialized");
                    } catch (Exception e) {
                        log.error("❌ Komoran init failed", e);
                        komoran = null; // 실패 시 null로 표시
                    }
                }
            }
        }
        return komoran;
    }

    /** 정규화 토큰 + 스팬(원문 begin/end) */
    public static final class NormToken {
        public final String norm; // 정규화 표제어(예: "발표", "하다", "보이다")
        public final int begin; // 원문 기준 시작 인덱스
        public final int end; // 원문 기준 끝 인덱스 (Komoran Token endIndex)
        public final String posHead; // 품사 헤드: N(명사류), V(동/형용사류), S(외래어/숫자)

        public NormToken(String norm, int begin, int end, String posHead) {
            this.norm = norm;
            this.begin = begin;
            this.end = end;
            this.posHead = posHead;
        }

        @Override
        public String toString() {
            return String.format("[%d,%d] %s(%s)", begin, end, norm, posHead);
        }
    }

    // ⭐ 제외할 일반 동사/형용사 (너무 흔한 것들)
    private static final Set<String> COMMON_VERBS = Set.of(
            "하", "되", "있", "없", "이", "아니");

    // ⭐ 제외할 의존명사/대명사
    private static final Set<String> COMMON_NOUNS = Set.of(
            "것", "수", "때", "곳", "데", "지", "게");

    // =========================
    // 공개 API
    // =========================

    /** (하위호환) Komoran 형태소 분석 결과에서 의미있는 토큰만 추출 (정규화 규칙 적용) */
    public static List<String> tokenizeKomoran(String text) {
        return tokenizeKomoranNorms(text);
    }

    /** 정규화 토큰 + 스팬(원문 begin/end) */
    public static List<NormToken> tokenizeForRepeatWithSpans(String text) {
        if (text == null || text.trim().isEmpty())
            return Collections.emptyList();

        Komoran k = getKomoran();
        if (k == null) {
            log.warn("Komoran not available - returning empty tokens");
            return Collections.emptyList();
        }

        KomoranResult result = k.analyze(text);
        List<Token> tokens = result.getTokenList();
        List<NormToken> out = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            String pos = t.getPos();
            String morph = t.getMorph();
            if (pos == null || morph == null || morph.isBlank())
                continue;

            if (isVerbLike(pos)) {
                MergeResult mr = mergeVerbLikeToLemma(tokens, i);
                out.add(new NormToken(mr.lemma, mr.begin, mr.end, "V"));
                i = mr.nextIndex - 1;
                continue;
            }

            Optional<String> normOpt = normalizeForRepeat(t);
            if (normOpt.isEmpty())
                continue;
            String norm = normOpt.get();

            String head = headPos(pos);
            if (!isMeaningfulHead(head))
                continue;

            out.add(new NormToken(norm, t.getBeginIndex(), t.getEndIndex(), head));
        }

        return out;
    }

    /**
     * Context-aware overload (단순히 context 로깅만 추가)
     */
    public static List<NormToken> tokenizeForRepeatWithSpans(String text, String context) {
        List<NormToken> out = tokenizeForRepeatWithSpans(text);
        if (log.isDebugEnabled() && !out.isEmpty()) {
            log.debug("Tokens({}): {} items", context, out.size());
        }
        return out;
    }

    /** 정규화 토큰 문자열만 반환 (의미 토큰만 유지) */
    public static List<String> tokenizeKomoranNorms(String text) {
        return tokenizeForRepeatWithSpans(text).stream()
                .filter(nt -> isMeaningfulHead(nt.posHead))
                .map(nt -> nt.norm)
                .collect(Collectors.toList());
    }

    /** 의미 토큰 기반 N-그램 생성 (정규화 토큰 사용) */
    public static List<String> komoranMeaningfulNGrams(String text, int n) {
        List<String> toks = tokenizeKomoranNorms(text);
        if (toks.size() < n)
            return Collections.emptyList();

        List<String> ngrams = new ArrayList<>();
        for (int i = 0; i <= toks.size() - n; i++) {
            List<String> ngramTokens = toks.subList(i, i + n);

            // 자기 중복 체크 (모든 토큰이 같으면 제외)
            Set<String> uniqueTokens = new HashSet<>(ngramTokens);
            if (uniqueTokens.size() == 1) {
                continue; // "일 일", "하다 하다" 같은 패턴 제외
            }

            ngrams.add(String.join(" ", ngramTokens));
        }
        return ngrams;
    }

    /** (기존) Komoran 토큰 기반 N-그램 생성 (정규화 문자열 기준) */
    public static List<String> komoranNGrams(String text, int n) {
        List<String> tokens = tokenizeKomoranNorms(text);
        if (tokens.size() < n)
            return new ArrayList<>();
        List<String> ngrams = new ArrayList<>();
        for (int i = 0; i <= tokens.size() - n; i++) {
            ngrams.add(String.join(" ", tokens.subList(i, i + n)));
        }
        return ngrams;
    }

    /** 형태소 분석 기반 키워드 추출 (빈도 필터링 포함) */
    public static Set<String> extractKeywordsKomoran(List<String> words, int minFrequency) {
        Komoran k = getKomoran();
        if (k == null) {
            log.warn("Komoran not available for keyword extraction");
            return Collections.emptySet();
        }

        List<String> nouns = new ArrayList<>();
        for (String text : words) {
            KomoranResult result = k.analyze(text);
            nouns.addAll(result.getNouns());
        }

        List<String> filtered = nouns.stream()
                .filter(word -> !TextNormalizer.isStopword(word))
                .filter(word -> word.length() >= 2)
                .collect(Collectors.toList());

        Map<String, Long> freq = filtered.stream()
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));
        if (freq.isEmpty())
            return Collections.emptySet();

        final int SHORT_DOC_THRESHOLD = 20;
        final int SHORT_DOC_TOPN = 3;
        if (filtered.size() <= SHORT_DOC_THRESHOLD) {
            return freq.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(SHORT_DOC_TOPN)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }
        return freq.entrySet().stream()
                .filter(e -> e.getValue() >= minFrequency)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * 상위 N개 키워드를 빈도 기준으로 반환합니다. 전체 키워드 집합 대신
     * 빈도 상위 `topN`개만 필요할 때 사용하세요.
     */
    public static List<String> extractTopKeywordsKomoran(List<String> words, int topN) {
        Komoran k = getKomoran();
        if (k == null) {
            log.warn("Komoran not available for top-keyword extraction");
            return Collections.emptyList();
        }

        List<String> nouns = new ArrayList<>();
        for (String text : words) {
            KomoranResult result = k.analyze(text);
            nouns.addAll(result.getNouns());
        }

        List<String> filtered = nouns.stream()
                .filter(word -> !TextNormalizer.isStopword(word))
                .filter(word -> word.length() >= 2)
                .collect(Collectors.toList());

        Map<String, Long> freq = filtered.stream()
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));
        if (freq.isEmpty())
            return Collections.emptyList();

        return freq.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(Math.max(0, topN))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /** 2회 이상 등장하는 원시 형태소 빈도 분석 */
    public static Map<String, Integer> extractRepeatedTokens(String text, int minCount) {
        if (text == null || text.trim().isEmpty())
            return new HashMap<>();

        Komoran k = getKomoran();
        if (k == null) {
            log.warn("Komoran not available for repeated tokens extraction");
            return new HashMap<>();
        }

        KomoranResult result = k.analyze(text);
        List<Token> tokens = result.getTokenList();

        Map<String, Integer> morphFreq = new HashMap<>();

        for (Token token : tokens) {
            String morph = token.getMorph();
            if (morph != null && !morph.trim().isEmpty()) {
                morphFreq.put(morph, morphFreq.getOrDefault(morph, 0) + 1);
            }
        }

        Map<String, Integer> result_map = new HashMap<>();
        for (Map.Entry<String, Integer> entry : morphFreq.entrySet()) {
            if (entry.getValue() >= minCount) {
                result_map.put(entry.getKey(), entry.getValue());
            }
        }

        return result_map;
    }

    // =========================
    // 내부 유틸/정규화 로직
    // =========================

    private static boolean isVerbLike(String pos) {
        return pos.startsWith("VV") || pos.startsWith("VA");
    }

    /** VV/VA 병합 시 STOP 조건: EC(연결어미), ETM(관형형 어미) */
    private static boolean isStopForVerbMerge(String pos) {
        return pos != null && (pos.startsWith("EC") || pos.equals("ETM"));
    }

    /** 품사 헤드를 N/V/S로 매핑 */
    private static String headPos(String pos) {
        if (pos == null)
            return "N";
        if (pos.startsWith("NNG") || pos.startsWith("NNP"))
            return "N";
        if (pos.startsWith("VV") || pos.startsWith("VA"))
            return "V";
        if (pos.equals("SL") || pos.equals("SN"))
            return "S";
        return "N";
    }

    /** 의미 토큰만 유지 (명사/동사/형용사/외래어/숫자) */
    private static boolean isMeaningfulHead(String head) {
        return "N".equals(head) || "V".equals(head) || "S".equals(head);
    }

    /** 동사 기본형 normalize: 간단 불규칙/축약 케이스 */
    private static String normalizeVerbBase(String base, List<String> seenMorphs) {
        // 하 + 아/여 → 하다
        if ("하".equals(base))
            return "하";
        // 되 + 어 → 되다
        if ("되".equals(base))
            return "되";
        // 위하 + 아/어서 → 위하다
        if ("위하".equals(base))
            return "위하";
        return base;
    }

    /** VV/VA 덩어리를 기본형 '…다'로 병합하여 반환 */
    private static MergeResult mergeVerbLikeToLemma(List<Token> tokens, int startIdx) {
        Token first = tokens.get(startIdx);
        String base = first.getMorph();
        int begin = first.getBeginIndex();
        int end = first.getEndIndex();

        List<String> seen = new ArrayList<>();
        seen.add(base);

        int j = startIdx + 1;
        while (j < tokens.size()) {
            Token t = tokens.get(j);
            String p = t.getPos();
            String m = t.getMorph();

            if (isStopForVerbMerge(p))
                break; // EC/ETM에서 병합 중단

            // ⭐ MAG(부사) + VV/VA 병합 허용 ("잘 하다" → "잘하다")
            if (p != null && p.equals("MAG")) {
                seen.add(m);
                end = t.getEndIndex();
                j++;

                // 다음 토큰이 동사/형용사면 계속 병합
                if (j < tokens.size()) {
                    Token next = tokens.get(j);
                    if (next.getPos().startsWith("VV") || next.getPos().startsWith("VA")) {
                        seen.add(next.getMorph());
                        end = next.getEndIndex();
                        j++;
                    }
                }
                continue;
            }

            // 허용 병합: VX, EP, XS(V/A)
            if (p != null && (p.startsWith("VX") || p.equals("EP") || p.startsWith("XS"))) {
                seen.add(m);
                end = t.getEndIndex();
                j++;
                continue;
            }

            // 다음이 또 VV/VA면 중단
            if (p != null && (p.startsWith("VV") || p.startsWith("VA")))
                break;

            break;
        }

        // "잘" + "하다" → "잘하다"로 병합
        String lemma;
        if (seen.size() == 2 && seen.get(1).equals("하")) {
            lemma = seen.get(0) + "하다";
        } else {
            String lemmaBase = normalizeVerbBase(base, seen);
            lemma = lemmaBase + "다";
        }

        return new MergeResult(lemma, begin, end, j);
    }

    private static final class MergeResult {
        final String lemma;
        final int begin;
        final int end;
        final int nextIndex;

        MergeResult(String lemma, int begin, int end, int nextIndex) {
            this.lemma = lemma;
            this.begin = begin;
            this.end = end;
            this.nextIndex = nextIndex;
        }
    }

    // 반복 분석에 의미있는 토큰인지 판단
    public static Optional<String> normalizeForRepeat(Token t) {
        String pos = t.getPos();
        String morph = t.getMorph();
        if (pos == null || morph == null)
            return Optional.empty();

        // 제거 대상 품사
        if (pos.startsWith("J") || pos.startsWith("E") || pos.startsWith("S")
                || pos.startsWith("XS") || pos.equals("VX") || pos.equals("VCP")
                || pos.equals("MM")) {
            return Optional.empty();
        }
        if ("MAG".equals(pos)) {
            return Optional.of(morph.toLowerCase());
        }

        String normalized;

        // 명사/고유명사/외래어/숫자
        if (pos.startsWith("NNG") || pos.startsWith("NNP") || pos.equals("SL") || pos.equals("SN")) {
            normalized = morph.toLowerCase();
        }
        // 동사/형용사 → '다' 표제화
        else if (pos.startsWith("VV") || pos.startsWith("VA")) {
            normalized = morph.toLowerCase() + "다";
        } else {
            return Optional.empty();
        }

        return Optional.of(normalized);
    }

    // =========================
    // Debug
    // =========================

    private static void debugNormTokens(List<NormToken> out) {
        try {
            if (log.isInfoEnabled()) {
                log.info("Komoran Tokens: {}", buildTokenSummary(out, 5));
            }
        } catch (Exception ignored) {
        }

        if (Boolean.getBoolean("komoran.debug")) {
            System.out.println("Komoran Debug - NormTokens:");
            out.forEach(nt -> System.out.printf("[%d,%d] %s (%s)%n", nt.begin, nt.end, nt.norm, nt.posHead));
        }
    }

    private static String buildTokenSummary(List<NormToken> tokens, int topN) {
        if (tokens == null || tokens.isEmpty())
            return "(no tokens)";

        int total = tokens.size();
        List<String> norms = tokens.stream().map(nt -> nt.norm).toList();

        Map<String, Long> freq = norms.stream()
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));

        String top = freq.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));

        String sample = String.join(" ", norms.size() <= 10 ? norms : norms.subList(0, 10))
                + (norms.size() > 10 ? " ..." : "");

        return String.format("count=%d, top=[%s], sample=[%s]", total, top, sample);
    }
}

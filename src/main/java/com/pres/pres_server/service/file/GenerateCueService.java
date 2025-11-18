package com.pres.pres_server.service.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.CueCard.Mode;
import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.dto.file.*;
import com.pres.pres_server.repository.CueCardRepository;
import com.pres.pres_server.repository.PresentationFileRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerateCueService {

    private final RestTemplate restTemplate;
    private final ExtractTextService extractTextService;
    private final CueCardRepository cueCardRepository;
    private final PresentationFileRepository presentationFileRepository;
    private final CueSlideService cueSlideService;

    @Value("${openai.api.key}")
    private String OPENAI_API_KEY;

    // ObjectMapper 는 빈 주입 권장. 기존 new 유지 시 문제 없지만 설정 일관성 위해 빈으로 주입해도 됨.
    private final ObjectMapper objectMapper;

    // ======================= Public APIs =======================

    @Transactional
    public CueGenerationResponseDto generateCueCards(Long fileId, int maxSections) {
        if (fileId == null || fileId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 파일 ID입니다: " + fileId);
        }
        if (maxSections <= 0) {
            throw new IllegalArgumentException("maxSections must be positive: " + maxSections);
        }

        PresentationFile file = presentationFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다: " + fileId));

        ExtractedTextDto dto = extractTextService.getExtractedTextByFileId(fileId);
        List<String> slides = dto.getSlideTexts();
        List<Integer> insufficient = Optional.ofNullable(dto.getInsufficientSlides())
                .orElseGet(ArrayList::new);

        if (slides == null || slides.isEmpty()) {
            throw new IllegalStateException("추출된 슬라이드 텍스트가 없습니다: fileId=" + fileId);
        }

        List<Integer> failed = new ArrayList<>();
        Map<Integer, String> slideErrors = new LinkedHashMap<>();
        int successCount = 0;
        int totalSlides = slides.size();

        for (int i = 0; i < totalSlides; i++) {
            int slideNum = i + 1;
            try {
                if (insufficient.contains(slideNum)) {
                    cueSlideService.persistInsufficient(file, slideNum);
                    successCount++;
                    continue;
                }

                String prompt = buildCueCardPrompt(slides.get(i), slideNum, maxSections,
                        totalSlides);
                String json = callAiModel(prompt, maxSections);
                CueSlideDto slideDto = parseToCueSlideDto(json, slideNum, maxSections);

                // 저장 (REQUIRES_NEW)
                cueSlideService.processSlide(file, slideDto);
                successCount++;
            } catch (Exception e) {

                failed.add(slideNum);
                String msg = Optional.ofNullable(e.getMessage()).orElse("원인 불명 오류");
                if (msg.length() > 300)
                    msg = msg.substring(0, 300) + "...";
                slideErrors.put(slideNum, msg);
                log.error("슬라이드 {} 처리 실패: {}", slideNum, e.getMessage(), e);
            }
        }
        List<CueCard> entities = cueCardRepository
                .findByPresentationFile_FileIdOrderBySlideNumberAscModeAscSectionNumberAsc(fileId);
        // qr slug/url 세팅 및 변경된 엔티티 저장
        generateQrMeta(entities);

        log.info("완료: 총 {}슬라이드 중 {}개 실패", totalSlides, failed.size());
        // toDto() 호출 삭제, 간단한 응답만 반환
        return CueGenerationResponseDto.builder()
                .success(failed.isEmpty())
                .message(failed.isEmpty()
                        ? "큐카드가 성공적으로 생성되었습니다."
                        : String.format("큐카드 생성 완료 (일부 실패: %d개)", failed.size()))
                .totalSlides(totalSlides)
                .successCount(successCount)
                .failureCount(failed.size())
                .errors(slideErrors.isEmpty() ? null : slideErrors)
                .build();
    }

    // 조회용 메서드, qr 미포함 반환
    public CueCardDto getCueCardsByFileId(Long fileId) {
        if (fileId == null || fileId <= 0)
            throw new IllegalArgumentException("유효하지 않은 파일 ID입니다: " + fileId);

        List<CueCard> cards = cueCardRepository
                .findByPresentationFile_FileIdOrderBySlideNumberAscModeAscSectionNumberAsc(fileId);
        if (cards.isEmpty())
            throw new IllegalArgumentException("해당 파일의 큐카드가 존재하지 않습니다: " + fileId);

        return toDto(fileId, cards, Collections.emptyMap(), false);
    }

    // 조회용 메서드, qr 포함 반환
    public CueCardDto getCueCardsWithQrByFileId(Long fileId) {
        if (fileId == null || fileId <= 0)
            throw new IllegalArgumentException("유효하지 않은 파일 ID입니다: " + fileId);

        List<CueCard> cards = cueCardRepository
                .findByPresentationFile_FileIdOrderBySlideNumberAscModeAscSectionNumberAsc(fileId);
        if (cards.isEmpty())
            throw new IllegalArgumentException("해당 파일의 큐카드가 존재하지 않습니다: " + fileId);

        return toDto(fileId, cards, Collections.emptyMap(), true);
    }

    private void generateQrMeta(List<CueCard> cueCards) {
        // 슬라이드별로 그룹핑
        Map<Integer, List<CueCard>> bySlide = cueCards.stream()
                .collect(Collectors.groupingBy(CueCard::getSlideNumber));

        for (Map.Entry<Integer, List<CueCard>> entry : bySlide.entrySet()) {
            List<CueCard> list = entry.getValue();

            // 슬라이드의 대표(ADVANCED) 1건만 QR 부여: 섹션 번호가 가장 낮은 ADVANCED
            Optional<CueCard> advOpt = list.stream()
                    .filter(c -> c.getMode() == Mode.ADVANCED)
                    .sorted(Comparator.comparing(c -> Optional.ofNullable(c.getSectionNumber()).orElse(0)))
                    .findFirst();

            if (advOpt.isEmpty())
                continue; // 없다면 패스(정책에 따라 생성해도 됨)

            CueCard adv = advOpt.get();
            if (adv.getQrSlug() != null && !adv.getQrSlug().isBlank())
                continue;

            String slug;
            int tries = 0;
            do {
                slug = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                tries++;
            } while (cueCardRepository.findByQrSlug(slug).isPresent() && tries < 3);

            if (tries >= 3)
                throw new IllegalStateException("QR slug 충돌 다중 발생");

            adv.setQrSlug(slug);
        }

        // 변경된 ADV 엔티티만 모아서 명시적으로 저장
        List<CueCard> modified = cueCards.stream()
                .filter(c -> c.getMode() == CueCard.Mode.ADVANCED)
                .filter(c -> c.getQrSlug() != null)
                .toList();

        if (!modified.isEmpty()) {
            cueCardRepository.saveAll(modified);
        }
    }

    /**
     * 엔티티 → 구조화 DTO 변환 메서드
     */
    private CueCardDto toDto(Long fileId, List<CueCard> entities,
            Map<Integer, String> slideErrors, boolean includeQr) {
        Map<Integer, List<CueCard>> bySlide = entities.stream()
                .collect(Collectors.groupingBy(CueCard::getSlideNumber, TreeMap::new, Collectors.toList()));

        List<CueSlideDto> slides = new ArrayList<>();

        for (Map.Entry<Integer, List<CueCard>> e : bySlide.entrySet()) {
            int slide = e.getKey();
            List<CueCard> items = e.getValue();

            CueSlideDto s = new CueSlideDto();
            s.setSlideNumber(slide);

            // BASIC
            List<CueBasicDto> basic = items.stream()
                    .filter(c -> c.getMode() == Mode.BASIC)
                    .sorted(Comparator.comparing(c -> Optional.ofNullable(c.getSectionNumber()).orElse(0)))
                    .map(c -> {
                        CueBasicDto b = new CueBasicDto();
                        b.setCueId(c.getCueId());
                        b.setSection(Optional.ofNullable(c.getSectionNumber()).orElse(0));
                        b.setKeyword(Optional.ofNullable(c.getSectionKeyword()).orElse(""));
                        b.setText(Optional.ofNullable(c.getContent()).orElse(""));
                        return b;
                    }).toList();
            s.setBasic(basic);

            // BASIC 맵/인덱스
            Map<Integer, CueBasicDto> basicBySec = basic.stream()
                    .collect(Collectors.toMap(CueBasicDto::getSection, x -> x, (a, b) -> a));
            List<Integer> basicIndexOrdered = basic.stream()
                    .map(CueBasicDto::getSection).sorted().toList();

            // ADVANCED 섹션(섹션/키워드는 BASIC에 동기화)
            Map<Integer, CueAdvancedDto> advBySec = items.stream()
                    .filter(c -> c.getMode() == Mode.ADVANCED)
                    .collect(Collectors.toMap(
                            c -> Optional.ofNullable(c.getSectionNumber()).orElse(0),
                            c -> {
                                CueAdvancedDto a = new CueAdvancedDto();
                                a.setCueId(c.getCueId());
                                a.setSection(Optional.ofNullable(c.getSectionNumber()).orElse(0));
                                a.setText(Optional.ofNullable(c.getContent()).orElse(""));
                                return a;
                            },
                            (a, b) -> a));

            List<CueAdvancedDto> advancedList = new ArrayList<>();
            for (Integer idx : basicIndexOrdered) {
                CueAdvancedDto a = advBySec.get(idx);
                if (a == null) {
                    a = new CueAdvancedDto();
                    a.setSection(idx);
                    a.setText(""); // 누락 보강
                }
                String kw = Optional.ofNullable(basicBySec.get(idx))
                        .map(CueBasicDto::getKeyword).orElse("");
                a.setKeyword(kw);
                advancedList.add(a);
            }
            s.setAdvanced(advancedList);

            // QR: 섹션 번호가 가장 낮은 ADVANCED의 QR 사용
            if (includeQr) {
                Optional<CueCard> advFirst = items.stream()
                        .filter(c -> c.getMode() == Mode.ADVANCED)
                        .sorted(Comparator.comparing(c -> Optional.ofNullable(c.getSectionNumber()).orElse(0)))
                        .findFirst();
                s.setQrSlug(advFirst.map(CueCard::getQrSlug).orElse(null));
            } else {
                s.setQrSlug(null);
            }
            // s.setAdvanced(advancedList);
            slides.add(s);
        }

        CueCardDto dto = new CueCardDto();
        dto.setFileId(fileId);
        dto.setSlides(slides);
        dto.setErrors(slideErrors == null ? Collections.emptyMap() : slideErrors);
        return dto;
    }

    /**
     * OpenAI 호출: JSON Schema를 maxSections에 맞춰 강제
     */
    private String callAiModel(String prompt, int maxSections) {
        if (prompt == null || prompt.trim().isEmpty())
            throw new IllegalArgumentException("프롬프트가 비어있습니다.");

        String url = "https://api.openai.com/v1/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(OPENAI_API_KEY);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o-mini");
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content",
                        "너는 발표 큐카드를 JSON으로만 생성한다. " +
                                "설명/예시/마크다운을 절대 출력하지 말고, 유효한 JSON 한 덩어리만 출력하라."),
                Map.of("role", "user", "content", prompt)));
        requestBody.put("temperature", 0.5);
        requestBody.put("max_tokens", 2000);
        requestBody.put("response_format", buildResponseFormat(maxSections));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null)
                throw new RuntimeException("OpenAI API 응답이 비어있습니다.");

            if (body.containsKey("error")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> error = (Map<String, Object>) body.get("error");
                throw new RuntimeException("OpenAI API 오류 [" + error.get("type") + "]: " + error.get("message"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
            if (choices == null || choices.isEmpty())
                throw new RuntimeException("OpenAI API 응답에 choices가 없습니다.");

            Map<String, Object> firstChoice = choices.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");

            if (message == null)
                throw new RuntimeException("OpenAI API 응답에 message가 없습니다.");

            // String content = (String) message.get("content");
            // if (content == null || content.trim().isEmpty()) throw new
            // RuntimeException("OpenAI API 응답 content가 비어있습니다.");

            Object contentAny = message.get("content");
            String content;
            if (contentAny instanceof String s) {
                content = s;
            } else if (contentAny instanceof List<?> parts) {
                StringBuilder sb = new StringBuilder();
                for (Object p : parts) {
                    if (p instanceof Map<?, ?> m) {
                        Object t = m.get("text");
                        if (t instanceof String ts)
                            sb.append(ts);
                    }
                }
                content = sb.toString();
            } else if (message.get("parsed") instanceof String ps) {
                content = ps;
            } else {
                throw new RuntimeException("지원하지 않는 응답 포맷");
            }

            content = stripCodeFence(content);
            if (content == null || content.trim().isEmpty())
                throw new RuntimeException("OpenAI API 응답 content가 비어있습니다.");
            return content.trim();

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("OpenAI API 클라이언트 오류 [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("AI 서비스 요청 오류: " + e.getMessage());
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("OpenAI API 서버 오류 [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("AI 서비스 일시적 오류: " + e.getMessage());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("OpenAI API 연결 오류: {}", e.getMessage());
            throw new RuntimeException("AI 서비스 연결 실패: " + e.getMessage());
        } catch (Exception e) {
            log.error("큐카드 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("큐카드 생성에 실패했습니다: " + e.getMessage());
        }
    }

    private String stripCodeFence(String s) {
        if (s == null)
            return "";
        String x = s.trim();
        if (x.startsWith("```")) {
            int idx = x.indexOf('\n');
            if (idx > 0)
                x = x.substring(idx + 1);
            if (x.endsWith("```"))
                x = x.substring(0, x.length() - 3);
        }
        return x.trim();
    }

    /**
     * response_format JSON Schema (maxSections 반영)
     */
    private Map<String, Object> buildResponseFormat(int maxSections) {
        Map<String, Object> sectionItemSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "index", Map.of("type", "integer", "minimum", 1, "maximum", maxSections),
                        "keyword", Map.of("type", "string", "minLength", 1),
                        "text", Map.of("type", "string", "minLength", 1)),
                "required", List.of("index", "keyword", "text"),
                "additionalProperties", false);

        Map<String, Object> basicSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "sections", Map.of(
                                "type", "array",
                                "minItems", 1,
                                "maxItems", maxSections,
                                "items", sectionItemSchema)),
                "required", List.of("sections"),
                "additionalProperties", false);
        // keyword는 basic과 동일
        Map<String, Object> sectionItemSchemaAdvanced = Map.of(
                "type", "object",
                "properties", Map.of(
                        "index", Map.of("type", "integer", "minimum", 1, "maximum", maxSections),
                        "text", Map.of("type", "string", "minLength", 1)),
                "required", List.of("index", "text"),
                "additionalProperties", false);

        Map<String, Object> advancedSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "sections", Map.of(
                                "type", "array",
                                "minItems", 1,
                                "maxItems", maxSections,
                                "items", sectionItemSchemaAdvanced)),
                "required", List.of("sections"),
                "additionalProperties", false);

        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "slide", Map.of("type", "integer", "minimum", 1),
                        "basic", basicSchema,
                        "advanced", advancedSchema),
                "required", List.of("slide", "basic", "advanced"),
                "additionalProperties", false);

        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "CueCardSchema",
                        "schema", schema,
                        "strict", true));
    }

    /**
     * AI JSON → CueSlideDto로 정제
     * - slide 번호는 expectedSlide로 강제
     * - BASIC: 섹션 필수(1..max), keyword/text 필수, 중복 인덱스는 최초 1개만 채택
     * - ADVANCED: 섹션/text만 입력 받음(keyword는 입력 안 받음); BASIC과 동일 섹션 집합으로 강제 정렬
     * · 누락된 섹션은 빈 텍스트("")로 보강
     * · keyword는 BASIC의 동일 섹션 keyword로 주입
     */
    private CueSlideDto parseToCueSlideDto(String json, int expectedSlide, int maxSections) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        // slide 번호는 모델 값을 무시하고 기대값으로 강제
        int slide = expectedSlide;

        JsonNode sections = root.path("basic").path("sections");
        if (!sections.isArray() || sections.size() == 0)
            throw new IllegalStateException("basic.sections 비어있음");

        Set<Integer> basicSeen = new HashSet<>();
        List<CueBasicDto> basicList = new ArrayList<>();

        for (JsonNode s : sections) {
            int idx = s.path("index").asInt(-1);
            String kw = s.path("keyword").asText("");
            String tx = s.path("text").asText("");

            if (idx < 1 || idx > maxSections)
                continue;
            if (kw == null || kw.isBlank())
                continue; // ✅ BASIC 키워드 필수
            if (tx == null || tx.isBlank())
                continue; // ✅ BASIC 텍스트 필수
            if (!basicSeen.add(idx))
                continue;

            CueBasicDto b = new CueBasicDto();
            b.setSection(idx);
            b.setKeyword(kw == null ? "" : kw.trim());
            b.setText(tx.trim());
            basicList.add(b);
        }
        basicList.sort(Comparator.comparingInt(CueBasicDto::getSection));
        if (basicList.isEmpty())
            throw new IllegalStateException("유효 섹션 없음");

        // BASIC 맵/인덱스 집합
        Map<Integer, CueBasicDto> basicBySec = basicList.stream()
                .collect(Collectors.toMap(CueBasicDto::getSection, x -> x, (a, b) -> a));
        List<Integer> basicIndexOrdered = basicList.stream()
                .map(CueBasicDto::getSection)
                .sorted()
                .toList();

        JsonNode advSectionsNode = root.path("advanced").path("sections");
        Map<Integer, CueAdvancedDto> advBySec = new HashMap<>();

        if (advSectionsNode.isArray() && advSectionsNode.size() > 0) {
            Set<Integer> advSeen = new HashSet<>();
            for (JsonNode s : advSectionsNode) {
                int idx = s.path("index").asInt(-1);
                String tx = s.path("text").asText("");

                if (idx < 1 || idx > maxSections)
                    continue;
                if (tx == null || tx.isBlank())
                    continue;
                if (!advSeen.add(idx))
                    continue;

                CueAdvancedDto a = new CueAdvancedDto();
                a.setSection(idx);
                a.setText(tx.trim());
                advBySec.put(idx, a); // 인덱스별로 바로 접근 가능
            }
        }

        List<CueAdvancedDto> advancedList = new ArrayList<>();
        for (Integer idx : basicIndexOrdered) {
            CueAdvancedDto a = advBySec.get(idx);
            if (a == null) {
                a = new CueAdvancedDto();
                a.setSection(idx);
                a.setText("");
            }
            String kw = Optional.ofNullable(basicBySec.get(idx))
                    .map(CueBasicDto::getKeyword).orElse("");
            a.setKeyword(kw == null ? "" : kw.trim());
            advancedList.add(a);
        }

        CueSlideDto dto = new CueSlideDto();
        dto.setSlideNumber(slide);
        dto.setBasic(basicList);
        dto.setAdvanced(advancedList);
        return dto;
    }
    // ======================= Prompt Builder =======================

    private String buildCueCardPrompt(String slideText, int slideNumber, int maxSections,
            int totalSlides) {
        return """
                너는 대학생 발표자료에서 발표자가 사용할 발표 대본과 요약 큐카드를 생성하는 전문가다.
                출력은 반드시 JSON 형식으로만 하며, JSON 외의 설명문이나 텍스트를 포함하지 말라.

                출력 형식(JSON Schema)
                {
                  "slide": 슬라이드 번호 (정수),
                  "basic": {
                    "sections": [
                      {
                        "index": 섹션 번호 (1~%d),
                        "keyword": 섹션 핵심 키워드,
                        "text": 섹션에 해당하는 발표 대본 텍스트 (2~4문장, 공식체)
                      }
                    ]
                  },
                  "advanced": {
                    "sections": [
                    {
                    "index": 섹션 번호 (1~%d),
                    "text": "해당 슬라이드의 핵심 주제를 한 문장으로 요약한 문장"
                    }
                  ]
                }

                ---

                [핵심 제약사항]
                - 이 텍스트는 슬라이드 %d번 하나의 슬라이드이다.
                - 반드시 slide = %d 로 설정하고, 다른 슬라이드 번호(예: %d, %d)는 절대 사용하지 않는다.
                - 한 슬라이드 안에서만 큐카드를 생성한다.
                - 반드시 입력된 슬라이드 텍스트의 실제 내용을 기반으로 작성한다.

                ---

                [기본버전 작성 규칙]
                - 슬라이드 텍스트와 구조를 참고하여, 이 페이지(슬라이드)에 대한 발표자가 읽을 수 있는 발표 대본을 작성하라.
                - 문장은 자연스럽고 논리적인 흐름을 갖추되, 단정적이고 공식적인 발표체(“~입니다”, “~합니다”)를 사용하라.
                - 구어체(“~거든요”, “~해요”, “~같습니다”)는 사용하지 마라.
                - 슬라이드 제목만을 주제로 삼거나, 파일명으로 내용을 유추하지 마라.
                - 발표자가 실제로 말하지 않을 내용(예: “이 슬라이드는 ~를 보여줍니다” 등)은 포함하지 마라.
                - 표지(1번 슬라이드)에서는 인사와 발표 주제를 간단히 안내하는 수준으로 작성하라.
                - OCR 인식이 불가능하거나 텍스트가 거의 없는 슬라이드는 “(OCR 인식 불가 - 요약 생략)”을 포함하라.
                - 발표 흐름이 자연스럽게 이어질 수 있도록, 각 슬라이드 마지막 문장은 다음 내용을 예고하거나 적절한 연결 어미로 마무리하라.

                ---

                [비언어적 표현 규칙]
                - 발표자가 활용할 수 있도록 아래 비언어적 표현 아이콘을 적절한 위치(강조, 전환, 호흡 등)에 배치하라.
                  사용 가능한 아이콘:
                    <🔍 청중 바라보기>
                    <📄 발표자료 보기>
                    <✋ 제스처>
                    <👉 화면 가리키기>
                    <🌬 호흡>
                - 아이콘은 실제 발표 흐름에 어울리는 위치에 자연스럽게 삽입해야 하며, 반드시 위 예시처럼 <>로 감싸서 출력하라.
                  예시:
                    - 문단 첫 문장 앞: <🌬 호흡>, <🔍 청중 바라보기>
                    - 중요한 정보 뒤: <👉 화면 가리키기>, <✋ 제스처>
                    - 주제 전환 시: <🌬 호흡>, <📄 발표자료 보기>

                ---

                [기본버전 세부 분할 규칙]
                - 하나의 슬라이드 안에서도 주요 소제목이나 핵심 키워드 단위로 내용을 분리하라.
                - 각 구간은 발표자가 자연스럽게 말할 수 있는 2~4문장 내외로 작성하라.
                - 각 문단마다 비언어적 표현 아이콘을 적절히 삽입하라.
                - 최대 섹션 개수는 %d개를 넘지 않는다.
                - 초과할 경우 상위 %d개만 포함하라.
                - 본문(text)에는 섹션 제목/키워드/번호를 포함하지 않는다. (예: "#3", "[키워드]" 금지)
                - 본문(text)에는 해시(#), 대괄호([]), 섹션 번호/제목을 출력하지 말라.

                ---
                [대본 작성 형식 참고]
                            입력 예: "연구 방법론 - 데이터 수집 방식, 분석 기법, 검증 절차"

                            대본 형식 (각 섹션의 keyword와 text):

                            #1 [첫번째 주제]
                            [슬라이드의 첫 번째 핵심 내용]에 대해 설명드리겠습니다. <🌬 호흡>
                            [구체적인 설명 2~3문장]. <🔍 청중 바라보기>
                            [다음 단계로의 연결]. <👉 화면 가리키기>

                            #2 [두번째 주제]
                            [두 번째 핵심 개념]은 [구체적 설명]입니다.
                            이는 [중요성/의미]를 높이는 데 중요합니다. <✋ 제스처>
                            [다음 내용과의 연결]. <🌬 호흡>

                            #3 [세번째 주제]
                            [세 번째 내용]을 통해 [기대 결과]를 확인할 수 있습니다.
                            [추가 설명]. <🔍 청중 바라보기>
                            [마무리 또는 다음 슬라이드 예고]. <🌬 호흡>

                            ⚠️ 주의: 위는 구조 참고용이며, 반드시 입력된 슬라이드의 실제 내용을 사용할 것.
                            대괄호 [] 안의 내용은 실제 슬라이드 텍스트의 구체적 내용으로 채워야 함.
                            예시에 나온 "연구 방법론", "데이터 수집" 같은 단어를 그대로 쓰지 말 것.
                ---

                [심화버전 작성 규칙]
                - advanced.sections 는 basic.sections 의 index를 그대로 사용한다. (개수와 인덱스 집합 동일)
                - keyword는 출력하지 않는다. (서버가 basic의 동일 섹션 keyword로 보강한다)
                - advanced.sections 는 basic.sections 의 index를 그대로 사용한다. (개수와 인덱스 집합 누락/추가 금지)
                - advanced.sections[i].text 는 **basic.sections[i]의 내용만** 한 문장으로 요약한다. (슬라이드 전체 요약 금지)
                - 각 advanced.text 는 해당 섹션의 keyword/본문을 반영한 1문장 요약이어야 한다.
                - 요약문은 간결하고, 핵심 키워드만 포함하라.
                - 주요 메시지를 빠르게 파악할 수 있도록 선언문 또는 설명문 형태로 작성하라.
                - 비언어적 표현 아이콘은 advanced에 절대 포함하지 않는다.
                - 불필요한 부연 설명은 피하고, 문서의 핵심 논지에 집중하라.
                - 표지(1번 슬라이드)는 “이번 발표의 목적”만 간단히 설명하라.
                - OCR 인식이 불가능한 경우 “(OCR 인식 불가 – 요약 생략)”을 포함하라.

                ---

                [슬라이드 맥락 규칙]
                - 이 슬라이드는 전체 중 %d/%d 번째이다.
                - 표지(슬라이드 1): 인사/주제 소개만. 팀 소개는 표지에 명시적으로 있을 때만 한 줄 언급.
                - 마지막 슬라이드(슬라이드 %d): 감사/결론/다음 단계만. 팀 소개/세부 기능 소개 금지.

                ---

                [출력 규칙]
                - JSON만 출력한다. 텍스트나 예시 문장은 절대 포함하지 않는다.
                - slide = %d 로 설정한다.
                - basic.sections 배열에는 최대 %d개의 섹션을 포함한다.
                - 각 section에는 index, keyword, text 필드가 반드시 존재해야 한다.
                - advanced.text는 반드시 존재해야 하며 비어 있으면 안 된다.
                - 반드시 입력된 슬라이드 텍스트의 실제 내용을 기반으로 작성한다.


                ---

                [금지사항]
                - JSON 외 텍스트 출력 금지
                - 예시 문장, 설명문, 마크다운, 따옴표 이외의 형식 사용 금지
                - slide 번호 오기입 금지 (반드시 %d)
                - 여러 슬라이드 내용 병합 금지
                - section 필드 누락 금지
                - advanced.text 누락 금지
                - 입력되지 않은 내용을 임의로 생성하는 것 금지
                - 예시 단어("연구 방법론", "데이터 수집" 등)를 그대로 사용하는 것 금지

                ---

                [입력 슬라이드 텍스트]
                %s
                """.formatted(
                maxSections, // 1. line 12: (1~%d)
                maxSections, // 2. line 21: (1~%d)
                slideNumber, // 3. line 30: 슬라이드 %d번
                slideNumber, // 4. line 31: slide = %d
                slideNumber + 1, // 5. line 31: 예: %d
                slideNumber - 1, // 6. line 31: 예: %d
                maxSections, // 7. line 66: 최대 %d개
                maxSections, // 8. line 67: 상위 %d개
                slideNumber, // 9. line 114: slide = %d
                maxSections, // 10. line 115: 최대 %d개
                slideNumber, // 11. line 127: 반드시 %d
                slideNumber, // 12 "전체 중 %d/%d 번째"의 현재 번호
                totalSlides, // 13 "전체 중 %d/%d 번째"의 전체 개수
                totalSlides, // 14 "마지막 슬라이드(슬라이드 %d)"
                slideText // 15. line 138: %s
        );
    }
}

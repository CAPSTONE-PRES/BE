package com.pres.pres_server.service.file;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.pres.pres_server.dto.CueCardDto;
import com.pres.pres_server.dto.ExtractedTextDto;
import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.repository.CueCardRepository;
import com.pres.pres_server.repository.PresentationFileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerateCueService {
    @Value("${openai.api.key}")
    private String OPENAI_API_KEY;

    private final RestTemplate restTemplate;
    private final ExtractTextService extractTextService;
    private final CueCardRepository cueCardRepository;
    private final PresentationFileRepository presentationFileRepository;

    @Transactional
    public CueCardDto generateCueCards(Long fileId) {
        // 입력 값 검증
        if (fileId == null || fileId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 파일 ID입니다: " + fileId);
        }

        // 중복 생성 방지: 이미 생성 중인지 확인 (간단한 예시)
        log.info("파일 ID {}의 큐카드 생성을 시작합니다.", fileId);

        try {
            // 검증된 텍스트 정보 가져오기
            ExtractedTextDto extractedTextDto = extractTextService.getExtractedTextByFileId(fileId);
            List<String> slideTexts = extractedTextDto.getSlideTexts();
            List<Integer> insufficientSlides = extractedTextDto.getInsufficientSlides();

            if (slideTexts == null || slideTexts.isEmpty()) {
                throw new IllegalStateException("파일 ID " + fileId + "에 대한 추출된 텍스트가 없습니다.");
            }

            // 디버깅: 슬라이드 개수 확인
            log.info("총 슬라이드 개수: {}", slideTexts.size());
            if (insufficientSlides != null) {
                log.info("텍스트 부족 슬라이드: {}", insufficientSlides);
            }

            List<String> cueCards = new ArrayList<>();
            List<String> failedSlides = new ArrayList<>();

            for (int i = 0; i < slideTexts.size(); i++) {
                int slideNumber = i + 1;
                String slideText = slideTexts.get(i);
                log.info("슬라이드 {} 텍스트 길이: {}", slideNumber, slideText.length());

                try {
                    // 이미 검증된 결과 활용 - TextValidationService에서 부족하다고 판단된 슬라이드
                    if (insufficientSlides != null && insufficientSlides.contains(slideNumber)) {
                        cueCards.add(
                                "슬라이드 " + slideNumber + "\n[기본버전]\n(내용 부족 - 요약 생략)\n\n[심화버전]\n(내용 부족 – 요약 생략)");
                        continue;
                    }

                    // 정상 텍스트는 큐카드 생성
                    String prompt = buildCueCardPrompt(slideText, slideNumber);
                    String cueCard = callAiModel(prompt);

                    if (cueCard == null || cueCard.trim().isEmpty()) {
                        throw new RuntimeException("AI 모델에서 빈 응답을 받았습니다.");
                    }

                    cueCards.add(cueCard);
                } catch (Exception e) {
                    log.error("슬라이드 {} 큐카드 생성 실패: {}", slideNumber, e.getMessage());
                    failedSlides.add("슬라이드 " + slideNumber);
                    // 실패한 슬라이드는 기본 메시지로 대체
                    cueCards.add("슬라이드 " + slideNumber + "\n[기본버전]\n큐카드 생성에 실패했습니다. 다시 시도해주세요.\n\n[심화버전]\n큐카드 생성 실패");
                }
            }

            // 실패한 슬라이드가 있다면 경고 로그
            if (!failedSlides.isEmpty()) {
                log.warn("파일 ID {}에서 실패한 슬라이드: {}", fileId, String.join(", ", failedSlides));
            }

            // DB에 저장
            saveCueCardsToDatabase(fileId, cueCards);

            CueCardDto cueCardDto = new CueCardDto();
            cueCardDto.setFileId(fileId);
            cueCardDto.setCueCards(cueCards);

            log.info("파일 ID {}의 큐카드 생성이 완료되었습니다.", fileId);
            return cueCardDto;

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("파일 ID {}의 큐카드 생성 실패 (입력 오류): {}", fileId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("파일 ID {}의 큐카드 생성 중 예상치 못한 오류 발생: {}", fileId, e.getMessage(), e);
            throw new RuntimeException("큐카드 생성 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    // 큐카드를 DB에 저장 (Update 방식)
    @Transactional
    private void saveCueCardsToDatabase(Long fileId, List<String> cueCards) {
        try {
            // PresentationFile 조회
            PresentationFile presentationFile = presentationFileRepository.findById(fileId)
                    .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다: " + fileId));

            // 기존 큐카드 조회
            List<CueCard> existingCueCards = cueCardRepository.findByPresentationFile_FileIdOrderBySlideNumber(fileId);

            int updateCount = 0;
            int createCount = 0;

            // 큐카드 업데이트 또는 생성
            for (int i = 0; i < cueCards.size(); i++) {
                int slideNumber = i + 1;
                String content = cueCards.get(i);

                if (content == null || content.trim().isEmpty()) {
                    log.warn("슬라이드 {}의 큐카드 내용이 비어있습니다.", slideNumber);
                    content = "큐카드 내용이 생성되지 않았습니다.";
                }

                // 기존 큐카드가 있으면 업데이트, 없으면 생성
                CueCard cueCard = existingCueCards.stream()
                        .filter(c -> c.getSlideNumber() == slideNumber)
                        .findFirst()
                        .orElse(null);

                if (cueCard != null) {
                    // 기존 큐카드 업데이트
                    cueCard.setContent(content);
                    updateCount++;
                } else {
                    // 새 큐카드 생성
                    cueCard = new CueCard();
                    cueCard.setPresentationFile(presentationFile);
                    cueCard.setSlideNumber(slideNumber);
                    cueCard.setContent(content);
                    createCount++;
                }

                cueCardRepository.save(cueCard);
            }

            // 슬라이드 수가 줄어든 경우, 여분의 큐카드 삭제
            int deleteCount = 0;
            for (CueCard existingCard : existingCueCards) {
                if (existingCard.getSlideNumber() > cueCards.size()) {
                    cueCardRepository.delete(existingCard);
                    deleteCount++;
                }
            }

            log.info("파일 ID {}의 큐카드 처리 완료 - 업데이트: {}, 생성: {}, 삭제: {}",
                    fileId, updateCount, createCount, deleteCount);

        } catch (Exception e) {
            log.error("파일 ID {}의 큐카드 DB 저장 실패: {}", fileId, e.getMessage(), e);
            throw new RuntimeException("큐카드 저장 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    // DB에서 큐카드 조회
    public CueCardDto getCueCardsByFileId(Long fileId) {
        // 입력 값 검증
        if (fileId == null || fileId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 파일 ID입니다: " + fileId);
        }

        try {
            List<CueCard> savedCueCards = cueCardRepository.findByPresentationFile_FileIdOrderBySlideNumber(fileId);

            if (savedCueCards.isEmpty()) {
                throw new IllegalArgumentException("해당 파일의 큐카드가 존재하지 않습니다: " + fileId);
            }

            List<String> cueCardContents = savedCueCards.stream()
                    .map(CueCard::getContent)
                    .filter(content -> content != null && !content.trim().isEmpty())
                    .toList();

            if (cueCardContents.isEmpty()) {
                throw new IllegalStateException("해당 파일의 큐카드 내용이 모두 비어있습니다: " + fileId);
            }

            CueCardDto cueCardDto = new CueCardDto();
            cueCardDto.setFileId(fileId);
            cueCardDto.setCueCards(cueCardContents);

            log.info("파일 ID {}의 큐카드 {} 개를 조회했습니다.", fileId, cueCardContents.size());
            return cueCardDto;

        } catch (IllegalArgumentException e) {
            log.warn("파일 ID {}의 큐카드 조회 실패: {}", fileId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("파일 ID {}의 큐카드 조회 중 예상치 못한 오류: {}", fileId, e.getMessage(), e);
            throw new RuntimeException("큐카드 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    // 프롬프트 생성 (슬라이드 번호 포함)
    private String buildCueCardPrompt(String slideText, int slideNumber) {
        return "너는 대학생 발표자료에서 발표자가 사용할 발표 대본과 요약 큐카드를 생성하는 전문가야.\n\n" +
                "⚠️ 핵심 제약사항 ⚠️\n" +
                "- 이 텍스트는 슬라이드 " + slideNumber + "번 하나의 슬라이드입니다.\n" +
                "- 반드시 슬라이드 " + slideNumber + "번에 대한 큐카드만 생성하세요.\n" +
                "- 다른 슬라이드 번호(슬라이드 " + (slideNumber + 1) + ", 슬라이드 " + (slideNumber - 1) + " 등)를 절대 만들지 마세요.\n\n" +
                "아래 슬라이드별 텍스트를 참고해, 반드시 아래 지침과 예시를 따라 발표자료 대본을 작성해 줘.\n\n" +
                "---\n" +
                "[지침]\n" +
                "[0] 슬라이드 번호 고정\n" +
                "- 이 텍스트는 슬라이드 " + slideNumber + "번입니다. 반드시 \"슬라이드 " + slideNumber + "\"로 시작하고 끝내세요.\n" +
                "- 다른 슬라이드 번호는 절대 사용하지 마세요.\n\n" +
                "[1] 기본버전\n" +
                "- 슬라이드의 텍스트와 구조를 참고하여, 이 페이지(슬라이드)에 대한 발표자가 읽을 수 있는 발표 대본을 작성하세요.\n" +
                "- 문장은 자연스럽고 논리적인 흐름을 갖추되, 단정적이고 공식적인 발표체(예: \"~입니다\", \"~합니다\")를 사용하세요.\n" +
                "- 구어체(예: \"~거든요\", \"~해요\", \"~같습니다\")는 사용하지 마세요.\n" +
                "- 슬라이드 제목만을 주제로 삼거나, 파일명으로 내용을 유추하지 마세요.\n" +
                "- 발표자가 실제로 말하지 않을 내용(예: \"이 슬라이드는 ~를 보여줍니다\" 등 슬라이드 설명이나 화면 안내)은 포함하지 마세요.\n" +
                "- 표지(1번 슬라이드)에서는 인사와 발표 주제를 간단하게 안내하는 수준으로만 작성하세요.\n" +
                "- OCR 인식이 불가능하거나 텍스트가 거의 없는 슬라이드는 아래와 같이 작성하세요:\n" +
                "    [기본버전] (OCR 인식 불가 - 요약 생략)\n" +
                "- 발표 흐름이 자연스럽게 이어질 수 있도록, 각 슬라이드 마지막 문장은 다음 내용을 예고하거나, 적절한 연결 어미로 마무리하세요.\n" +
                "- 발표자가 활용할 수 있도록 아래 비언어적 표현 아이콘을 적절한 위치(강조, 전환, 호흡 등)에 배치하세요:\n" +
                "    🔍 청중 바라보기\n" +
                "    📄 발표자료 보기\n" +
                "    ✋ 제스처\n" +
                "    👉 화면 가리키기\n" +
                "    🌬 호흡\n" +
                "    ❓ 질의응답\n" +
                "- 아이콘은 실제 발표 흐름에 어울리는 위치에 자연스럽게 삽입하세요.\n" +
                "    예시: 슬라이드 첫 문장 앞(호흡/청중 바라보기), 중요한 정보 뒤(화면 가리키기/제스처), 주제 전환 시(호흡/자료 보기) 등\n\n" +
                "[2] 심화버전\n" +
                "- 해당 슬라이드의 핵심 주제를 한문장으로 논리적으로 요약하세요.\n" +
                "- 요약문은 간단한 문장으로, **핵심 키워드만** 포함하여 작성하세요.\n" +
                "- 주요 메시지를 빠르게 파악할 수 있도록, 선언문 또는 설명문 형태로 간결하게 작성하세요.\n" +
                "- 불필요한 부연설명은 피하고, 문서의 핵심 논지에 집중하세요.\n" +
                "- 표지(1번 슬라이드)는 '이번 발표의 목적'만 간결하게 설명하세요.\n" +
                "- OCR 인식이 불가능한 경우 아래처럼 작성하세요:\n" +
                "    [심화버전] (OCR 인식 불가 – 요약 생략)\n\n" +
                "[3] 출력 형식 예시\n\n" +
                "슬라이드 " + slideNumber + "\n" +
                "[기본버전]\n" +
                "안녕하세요. 오늘 '사업 타당성 분석'에 대해 발표할 경영학과 20210001 김지원입니다. 🌬 호흡\n" +
                "지금부터 발표를 시작하겠습니다. 🔍 청중 바라보기\n\n" +
                "[심화버전]\n" +
                "발표 주제 설명 및 자기소개\n\n" +
                "[4] 추가 지침\n" +
                "- 반드시 \"슬라이드 " + slideNumber + "\"로 시작하고, 다른 슬라이드 번호는 사용하지 마세요.\n" +
                "- [기본버전], [심화버전] 순서로 반드시 출력하세요.\n" +
                "- 형식, 아이콘, 슬라이드/버전 순서를 꼭 지켜주세요.\n" +
                "- 출력 결과가 위 예시와 다르거나 형식이 어긋나지 않도록 주의하세요.\n\n" +
                "⛔ 절대 금지사항:\n" +
                "- 슬라이드 " + (slideNumber + 1) + ", 슬라이드 " + (slideNumber + 2) + " 등 다른 번호 사용 금지\n" +
                "- 여러 슬라이드로 분할 금지\n" +
                "- 오직 슬라이드 " + slideNumber + "번만 출력\n\n" +
                "---\n" +
                "[슬라이드 " + slideNumber + "번 OCR 또는 텍스트 추출 결과]\n" +
                slideText + "\n" +
                "---\n" +
                "[최종 출력]";
    }

    // 프롬프트 생성 (기존 호환성)

    // OPENAI API 호출 메서드
    private String callAiModel(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("프롬프트가 비어있습니다.");
        }

        String url = "https://api.openai.com/v1/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(OPENAI_API_KEY);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o-mini"); // 가능하면 JSON 모드를 지원하는 최신으로
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "너는 발표 큐카드를 생성하는 도우미야."),
                Map.of("role", "user", "content", prompt)));
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 2000);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getBody() == null) {
                throw new RuntimeException("OpenAI API 응답이 비어있습니다.");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

            if (responseBody == null) {
                throw new RuntimeException("OpenAI API 응답 본문이 비어있습니다.");
            }

            // 에러 응답 체크
            if (responseBody.containsKey("error")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
                String errorMessage = (String) error.get("message");
                String errorType = (String) error.get("type");
                throw new RuntimeException("OpenAI API 오류 [" + errorType + "]: " + errorMessage);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");

            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("OpenAI API 응답에 선택지가 없습니다.");
            }

            Map<String, Object> firstChoice = choices.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");

            if (message == null) {
                throw new RuntimeException("OpenAI API 응답에 메시지가 없습니다.");
            }

            String content = (String) message.get("content");
            if (content == null || content.trim().isEmpty()) {
                throw new RuntimeException("OpenAI API 응답 내용이 비어있습니다.");
            }

            return content.trim();

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // HTTP 4xx 오류 (API 키 문제, 요청 형식 오류 등)
            log.error("OpenAI API 클라이언트 오류 [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("AI 서비스 요청 오류: " + e.getMessage());
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            // HTTP 5xx 오류 (OpenAI 서버 문제)
            log.error("OpenAI API 서버 오류 [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("AI 서비스 일시적 오류: " + e.getMessage());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            // 네트워크 연결 문제
            log.error("OpenAI API 연결 오류: {}", e.getMessage());
            throw new RuntimeException("AI 서비스 연결 실패: " + e.getMessage());
        } catch (Exception e) {
            // 로깅을 위한 에러 정보 출력
            log.error("큐카드 생성 실패: {}", e.getMessage(), e);

            // 사용자 친화적인 기본 메시지 반환
            throw new RuntimeException("큐카드 생성에 실패했습니다: " + e.getMessage());
        }
    }

}

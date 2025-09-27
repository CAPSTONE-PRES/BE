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
import org.springframework.web.client.RestTemplate;

import com.pres.pres_server.dto.CueCardDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GenerateCueService {
    @Value("${openai.api.key}")
    private String OPENAI_API_KEY;

    private final RestTemplate restTemplate;
    private final ExtractTextService extractTextService;

    public CueCardDto generateCueCards(Long fileId) {
        // ExtractTextService를 통해 슬라이드 텍스트 가져오기
        List<String> slideTexts = extractTextService.getSlideTextsByFileId(fileId);
        String fullText = extractTextService.getFullTextByFileId(fileId);

        List<String> cueCards = new ArrayList<>();
        for (String slideText : slideTexts) {
            String prompt = buildCueCardPrompt(slideText);
            String cueCard = callAiModel(prompt);
            cueCards.add(cueCard);
        }

        CueCardDto cueCardDto = new CueCardDto();
        cueCardDto.setFileId(fileId);
        cueCardDto.setExtractedText(fullText); // 전체 텍스트 사용
        cueCardDto.setCueCards(cueCards);
        return cueCardDto;
    }

    // 프롬프트 생성
    private String buildCueCardPrompt(String slideText) {
        return """
                너는 대학생 발표자료에서 발표자가 사용할 발표 대본과 요약 큐카드를 생성하는 전문가야.

                아래 슬라이드별 텍스트를 참고해, 반드시 아래 지침과 예시를 따라 발표자료 대본을 작성해 줘.

                ---
                [지침]
                [0] 슬라이드 번호 자동 지정
                - 입력된 텍스트에서 슬라이드별 명확한 구분자(예: 제목) 없이도, 각 주요 섹션 순서대로 슬라이드 번호(1, 2, 3, ...)를 부여하여 모든 슬라이드를 누락 없이 처리하세요.

                [1] 기본버전
                - 슬라이드의 텍스트와 구조를 참고하여, 각 페이지(슬라이드)마다 발표자가 읽을 수 있는 발표 대본을 작성하세요.
                - 문장은 자연스럽고 논리적인 흐름을 갖추되, 단정적이고 공식적인 발표체(예: “~입니다”, “~합니다”)를 사용하세요.
                - 구어체(예: “~거든요”, “~해요”, “~같습니다”)는 사용하지 마세요.
                - 슬라이드 제목만을 주제로 삼거나, 파일명으로 내용을 유추하지 마세요.
                - 발표자가 실제로 말하지 않을 내용(예: “이 슬라이드는 ~를 보여줍니다” 등 슬라이드 설명이나 화면 안내)은 포함하지 마세요.
                - 표지(1번 슬라이드)에서는 인사와 발표 주제를 간단하게 안내하는 수준으로만 작성하세요.
                - OCR 인식이 불가능하거나 텍스트가 거의 없는 슬라이드는 아래와 같이 작성하세요:
                    [기본버전] (OCR 인식 불가 - 요약 생략)
                - 발표 흐름이 자연스럽게 이어질 수 있도록, 각 슬라이드 마지막 문장은 다음 내용을 예고하거나, 적절한 연결 어미로 마무리하세요.
                - 발표자가 활용할 수 있도록 아래 비언어적 표현 아이콘을 적절한 위치(강조, 전환, 호흡 등)에 배치하세요:
                    🔍 청중 바라보기
                    📄 발표자료 보기
                    ✋ 제스처
                    👉 화면 가리키기
                    🌬 호흡
                    ❓ 질의응답
                - 아이콘은 실제 발표 흐름에 어울리는 위치에 자연스럽게 삽입하세요.
                    예시: 슬라이드 첫 문장 앞(호흡/청중 바라보기), 중요한 정보 뒤(화면 가리키기/제스처), 주제 전환 시(호흡/자료 보기) 등

                [2] 심화버전
                - 해당 슬라이드의 핵심 주제를 한문장으로 논리적으로 요약하세요.
                - 요약문은 간단한 문장으로, **핵심 키워드만** 포함하여 작성하세요.
                - 주요 메시지를 빠르게 파악할 수 있도록, 선언문 또는 설명문 형태로 간결하게 작성하세요.
                - 불필요한 부연설명은 피하고, 문서의 핵심 논지에 집중하세요.
                - 표지(1번 슬라이드)는 ‘이번 발표의 목적’만 간결하게 설명하세요.
                - OCR 인식이 불가능한 경우 아래처럼 작성하세요:
                    [심화버전] (OCR 인식 불가 – 요약 생략)

                [3] 출력 형식 예시

                슬라이드 1
                [기본버전]
                안녕하세요. 오늘 ‘사업 타당성 분석’에 대해 발표할 경영학과 20210001 김지원입니다. 🌬 호흡
                지금부터 발표를 시작하겠습니다. 🔍 청중 바라보기

                [심화버전]
                발표 주제 설명 및 자기소개

                슬라이드 2
                [기본버전]
                타당성 분석은 사업 아이디어가 실제로 실행 가능한지 판단하는 절차입니다.
                이를 통해 사업화 가치가 있는지 평가할 수 있습니다. 👉 화면 가리키기
                이제 타당성 분석의 시기와 중요성에 대해 설명드리겠습니다. 🌬 호흡

                [심화버전]
                타당성분석 개념 및 목적 설명

                슬라이드 3
                [기본버전]
                타당성 분석은 사업 아이디어의 실행 가능성을 예비 평가하는 것으로, 비즈니스의 초기 단계에서 수행해야 가장 효과적입니다.
                많은 리소스가 투입되기 전에 아이디어를 선별하는 데 중요한 역할을 합니다. 🔍 청중 바라보기
                일부 기업가는 아이디어 파악 후 바로 비즈니스 모델 개발로 넘어가 실수를 할 수 있습니다. ✋ 제스처
                효과적인 타당성 분석은 이러한 오류를 방지할 수 있습니다. 🌬 호흡

                [심화버전]
                타다성 분석을 비즈니스 초기단계 수행해야하는 중요성을 설명

                [4] 추가 지침
                - 각 슬라이드별로 [기본버전], [심화버전] 순서로 반드시 출력하세요.
                - 형식, 아이콘, 슬라이드/버전 순서를 꼭 지켜주세요.
                - 출력 결과가 위 예시와 다르거나 형식이 어긋나지 않도록 주의하세요.

                ---
                [슬라이드별 OCR 또는 텍스트 추출 결과]
                """ + slideText + """
                ---
                [최종 출력]
                """;
    }

    // OPENAI API 호출 메서드
    private String callAiModel(String prompt) {
        String url = "https://api.openai.com/v1/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(OPENAI_API_KEY);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o-mini");
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "너는 발표 큐카드를 생성하는 도우미야."),
                Map.of("role", "user", "content", prompt)));
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 2000);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getBody() == null) {
                throw new RuntimeException("OpenAI API 응답이 비어있습니다.");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
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
        } catch (Exception e) {
            // 로깅을 위한 에러 정보 출력 (실제 운영에서는 적절한 로깅 프레임워크 사용)
            System.err.println("큐카드 생성 실패: " + e.getMessage());
            e.printStackTrace();

            // 사용자 친화적인 기본 메시지 반환
            return "큐카드 생성에 실패했습니다. 잠시 후 다시 시도해주세요.";
        }
    }

}

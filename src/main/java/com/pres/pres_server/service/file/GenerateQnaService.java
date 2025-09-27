package com.pres.pres_server.service.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.pres.pres_server.dto.QnaDto;

@Service
public class GenerateQnaService {
    @Value("${openai.api.key}")
    private String OPENAI_API_KEY;

    // extractedText를 받아서 예상 질문과 답변을 생성하는 메서드
    public QnaDto generateQna(String extractedText) {
        // extractedText를 받아서 예상 질문과 답변을 생성하는 메서드.
        String question = callAiModel(extractedText, "question");
        String answer = callAiModel(extractedText, "answer");

        // 임시로 더미 데이터를 반환합니다.
        QnaDto qna = new QnaDto();
        qna.setQuestion(question);
        qna.setAnswer(answer);
        return qna;
    }

    private String callAiModel(String extractedText, String type) {
        String prompt;
        if ("question".equals(type)) {
            prompt = "다음 텍스트를 참고해서 예상 질문을 생성해줘: " + extractedText;
        } else if ("answer".equals(type)) {
            prompt = "다음 텍스트를 참고해서 적절한 답변을 생성해줘: " + extractedText;
        } else {
            prompt = extractedText;
        }
        // AI 모델 호출 로직
        return prompt;
    }
}

package com.pres.pres_server.repository;

import com.pres.pres_server.domain.QnaAnswer;
import com.pres.pres_server.domain.QnaQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QnaAnswerRepository extends JpaRepository<QnaAnswer, Long> {

    // 특정 질문의 모든 답변 찾기
    List<QnaAnswer> findByQnaQuestion(QnaQuestion qnaQuestion);

    // 특정 질문의 특정 타입 답변 찾기
    List<QnaAnswer> findByQnaQuestionAndAnswerType(QnaQuestion qnaQuestion, String answerType);

    // AI 생성 답변만 찾기
    List<QnaAnswer> findByOrigin(String origin);

    // 특정 모델로 생성된 답변 찾기
    List<QnaAnswer> findByModel(String model);

    // 질문 ID로 AI 생성 답변 찾기
    @Query("SELECT a FROM QnaAnswer a WHERE a.qnaQuestion.qnaId = :questionId AND a.origin = 'AI_GENERATED'")
    List<QnaAnswer> findAiAnswersByQuestionId(@Param("questionId") Long questionId);
}
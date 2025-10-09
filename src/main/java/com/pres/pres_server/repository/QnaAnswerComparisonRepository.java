package com.pres.pres_server.repository;

import com.pres.pres_server.domain.PracticeSession;
import com.pres.pres_server.domain.QnaAnswerComparison;
import com.pres.pres_server.domain.QnaQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QnaAnswerComparisonRepository extends JpaRepository<QnaAnswerComparison, Long> {

    // 특정 세션의 QnA 비교 결과 조회
    Optional<QnaAnswerComparison> findByPracticeSession(PracticeSession practiceSession);

    // 특정 세션의 모든 QnA 비교 결과 조회
    List<QnaAnswerComparison> findAllByPracticeSession(PracticeSession practiceSession);

    // 특정 세션의 특정 질문에 대한 비교 결과 조회
    Optional<QnaAnswerComparison> findByPracticeSessionAndQnaQuestion(
            PracticeSession practiceSession,
            QnaQuestion qnaQuestion);
}


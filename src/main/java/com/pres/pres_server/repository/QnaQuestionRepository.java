package com.pres.pres_server.repository;

import com.pres.pres_server.domain.QnaQuestion;
import com.pres.pres_server.domain.CueCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QnaQuestionRepository extends JpaRepository<QnaQuestion, Long> {

    // CueCard로 질문 찾기
    List<QnaQuestion> findByCueCardId(CueCard cueCard);

    // 특정 CueCard의 활성 상태 질문만 찾기
    @Query("SELECT q FROM QnaQuestion q WHERE q.cueCardId = :cueCard AND q.status = 'ACTIVE'")
    List<QnaQuestion> findActiveByCueCard(@Param("cueCard") CueCard cueCard);

    // AI 생성 질문만 찾기
    List<QnaQuestion> findByOrigin(String origin);

    // 특정 모델로 생성된 질문 찾기
    List<QnaQuestion> findByModel(String model);
}
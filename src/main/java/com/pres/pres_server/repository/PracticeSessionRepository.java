package com.pres.pres_server.repository;

import com.pres.pres_server.domain.PracticeSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    /**
     * 특정 프로젝트의 모든 연습 세션 조회
     */
    List<PracticeSession> findByProjectProjectIdOrderByPracticedAtDesc(Long projectId);

    /**
     * 프로젝트의 최신 연습 세션 하나만 조회 (practicedAt 내림차순, 가장 최근)
     */
    java.util.Optional<PracticeSession> findTopByProjectProjectIdOrderByPracticedAtDesc(Long projectId);
}

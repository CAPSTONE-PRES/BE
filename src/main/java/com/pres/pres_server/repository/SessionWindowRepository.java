package com.pres.pres_server.repository;

import com.pres.pres_server.domain.SessionWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionWindowRepository extends JpaRepository<SessionWindow, Long> {

    /**
     * 특정 세션의 모든 윈도우 조회
     */
    List<SessionWindow> findByPracticeSessionSessionIdOrderByWindowIndexAsc(Long sessionId);

    /**
     * 특정 세션의 윈도우 개수 조회
     */
    long countByPracticeSessionSessionId(Long sessionId);

    /**
     * 특정 세션의 실패한 윈도우만 조회
     */
    List<SessionWindow> findByPracticeSessionSessionIdAndStatusOrderByWindowIndexAsc(Long sessionId, String status);
}
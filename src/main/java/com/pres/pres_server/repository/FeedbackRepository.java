package com.pres.pres_server.repository;

import com.pres.pres_server.domain.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    /**
     * 특정 세션의 피드백 조회
     */
    Optional<Feedback> findByPracticeSessionSessionId(Long sessionId);
}

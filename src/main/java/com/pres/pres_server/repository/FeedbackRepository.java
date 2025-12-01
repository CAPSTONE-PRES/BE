package com.pres.pres_server.repository;

import com.pres.pres_server.domain.Feedback;
import com.pres.pres_server.dto.practice.PracticeHistoryDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    /**
     * 특정 세션의 피드백 조회
     */
    Optional<Feedback> findByPracticeSessionSessionId(Long sessionId);

    // 지난 연습 기록 조회 (현재 세션 제외)
    @Query("""
            select new com.pres.pres_server.dto.practice.PracticeHistoryDto(
                ps.sessionId,
                ps.practicedAt,
                f.totalScore
            )
            from Feedback f
            join f.practiceSession ps
            where ps.project.projectId = :projectId
              and ps.sessionId <> :excludeSessionId
            order by ps.practicedAt desc
            """)
    // Use Pageable to allow limiting results (e.g., top 3) and ensure DB-side
    // ordering
    List<PracticeHistoryDto> findHistoryByProjectIdExcludingSession(
            @Param("projectId") Long projectId,
            @Param("excludeSessionId") Long excludeSessionId,
            org.springframework.data.domain.Pageable pageable);
}

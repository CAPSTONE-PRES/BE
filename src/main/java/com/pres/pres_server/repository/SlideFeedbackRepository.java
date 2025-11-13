package com.pres.pres_server.repository;

import com.pres.pres_server.domain.SlideFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SlideFeedbackRepository extends JpaRepository<SlideFeedback, Long> {

    /**
     * 특정 피드백 ID에 해당하는 모든 슬라이드 피드백 조회
     * 슬라이드 번호 순으로 정렬
     */
    @Query("SELECT sf FROM SlideFeedback sf WHERE sf.feedback.feedbackId = :feedbackId ORDER BY sf.slideNumber ASC")
    List<SlideFeedback> findByFeedbackIdOrderBySlideNumber(@Param("feedbackId") Long feedbackId);

    /**
     * 특정 피드백 ID와 슬라이드 번호로 조회
     */
    @Query("SELECT sf FROM SlideFeedback sf WHERE sf.feedback.feedbackId = :feedbackId AND sf.slideNumber = :slideNumber")
    SlideFeedback findByFeedbackIdAndSlideNumber(@Param("feedbackId") Long feedbackId,
            @Param("slideNumber") Integer slideNumber);
}

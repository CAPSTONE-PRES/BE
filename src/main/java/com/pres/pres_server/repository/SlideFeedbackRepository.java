package com.pres.pres_server.repository;

import com.pres.pres_server.domain.SlideFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SlideFeedbackRepository extends JpaRepository<SlideFeedback, Long> {

    @Query("SELECT sf FROM SlideFeedback sf WHERE sf.feedback.feedbackId = :feedbackId ORDER BY sf.timestampSeconds ASC, sf.id ASC")
    List<SlideFeedback> findByFeedbackIdOrderByTimestampSecondsAsc(@Param("feedbackId") Long feedbackId);

    @Query("SELECT sf FROM SlideFeedback sf WHERE sf.feedback.feedbackId = :feedbackId AND sf.slideNumber = :slideNumber ORDER BY sf.timestampSeconds ASC")
    List<SlideFeedback> findByFeedbackIdAndSlideNumberOrderByTimestampSecondsAsc(@Param("feedbackId") Long feedbackId,
            @Param("slideNumber") Integer slideNumber);

    @Query("SELECT sf FROM SlideFeedback sf WHERE sf.feedback.feedbackId = :feedbackId AND sf.slideNumber = :slideNumber AND sf.visitIndex = :visitIndex")
    SlideFeedback findByFeedbackIdAndSlideNumberAndVisitIndex(@Param("feedbackId") Long feedbackId,
            @Param("slideNumber") Integer slideNumber,
            @Param("visitIndex") Integer visitIndex);
}

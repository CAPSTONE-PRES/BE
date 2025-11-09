package com.pres.pres_server.repository;

import com.pres.pres_server.domain.Comment;
import com.pres.pres_server.domain.CueCard;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    // 특정 큐카드에 달린 모든 코멘트 조회
    List<Comment> findByCueCard(CueCard cueCard);

    //
    List<Comment> findByCueCardCueIdAndParentCommentIsNull(Long cueCardId);

    // top-level 댓글 조회 + 대댓글 fetch join
    @Query("SELECT DISTINCT c FROM Comment c " +
            "LEFT JOIN FETCH c.replies r " +
            "WHERE c.cueCard.cueId = :cueId AND c.parentComment IS NULL " +
            "ORDER BY c.createdAt ASC")
    List<Comment> findTopLevelCommentsWithReplies(@Param("cueId") Long cueId);
}
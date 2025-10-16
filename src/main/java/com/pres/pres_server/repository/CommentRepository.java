package com.pres.pres_server.repository;

import com.pres.pres_server.domain.Comment;
import com.pres.pres_server.domain.CueCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    // 특정 큐카드에 달린 모든 코멘트 조회
    List<Comment> findByCueCard(CueCard cueCard);
}
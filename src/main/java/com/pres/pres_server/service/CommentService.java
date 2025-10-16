package com.pres.pres_server.service;

import com.pres.pres_server.domain.Comment;
import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.Comment.CommentRequestDTO;
import com.pres.pres_server.dto.Comment.CommentResponseDTO;
import com.pres.pres_server.repository.CommentRepository;
import com.pres.pres_server.repository.CueCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CueCardRepository cueCardRepository;

    @Transactional
    public CommentResponseDTO addComment(Long fileId, int slideNumber, CommentRequestDTO request, User user) {

        CueCard cueCard = cueCardRepository.findById(request.getCueId())
                .orElseThrow(() -> new IllegalArgumentException("큐카드가 존재하지 않습니다: " + request.getCueId()));

        Comment comment = new Comment();
        comment.setCueCard(cueCard);
        comment.setAuthorUser(user);
        comment.setContent(request.getContent());
        comment.setLocation(request.getLocation());
        comment.setCreatedAt(LocalDateTime.now());

        Comment saved = commentRepository.save(comment);

        CommentResponseDTO response = new CommentResponseDTO();
        response.setCommentId(saved.getCommentId());
        response.setMessage("코멘트가 성공적으로 저장되었습니다.");

        return response;
    }
}

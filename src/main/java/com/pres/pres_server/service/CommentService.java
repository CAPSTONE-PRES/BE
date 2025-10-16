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

    // 코멘트 수정 서비스 코드
    @Transactional
    public CommentResponseDTO updateComment(Long commentId, CommentRequestDTO request, User user) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("코멘트가 존재하지 않습니다: " + commentId));

        if (!comment.getAuthorUser().getId().equals(user.getId())) {
            throw new RuntimeException("수정 권한이 없습니다.");
        }

        comment.setContent(request.getContent());
        comment.setLocation(request.getLocation());
        commentRepository.save(comment);

        return CommentResponseDTO.builder()
                .commentId(comment.getCommentId())
                .message("코멘트가 성공적으로 수정되었습니다.")
                .build();
    }

    // 코멘트 삭제 서비스 코드
    @Transactional
    public CommentResponseDTO deleteComment(Long commentId, User user) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("코멘트가 존재하지 않습니다: " + commentId));

        if (!comment.getAuthorUser().getId().equals(user.getId())) {
            throw new RuntimeException("삭제 권한이 없습니다.");
        }

        commentRepository.delete(comment);

        return CommentResponseDTO.builder()
                .commentId(commentId)
                .message("코멘트가 성공적으로 삭제되었습니다.")
                .build();
    }
}

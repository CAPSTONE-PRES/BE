package com.pres.pres_server.service;

import com.pres.pres_server.domain.Comment;
import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.Comment.CommentRequestDTO;
import com.pres.pres_server.dto.Comment.CommentResponseDTO;
import com.pres.pres_server.dto.Comment.ReplyDTO;
import com.pres.pres_server.dto.CueCard.CommentDetailDTO;
import com.pres.pres_server.dto.CueCard.CueCardCommentDTO;
import com.pres.pres_server.repository.CommentRepository;
import com.pres.pres_server.repository.CueCardRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CueCardRepository cueCardRepository;

    // 최상위 댓글 생성
    public CommentResponseDTO createComment(Long cueId, CommentRequestDTO request, User user) {
        CueCard cueCard = cueCardRepository.findById(cueId)
                .orElseThrow(() -> new EntityNotFoundException("해당 큐카드가 존재하지 않습니다."));

        Comment comment = Comment.builder()
                .cueCard(cueCard)
                .authorUser(user)
                .content(request.getContent())
                .location(request.getLocation())
                .createdAt(LocalDateTime.now())
                .build();

        commentRepository.save(comment);
        return CommentResponseDTO.from(comment, user);
    }

    // 대댓글 생성
    public ReplyDTO createReply(Long parentCommentId, CommentRequestDTO request, User user) {
        Comment parentComment = commentRepository.findById(parentCommentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent comment not found"));

        Comment reply = new Comment();
        reply.setParentComment(parentComment);
        reply.setCueCard(parentComment.getCueCard());
        reply.setAuthorUser(user);
        reply.setContent(request.getContent());
        reply.setCreatedAt(LocalDateTime.now());

        parentComment.getReplies().add(reply);
        commentRepository.save(reply);

        return ReplyDTO.from(reply);
    }

    // 댓글 수정
    public CommentResponseDTO updateComment(Long commentId, CommentRequestDTO request, User user) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("해당 댓글을 찾을 수 없습니다"));

        if (!comment.getAuthorUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("해당 코멘트를 수정할 권한이 없습니다.");
        }

        comment.setContent(request.getContent());
        commentRepository.save(comment);

        return CommentResponseDTO.from(comment, user);
    }

    // 댓글 삭제
    public void deleteComment(Long commentId, User user) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("해당 댓글을 찾을 수 없습니다"));

        if (!comment.getAuthorUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("해당 코멘트를 삭제할 권한이 없습니다.");
        }

        commentRepository.delete(comment);
    }

    // 댓글 조회
    public List<CommentResponseDTO> getComments(Long cueId, User currentUser) {
        List<Comment> comments = commentRepository.findTopLevelCommentsWithReplies(cueId);

        return comments.stream()
                .map(c -> CommentResponseDTO.from(c, currentUser))
                .collect(Collectors.toList());
    }

    /*

    @Transactional
    public CommentResponseDTO addComment(CommentRequestDTO request, User user) {

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

    // 코멘트 슬라이드별 불러오기
    @Transactional(readOnly = true)
    public List<CueCardCommentDTO> getCommentsBySlide(Long fileId, int slideNumber, User user) {

        // 슬라이드 큐카드 리스트 조회
        List<CueCard> cueCards = cueCardRepository.findByPresentationFile_FileIdAndSlideNumber(fileId, slideNumber);
        if (cueCards.isEmpty()) return Collections.emptyList();

        List<CueCardCommentDTO> result = new ArrayList<>();

        for (CueCard cueCard : cueCards) {
            List<Comment> cueComments = commentRepository.findByCueCard(cueCard);

            List<CommentDetailDTO> commentDetails = cueComments.stream()
                    .map(c -> CommentDetailDTO.builder()
                            .commentId(c.getCommentId())
                            .authorUserId(c.getAuthorUser().getId())
                            .authorName(c.getAuthorUser().getUsername())
                            .authorProfileImageUrl(c.getAuthorUser().getProfileImageUrl())
                            .content(c.getContent())
                            .location(c.getLocation())
                            .createdAt(c.getCreatedAt())
                            .build())
                    .toList();

            result.add(new CueCardCommentDTO(cueCard.getCueId(), commentDetails));
        }

        return result;
    }

     */
}

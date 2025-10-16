package com.pres.pres_server.service;

import com.pres.pres_server.domain.Comment;
import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.Comment.CommentRequestDTO;
import com.pres.pres_server.dto.Comment.CommentResponseDTO;
import com.pres.pres_server.dto.CueCard.CommentDetailDTO;
import com.pres.pres_server.dto.CueCard.CueCardCommentDTO;
import com.pres.pres_server.repository.CommentRepository;
import com.pres.pres_server.repository.CueCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CueCardRepository cueCardRepository;

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

}

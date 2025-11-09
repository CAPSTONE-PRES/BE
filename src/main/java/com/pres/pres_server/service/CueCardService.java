package com.pres.pres_server.service;

import com.pres.pres_server.domain.*;
import com.pres.pres_server.dto.CueCard.CueCardContentDTO;
import com.pres.pres_server.dto.CueCard.CueCardCreateResponseDTO;
import com.pres.pres_server.dto.CueCard.CueCardUpdateRequest;
import com.pres.pres_server.dto.CueCard.CueCardUpdateResponseDTO;
import com.pres.pres_server.dto.Workspace.WorkspaceMemberDTO;
import com.pres.pres_server.dto.practice.CueCardUncheckedDTO;
import com.pres.pres_server.dto.practice.CueCardUncheckedMemberDTO;
import com.pres.pres_server.repository.CueCardCheckMemberRepository;
import com.pres.pres_server.repository.CueCardRepository;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CueCardService {

    private final CueCardRepository cueCardRepository;
    private final CueCardCheckMemberRepository cueCardCheckRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CueCardCheckMemberRepository cueCardCheckMemberRepository;
    private final PresentationFileRepository presentationFileRepository;

    // 큐카드 페이지별 불러오기
    @Transactional(readOnly = true)
    public CueCardCreateResponseDTO getCueCards(Long fileId, int slideNumber, User user) {

        PresentationFile file = presentationFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("해당 파일이 존재하지 않습니다."));

        // 권한 확인: 로그인한 유저가 워크스페이스 멤버인지
        boolean isMember = teamMemberRepository.existsByWorkspace_WorkspaceIdAndUser_Id(
                file.getProject().getWorkspaceId().getWorkspaceId(), user.getId()
        );
        if (!isMember) throw new RuntimeException("권한이 없습니다.");

        List<CueCard> cueCards = cueCardRepository.findByPresentationFile_FileIdAndSlideNumber(fileId, slideNumber);

        List<CueCardContentDTO> cueCardContents = cueCards.stream()
                .map(c -> new CueCardContentDTO(c.getCueId(), c.getContent()))
                .toList();

        return new CueCardCreateResponseDTO(fileId, slideNumber, cueCardContents);
    }

    // 비언어적 요소 on/off 서비스 코드
    @Transactional(readOnly = true)
    public CueCardCreateResponseDTO getCueCardsNonVerbal(Long fileId, int slideNumber, String type, User user) {

        PresentationFile file = presentationFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("해당 파일이 존재하지 않습니다."));

        // 권한 확인
        boolean isMember = teamMemberRepository.existsByWorkspace_WorkspaceIdAndUser_Id(
                file.getProject().getWorkspaceId().getWorkspaceId(), user.getId()
        );
        if (!isMember) throw new RuntimeException("권한이 없습니다.");

        List<CueCard> cueCards = cueCardRepository.findByPresentationFile_FileIdAndSlideNumber(fileId, slideNumber);

        List<CueCardContentDTO> cueCardContents = cueCards.stream()
                .map(c -> {
                    String content = c.getContent();
                    // type=off이면 비언어적 요소 제거
                    if ("off".equalsIgnoreCase(type)) {
                        content = content.replaceAll("<[^>]*>", "");
                    }
                    return new CueCardContentDTO(c.getCueId(), content);
                })
                .toList();

        return new CueCardCreateResponseDTO(fileId, slideNumber, cueCardContents);
    }


    // 큐카드 내용 업데이트
    /*@Transactional
    public CueCardUpdateResponseDTO updateCueCards(Long fileId, int slideNumber,
                                                   CueCardUpdateRequest request, User user) {

        PresentationFile file = presentationFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("해당 파일이 존재하지 않습니다."));

        boolean isMember = teamMemberRepository.existsByWorkspace_WorkspaceIdAndUser_Id(
                file.getProject().getWorkspaceId().getWorkspaceId(), user.getId()
        );
        if (!isMember) throw new RuntimeException("권한이 없습니다.");

        List<CueCard> cueCards = cueCardRepository.findByPresentationFile_FileIdAndSlideNumber(fileId, slideNumber);

        for (CueCardContentDTO dto : request.getCueCards()) {
            CueCard cueCard = cueCards.stream()
                    .filter(c -> c.getCueId().equals(dto.getCueId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("큐카드가 존재하지 않습니다: " + dto.getCueId()));

            cueCard.setContent(dto.getContent());
            cueCardRepository.save(cueCard);
        }

        return new CueCardUpdateResponseDTO(fileId, slideNumber,
                "cuecard 내용 업데이트가 성공적으로 완료되었습니다");
    }*/
    @Transactional
    public CueCardUpdateResponseDTO updateCueCard(Long fileId, int slideNumber, Integer sectionNumber, CueCardUpdateRequest request, User user) {
        try {
            // 1. 큐카드 조회 (슬라이드 + 섹션 + 파일 기준)
            CueCard cueCard = cueCardRepository.findByPresentationFile_FileIdAndSlideNumberAndSectionNumber(fileId, slideNumber, sectionNumber)
                    .orElseThrow(() -> new IllegalArgumentException("큐카드를 찾을 수 없습니다."));

            // 2. 내용 업데이트
            cueCard.setContent(request.getContent());
            cueCardRepository.save(cueCard);

            // 3. DTO 반환
            return CueCardUpdateResponseDTO.builder()
                    .cueId(cueCard.getCueId())
                    .content(cueCard.getContent())
                    .messager("성공적으로 업데이트 되었습니다")
                    .updatedAt(cueCard.getUpdatedAt())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("큐카드 업데이트 중 오류 발생", e);
        }
    }

    // 큐카드 체크 상태 변환 서비스
    @Transactional
    public void setCheckStatus(Long cueId, User user, boolean status) {

        CueCard cue = cueCardRepository.findById(cueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CueCard not found"));

        // 권한 체크 — 해당 파일이 속한 프로젝트/워크스페이스의 멤버인지 확인
        Long workspaceId = cue.getPresentationFile().getProject().getWorkspaceId().getWorkspaceId();
        boolean isMember = teamMemberRepository.findByWorkspace_WorkspaceId(workspaceId)
                .stream()
                .anyMatch(tm -> tm.getUser().getId().equals(user.getId()));

        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "워크스페이스 멤버가 아닙니다");
        }

        //upsert 체크 레코드
        Optional<CueCardCheckMember> existOpt = cueCardCheckRepository.findByCueCardAndUser(cue, user);
        if (existOpt.isPresent()) {
            CueCardCheckMember chk = existOpt.get();
            chk.setChecked(status);
            chk.setCheckedAt(status ? LocalDateTime.now() : null);
            cueCardCheckRepository.save(chk);
        } else {
            CueCardCheckMember chk = new CueCardCheckMember();
            chk.setCueCard(cue);
            chk.setUser(user);
            chk.setChecked(status);
            chk.setCheckedAt(status ? LocalDateTime.now() : null);
            cueCardCheckRepository.save(chk);
        }
    }

    // 큐카드 체크 안한 멤버 조회 서비스 코드
    public CueCardUncheckedDTO getUncheckedMembers(Long cueId) {
        CueCard cueCard = cueCardRepository.findByCueId(cueId)
                .orElseThrow(() -> new IllegalArgumentException("해당 큐카드가 존재하지 않습니다."));

        List<Long> checkedUserIds = cueCardCheckMemberRepository.findByCueCard(cueCard)
                .stream()
                .map(ccm -> ccm.getUser().getId())
                .toList();

        List<WorkspaceMemberDTO> uncheckedMembers = teamMemberRepository
                .findByWorkspace_WorkspaceId(cueCard.getPresentationFile().getProject().getWorkspaceId().getWorkspaceId())
                .stream()
                .filter(tm -> !checkedUserIds.contains(tm.getUser().getId()))
                .map(tm -> new WorkspaceMemberDTO(
                        tm.getMemberId(),
                        tm.getUser().getId(),
                        tm.getUser().getEmail(),
                        tm.getUser().getUsername(),
                        tm.getUser().getProfileImageUrl()
                ))
                .toList();

        CueCardUncheckedMemberDTO cueCardDTO = new CueCardUncheckedMemberDTO(cueCard.getCueId(), uncheckedMembers);
        return new CueCardUncheckedDTO(cueId, List.of(cueCardDTO));
    }


}

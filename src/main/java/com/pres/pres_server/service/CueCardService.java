package com.pres.pres_server.service;

import com.pres.pres_server.domain.*;
import com.pres.pres_server.dto.CueCard.CueCardContentDTO;
import com.pres.pres_server.dto.CueCard.CueCardCreateResponseDTO;
import com.pres.pres_server.dto.CueCard.CueCardUpdateRequest;
import com.pres.pres_server.dto.CueCard.CueCardUpdateResponseDTO;
import com.pres.pres_server.dto.Workspace.WorkspaceMemberDTO;
import com.pres.pres_server.dto.practice.CueCardCheckStatusDTO;
import com.pres.pres_server.dto.practice.CueCardCheckStatusFileDTO;
import com.pres.pres_server.dto.practice.CueCardUncheckedDTO;
import com.pres.pres_server.dto.practice.CueCardUncheckedMemberDTO;
import com.pres.pres_server.repository.CueCardCheckMemberRepository;
import com.pres.pres_server.repository.CueCardRepository;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.TeamMemberRepository;
import com.pres.pres_server.service.user.UserService;
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
    private final UserService userService;


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
//
//    // 큐카드 내용 업데이트
//    @Transactional
//    public CueCardUpdateResponseDTO updateCueCardMode(Long cueId, CueCardUpdateRequest request, User user) {
//
//        CueCard.Mode modeEnum = request.getMode(); // 이미 Enum 타입이므로 변환 불필요
//
//        CueCard cueCard = cueCardRepository.findByCueIdAndMode(cueId, modeEnum)
//                .orElseThrow(() -> new RuntimeException("해당 cueId와 mode에 해당하는 큐카드를 찾을 수 없습니다."));
//
//        if (!cueCard.getPresentationFile().getProject().getPresenter().equals(user)) {
//            throw new RuntimeException("큐카드를 수정할 권한이 없습니다.");
//        }
//
//        if (request.getContent() == null || request.getContent().isBlank()) {
//            throw new RuntimeException("content는 비어있을 수 없습니다.");
//        }
//
//        cueCard.setContent(request.getContent());
//        cueCardRepository.saveAndFlush(cueCard);
//
//        return CueCardUpdateResponseDTO.builder()
//                .cueId(cueCard.getCueId())
//                .updatedContent(cueCard.getContent())
//                .message("큐카드가 성공적으로 수정되었습니다.")
//                .build();
//    }

    public CueCardUpdateResponseDTO updateCueCardMode(Long cueId, CueCardUpdateRequest request, Long userId) {

        CueCard.Mode modeEnum = request.getMode();

        CueCard cueCard = cueCardRepository.findByCueIdAndMode(cueId, modeEnum)
                .orElseThrow(() -> new RuntimeException("해당 cueId와 mode에 해당하는 큐카드를 찾을 수 없습니다."));

        User presenter = cueCard.getPresentationFile().getProject().getPresenter();

        if (!presenter.getId().equals(userId)) {
            throw new RuntimeException("큐카드를 수정할 권한이 없습니다.");
        }

        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new RuntimeException("content는 비어있을 수 없습니다.");
        }

        cueCard.setContent(request.getContent());
        cueCardRepository.saveAndFlush(cueCard);

        return CueCardUpdateResponseDTO.builder()
                .cueId(cueCard.getCueId())
                .updatedContent(cueCard.getContent())
                .message("큐카드가 성공적으로 수정되었습니다.")
                .build();
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
                .filter(CueCardCheckMember::isChecked)
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
                        userService.resolveProfileUrl(tm.getUser())
                ))
                .toList();

        CueCardUncheckedMemberDTO cueCardDTO = new CueCardUncheckedMemberDTO(cueCard.getCueId(), uncheckedMembers);
        return new CueCardUncheckedDTO(cueId, List.of(cueCardDTO));
    }


    // TODO: - 큐카드 체크 상태 조회 API 임시 추가 (2025-11-11)
    @Transactional(readOnly = true)
    public CueCardCheckStatusDTO getCheckStatus(Long cueId, User user) {
        CueCard cue = cueCardRepository.findById(cueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CueCard not found"));

        // 권한 체크 - 기존 setCheckStatus와 동일한 로직
        Long workspaceId = cue.getPresentationFile().getProject().getWorkspaceId().getWorkspaceId();
        boolean isMember = teamMemberRepository.findByWorkspace_WorkspaceId(workspaceId)
                .stream()
                .anyMatch(tm -> tm.getUser().getId().equals(user.getId()));

        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "워크스페이스 멤버가 아닙니다");
        }

        boolean checked = cueCardCheckRepository.findByCueCardAndUser(cue, user)
                .map(CueCardCheckMember::isChecked)  // 도메인 확인 결과: isChecked() 사용 가능
                .orElse(false);

        return new CueCardCheckStatusDTO(cueId, checked);
    }

    // TODO: - 큐카드 파일 단위 체크 상태 조회 API 임시 추가 (2025-11-11)
    @Transactional(readOnly = true)
    public CueCardCheckStatusFileDTO getCheckStatusByFile(Long fileId, User user) {
        PresentationFile file = presentationFileRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."));

        // 권한 체크 - 워크스페이스 멤버인지 확인
        Long workspaceId = file.getProject().getWorkspaceId().getWorkspaceId();
        boolean isMember = teamMemberRepository.existsByWorkspace_WorkspaceIdAndUser_Id(
                workspaceId, user.getId()
        );

        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "워크스페이스 멤버가 아닙니다");
        }

        // 해당 파일의 모든 큐카드 조회 (기존 메서드 활용)
        List<CueCard> cueCards = cueCardRepository
                .findByPresentationFile_FileIdOrderBySlideNumberAscModeAscSectionNumberAsc(fileId);

        // 체크된 큐카드 ID만 필터링
        List<Long> checkedCueIds = cueCards.stream()
                .filter(cue -> cueCardCheckRepository.findByCueCardAndUser(cue, user)
                        .map(CueCardCheckMember::isChecked)
                        .orElse(false))
                .map(CueCard::getCueId)
                .toList();

        return new CueCardCheckStatusFileDTO(fileId, checkedCueIds);
    }


}

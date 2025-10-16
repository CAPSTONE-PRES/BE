package com.pres.pres_server.service;

import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.CueCardCheckMember;
import com.pres.pres_server.domain.TeamMember;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.Workspace.WorkspaceMemberDTO;
import com.pres.pres_server.dto.practice.CueCardUncheckedDTO;
import com.pres.pres_server.dto.practice.CueCardUncheckedMemberDTO;
import com.pres.pres_server.repository.CueCardCheckMemberRepository;
import com.pres.pres_server.repository.CueCardRepository;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @Transactional
    public void setCheckStatus(Long fileId, int slideNumber, Long cueId, User user, boolean status) {

        CueCard cue = cueCardRepository.findById(cueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CueCard not found"));

        if (!cue.getPresentationFile().getFileId().equals(fileId) || cue.getSlideNumber() != slideNumber) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileId/slideNumber mismatch");
        }

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

    public CueCardUncheckedDTO getUncheckedMembers(Long fileId, int slideNumber) {
        List<CueCard> cueCards = cueCardRepository.findByPresentationFile_FileIdAndSlideNumber(fileId, slideNumber);

        List<CueCardUncheckedMemberDTO> cueCardDTOs = new ArrayList<>();

        for (CueCard cueCard : cueCards) {
            List<Long> checkedUserIds = cueCardCheckMemberRepository.findByCueCard(cueCard)
                    .stream()
                    .map(ccm -> ccm.getUser().getId())
                    .toList();

            List<WorkspaceMemberDTO> uncheckedMembers = teamMemberRepository
                    .findByWorkspace_WorkspaceId(cueCard.getPresentationFile().getProject().getWorkspaceId().getWorkspaceId())
                    .stream()
                    .filter(tm -> !checkedUserIds.contains(tm.getUser().getId()))
                    .map(tm -> new WorkspaceMemberDTO(tm.getMemberId(), tm.getUser().getUsername(), tm.getUser().getProfileImageUrl()))
                    .toList();

            cueCardDTOs.add(new CueCardUncheckedMemberDTO(cueCard.getCueId(), uncheckedMembers));
        }

        return new CueCardUncheckedDTO(fileId, slideNumber, cueCardDTOs);
    }

}

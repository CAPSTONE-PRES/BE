package com.pres.pres_server.service;

import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.CueCardCheckMember;
import com.pres.pres_server.domain.User;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CueCardService {

    private final CueCardRepository cueCardRepository;
    private final CueCardCheckMemberRepository cueCardCheckRepository;
    private final PresentationFileRepository presentationFileRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Transactional
    public void setCheckStatus(Long fileId, int slideNumber, Long cueId, User user, boolean status) {
        // 1) cueCard 존재 및 파일/슬라이드 일치 확인
        CueCard cue = cueCardRepository.findById(cueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CueCard not found"));

        if (!cue.getPresentationFile().getFileId().equals(fileId) || cue.getSlideNumber() != slideNumber) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileId/slideNumber mismatch");
        }

        // 2) 권한 체크 — 해당 파일이 속한 프로젝트/워크스페이스의 멤버인지 확인
        Long workspaceId = cue.getPresentationFile().getProject().getWorkspaceId().getWorkspaceId();
        boolean isMember = teamMemberRepository.findByWorkspace_WorkspaceId(workspaceId)
                .stream()
                .anyMatch(tm -> tm.getUser().getId().equals(user.getId()));

        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "워크스페이스 멤버가 아닙니다");
        }

        // 3) upsert 체크 레코드
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

}

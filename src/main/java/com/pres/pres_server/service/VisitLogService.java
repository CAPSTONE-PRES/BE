package com.pres.pres_server.service;

import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.domain.VisitLog;
import com.pres.pres_server.domain.WorkSpace;
import com.pres.pres_server.repository.VisitLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class VisitLogService {

    private final VisitLogRepository visitLogRepository;

    /**
     * user + workspace + project 조합으로 VisitLog를 upsert
     */

    // 워크스페이스 방문 기록용
    public void upsertVisitLog(User user, WorkSpace workspace, Project project) {
        Optional<VisitLog> existingLog =
                visitLogRepository.findByUserAndWorkspaceAndProject(user, workspace, project);

        if (existingLog.isPresent()) {
            VisitLog log = existingLog.get();
            log.setVisitedAt(LocalDateTime.now());
        } else {
            VisitLog newLog = new VisitLog();
            newLog.setUser(user);
            newLog.setWorkspace(workspace);
            newLog.setProject(project);
            newLog.setVisitedAt(LocalDateTime.now());
            visitLogRepository.save(newLog);
        }
    }

    // 프로젝트 방문 기록용
    public void upsertVisitLog(User user, Project project) {
        if (user == null || project == null) return;

        VisitLog visitLog = visitLogRepository
                .findTopByUserAndProjectOrderByVisitedAtDesc(user, project)
                .orElse(null);

        if (visitLog == null) {
            // 새 로그 생성
            visitLog = new VisitLog();
            visitLog.setUser(user);
            visitLog.setProject(project);
        }

        // 최근 방문 시간 갱신
        visitLog.setVisitedAt(LocalDateTime.now());
        visitLogRepository.save(visitLog);
    }

}

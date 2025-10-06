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
}

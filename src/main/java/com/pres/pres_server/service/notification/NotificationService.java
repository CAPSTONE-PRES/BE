package com.pres.pres_server.service.notification;

import com.pres.pres_server.domain.Project;
import com.pres.pres_server.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 통합 알림 서비스
 * - Policy 판단
 * - 이메일 발송
 * - 도메인 이벤트용 트리거 훅
 * - 발표 연습 스케줄러
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationPolicy notificationPolicy;
    private final EmailNotificationService emailNotificationService;
    private final ProjectRepository projectRepository;
    /**
     * 정책: 주어진 유저에 대해 해당 타입 전송 허용 여부
     */
    public boolean isAllowed(Long userId, NotificationType type) {
        return notificationPolicy.isAllowed(userId, type);
    }

    /**
     * 정책이 허용하면 이메일을 발송한다 (best-effort)
     */
    public void sendIfAllowed(Long userId, NotificationType type, Map<String, Object> vars) {
        if (!isAllowed(userId, type)) {
            log.info("Notification blocked by policy: userId={}, type={}", userId, type);
            return;
        }
        // EmailNotificationService handles recipient lookup and actual sending
        emailNotificationService.send(userId, type, vars);
    }

    /* === Scheduler for practice reminders === */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void sendDailyPracticeReminders() {
        LocalDate today = LocalDate.now();
        sendForDate(today.plusDays(1), NotificationType.PRACTICE_REMINDER_D1);
        sendForDate(today.plusDays(2), NotificationType.PRACTICE_REMINDER_D2);
    }


    @Transactional(readOnly = true)
    public void sendForDate(LocalDate date, NotificationType type) {
        List<Project> projects = projectRepository.findByDueDate(date);
        for (Project p : projects) {
            if (p.getPresenter() == null || p.getPresenter().getId() == null) {
                log.warn("Skipping project without presenter: {}", p.getProjectId());
                continue;
            }
            Long presenterId = p.getPresenter().getId();
            Map<String, Object> vars = new HashMap<>();
            vars.put("projectName", p.getTitle());
            vars.put("dueDate", p.getDueDate());
            vars.put("link", p.getProjectId() == null ? "" : "/presentation/" + p.getProjectId());
            sendIfAllowed(presenterId, type, vars);
        }
    }
}

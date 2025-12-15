package com.pres.pres_server.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Deprecated scheduler wrapper that delegates to unified NotificationService
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Deprecated
public class PracticeReminderScheduler {

    private final NotificationService delegate;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void sendDailyReminders() {
        delegate.sendDailyPracticeReminders();
    }
}

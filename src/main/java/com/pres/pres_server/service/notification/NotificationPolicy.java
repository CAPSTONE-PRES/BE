package com.pres.pres_server.service.notification;

import com.pres.pres_server.dto.UserNotificationsDto;
import com.pres.pres_server.service.user.UserNotificationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 순수 정책 판단: 주어진 유저에 대해 특정 타입 알림 전송 허용 여부만 판단한다.
 * - 메일 발송이나 템플릿 처리 책임은 갖지 않는다.
 */
@Component
@RequiredArgsConstructor
public class NotificationPolicy {

    private final UserNotificationsService userNotificationsService;

    public boolean isAllowed(Long userId, NotificationType type) {
        UserNotificationsDto prefs = userNotificationsService.getForUserReadOnly(userId);
        if (!prefs.isEmailEnabled())
            return false;

        return switch (type) {
            case INVITE -> prefs.isInviteEnabled();
            case REVIEW_COMMENT -> prefs.isReviewCommentEnabled();
            case PRACTICE_REMINDER_D1 -> prefs.isPracticeReminderEnabled() && prefs.isPracticeRemindD1();
            case PRACTICE_REMINDER_D2 -> prefs.isPracticeReminderEnabled() && prefs.isPracticeRemindD2();
        };
    }
}

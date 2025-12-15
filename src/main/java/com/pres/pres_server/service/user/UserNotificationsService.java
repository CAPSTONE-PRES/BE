package com.pres.pres_server.service.user;

import com.pres.pres_server.domain.UserNotifications;
import com.pres.pres_server.dto.UserNotificationsDto;
import com.pres.pres_server.repository.UserNotificationsRepository;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// “유저 알림 설정(Preferences) 저장/조회” 전담
// GET /me/notifications 같은 API에서 호출
// PATCH /me/notifications 같은 API에서 호출
// 내부적으로 기본값 DTO 반환/초기 row 생성(업데이트 시) 처리
@Service
@RequiredArgsConstructor
@Slf4j
public class UserNotificationsService {

    private static final boolean DEFAULT_EMAIL_ENABLED = true;
    private static final boolean DEFAULT_WORKSPACE_ACTIVITY_ENABLED = true;
    private static final boolean DEFAULT_INVITE_ENABLED = true;
    private static final boolean DEFAULT_REVIEW_COMMENT_ENABLED = true;
    private static final boolean DEFAULT_PRACTICE_REMINDER_ENABLED = true;
    private static final boolean DEFAULT_PRACTICE_REMIND_D1 = false;
    private static final boolean DEFAULT_PRACTICE_REMIND_D2 = false;

    private final UserNotificationsRepository repository;

    @Transactional(readOnly = true)
    public UserNotificationsDto getForUser(Long userId) {
        return getForUserReadOnly(userId);
    }

    @Transactional(readOnly = true)
    public UserNotificationsDto getForUserReadOnly(Long userId) {
        Optional<UserNotifications> opt = repository.findByUserId(userId);
        if (opt.isPresent()) {
            return toDto(opt.get());
        }
        return defaultDtoForUser(userId);
    }

    @Transactional
    public UserNotificationsDto updateForUser(Long userId, com.pres.pres_server.dto.UserNotificationsUpdateDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("UserNotificationsUpdateDto must not be null");
        }
        UserNotifications entity = ensureNotifications(userId);

        if (dto.getEmailEnabled() != null)
            entity.setEmailEnabled(dto.getEmailEnabled());
        if (dto.getWorkspaceActivityEnabled() != null)
            entity.setWorkspaceActivityEnabled(dto.getWorkspaceActivityEnabled());
        if (dto.getInviteEnabled() != null)
            entity.setInviteEnabled(dto.getInviteEnabled());
        if (dto.getReviewCommentEnabled() != null)
            entity.setReviewCommentEnabled(dto.getReviewCommentEnabled());
        if (dto.getPracticeReminderEnabled() != null)
            entity.setPracticeReminderEnabled(dto.getPracticeReminderEnabled());

        // D1/D2 적용 — D1/D2가 true면 practiceReminderEnabled는 반드시 true로 설정
        if (dto.getPracticeRemindD1() != null) {
            entity.setPracticeRemindD1(dto.getPracticeRemindD1());
            if (dto.getPracticeRemindD1()) {
                entity.setPracticeReminderEnabled(true);
            }
        }
        if (dto.getPracticeRemindD2() != null) {
            entity.setPracticeRemindD2(dto.getPracticeRemindD2());
            if (dto.getPracticeRemindD2()) {
                entity.setPracticeReminderEnabled(true);
            }
        }

        // 만약 사용자가 명시적으로 리마인더를 비활성화하면 D1/D2도 함께 false로 만듦
        if (dto.getPracticeReminderEnabled() != null && !dto.getPracticeReminderEnabled()) {
            entity.setPracticeRemindD1(false);
            entity.setPracticeRemindD2(false);
        }

        UserNotifications saved = repository.save(entity);
        log.debug("Updated UserNotifications for userId={}: {}", userId, saved);
        return toDto(saved);
    }

    private UserNotificationsDto toDto(UserNotifications e) {
        return UserNotificationsDto.builder()
                .emailEnabled(e.isEmailEnabled())
                .workspaceActivityEnabled(e.isWorkspaceActivityEnabled())
                .inviteEnabled(e.isInviteEnabled())
                .reviewCommentEnabled(e.isReviewCommentEnabled())
                .practiceReminderEnabled(e.isPracticeReminderEnabled())
                .practiceRemindD1(e.isPracticeRemindD1())
                .practiceRemindD2(e.isPracticeRemindD2())
                .build();
    }

    private UserNotifications defaultForUser(Long userId) {
        UserNotifications u = UserNotifications.builder()
                .userId(userId)
                .emailEnabled(DEFAULT_EMAIL_ENABLED)
                .workspaceActivityEnabled(DEFAULT_WORKSPACE_ACTIVITY_ENABLED)
                .inviteEnabled(DEFAULT_INVITE_ENABLED)
                .reviewCommentEnabled(DEFAULT_REVIEW_COMMENT_ENABLED)
                .practiceReminderEnabled(DEFAULT_PRACTICE_REMINDER_ENABLED)
                .practiceRemindD1(DEFAULT_PRACTICE_REMIND_D1)
                .practiceRemindD2(DEFAULT_PRACTICE_REMIND_D2)
                .build();
        try {
            return repository.saveAndFlush(u);
        } catch (DataIntegrityViolationException ex) {
            // Don't try to broadly classify the integrity error; instead re-query.
            log.warn("saveAndFlush failed for userId={}, attempting to re-query existing record.", userId, ex);
            Optional<UserNotifications> existing = repository.findByUserId(userId);
            if (existing.isPresent()) {
                log.info(
                        "Found existing UserNotifications after save failure for userId={}. Returning existing record.",
                        userId);
                return existing.get();
            }
            log.error("Failed to create UserNotifications for userId={} and no existing record found.", userId, ex);
            throw ex;
        }
    }

    @Transactional
    public UserNotifications ensureNotifications(Long userId) {
        Optional<UserNotifications> opt = repository.findByUserId(userId);
        return opt.orElseGet(() -> defaultForUser(userId));
    }

    private UserNotificationsDto defaultDtoForUser(Long userId) {
        return UserNotificationsDto.builder()
                .emailEnabled(DEFAULT_EMAIL_ENABLED)
                .workspaceActivityEnabled(DEFAULT_WORKSPACE_ACTIVITY_ENABLED)
                .inviteEnabled(DEFAULT_INVITE_ENABLED)
                .reviewCommentEnabled(DEFAULT_REVIEW_COMMENT_ENABLED)
                .practiceReminderEnabled(DEFAULT_PRACTICE_REMINDER_ENABLED)
                .practiceRemindD1(DEFAULT_PRACTICE_REMIND_D1)
                .practiceRemindD2(DEFAULT_PRACTICE_REMIND_D2)
                .build();
    }
}

package com.pres.pres_server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_notifications")
// Auditing: use Hibernate @CreationTimestamp / @UpdateTimestamp
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotifications {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Builder.Default
    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Builder.Default
    @Column(name = "invite_enabled", nullable = false)
    private boolean inviteEnabled = true;

    @Builder.Default
    @Column(name = "review_comment_enabled", nullable = false)
    private boolean reviewCommentEnabled = true;

    @Builder.Default
    @Column(name = "practice_reminder_enabled", nullable = false)
    private boolean practiceReminderEnabled = true;

    @Builder.Default
    @Column(name = "practice_remind_d1", nullable = false)
    private boolean practiceRemindD1 = false;

    @Builder.Default
    @Column(name = "practice_remind_d2", nullable = false)
    private boolean practiceRemindD2 = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}

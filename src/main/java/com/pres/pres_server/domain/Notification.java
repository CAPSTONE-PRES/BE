package com.pres.pres_server.domain;

import com.pres.pres_server.service.notification.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_receiver", columnList = "receiver_user_id"),
                @Index(name = "idx_notifications_read", columnList = "is_read"),
                @Index(name = "idx_notifications_created", columnList = "created_at")
        }
)
public class Notification {
    @Id
    @GeneratedValue
    private Long id;

    // 알림 수신자
    @Column(name = "receiver_user_id", nullable = false)
    private Long receiverUserId;

    // 알림 종류
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    // 어떤 워크스페이스에서 발생했는지
    @Column(name = "workspace_id")
    private Long workspaceId;

    // 프로젝트 단위로 이동할 수 있도록
    @Column(name = "project_id")
    private Long projectId;

    // 리뷰ID / 댓글ID / 발표ID 등
    @Column(name = "target_id")
    private Long targetId;

    // 알림 제목 (프론트 표시)
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    // 알림 본문
    @Column(name = "message", nullable = false, length = 255)
    private String message;

    // 클릭 시 이동 URL
    @Column(name = "link_url", length = 255)
    private String linkUrl;

    // 읽음 여부
    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    // 생성 시각
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

}

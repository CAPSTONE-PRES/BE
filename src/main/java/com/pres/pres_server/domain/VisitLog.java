package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
@Entity
@Table(
        name = "visit_logs",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "workspace_id", "project_id"})
        }
)
@Getter
@Setter
public class VisitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 누가 방문했는지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 어떤 워크스페이스를 방문했는지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private WorkSpace workspace;

    // 어떤 프로젝트를 방문했는지 (없을 수도 있으니 nullable 허용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    private LocalDateTime visitedAt;

    @PrePersist
    public void prePersist() {
        visitedAt = LocalDateTime.now();
    }
}

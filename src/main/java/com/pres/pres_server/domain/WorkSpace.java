package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "workspaces")
@Getter @Setter
public class WorkSpace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(name = "workspace_name", nullable = false)
    private String workspaceName;

    @Column(name = "created_at")
    private LocalDateTime created_at;

    @ManyToOne
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUserId;

    // string 형태로 저장, 최대 3개, 0요일 00:00-00:00 으로 저장 필요
    @Column(name = "classtime1")
    private String classtime1;

    @Column(name = "classtime2")
    private String classtime2;

    @Column(name = "classtime3")
    private String classtime3;
}

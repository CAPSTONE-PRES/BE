package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_members", uniqueConstraints = @UniqueConstraint(columnNames = { "workspace_id", "user_id" }))
@Getter
@Setter
public class TeamMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long member_id;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "invited_at")
    private LocalDateTime invited_at;

    @ManyToOne
    @JoinColumn(name = "workspace_id", nullable = false)
    private WorkSpace workspace;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}

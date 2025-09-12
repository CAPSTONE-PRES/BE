package com.pres.pres_server.domain;

import lombok.*;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "email_auth_code")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailAuthCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "expire_at", nullable = false)
    private LocalDateTime expireAt;
}

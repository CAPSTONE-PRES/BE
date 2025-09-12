package com.pres.pres_server.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 기본 생성자
@AllArgsConstructor
@Builder
@Table(name = "users")
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "email_verified", nullable = false)
    private boolean email_verified;

    @Column(name = "email_verified_at")
    private LocalDateTime email_verified_at;

    @Column(name = "is_admin")
    private boolean is_admin;

    @Column(name = "profile_image_url")
    private String profile_image_url;

    @Column(name = "push_enabled", nullable = false)
    private boolean push_enabled = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime created_at;

    @Builder
    public User(String email, String password, String username, boolean email_verified) {
        this.email = email;
        this.password = password;
        this.username = username;
        this.email_verified = email_verified;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("user"));
    }

    @Override // 계정 만료 여부
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override // 계정 잠금 여부
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override // 계정 비활성화 여부
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override // 계정 활성화 여부
    public boolean isEnabled() {
        return true;
    }

    // 사용자 이름 변경
    // OAuth2UserCustomService에서 사용
    public User update(String username) {
        this.username = username;
        return this;
    }

}

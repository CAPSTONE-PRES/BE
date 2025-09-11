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
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password")
    private String password;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private boolean emailVerified;

    private LocalDateTime emailVerifiedAt;

    private boolean isAdmin;

    private String profileImageUrl;

    @Column(nullable = false)
    private boolean pushEnabled = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt; // = LocalDateTime.now();

    @Builder
    public User(String email, String password, String username, boolean emailVerified) {
        this.email = email;
        this.password = password;
        this.username = username;
        this.emailVerified = emailVerified;
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

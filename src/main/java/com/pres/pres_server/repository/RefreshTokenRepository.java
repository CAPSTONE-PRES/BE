package com.pres.pres_server.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pres.pres_server.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByUserId(Long userId);

    Optional<RefreshToken> findByRefreshToken(String refreshToken);

    void deleteByUserId(Long userId);

}

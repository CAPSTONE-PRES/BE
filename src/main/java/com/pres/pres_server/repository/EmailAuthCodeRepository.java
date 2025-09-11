package com.pres.pres_server.repository;

import com.pres.pres_server.domain.EmailAuthCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailAuthCodeRepository extends JpaRepository<EmailAuthCode, Long> {
    Optional<EmailAuthCode> findByEmail(String email);

    void deleteByEmail(String email);

    void deleteAllByExpireAtBefore(LocalDateTime time);
}

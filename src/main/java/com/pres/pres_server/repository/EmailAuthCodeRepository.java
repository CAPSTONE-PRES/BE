package com.pres.pres_server.repository;

import com.pres.pres_server.domain.EmailAuthCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailAuthCodeRepository extends JpaRepository<EmailAuthCode, Long> {
    Optional<EmailAuthCode> findByEmail(String email);

    void deleteByEmail(String email);

    @Modifying
    @Transactional
    @Query("DELETE FROM EmailAuthCode e WHERE e.expireAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

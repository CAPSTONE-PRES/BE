package com.pres.pres_server.repository;

import com.pres.pres_server.domain.UserNotifications;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationsRepository extends JpaRepository<UserNotifications, Long> {
    Optional<UserNotifications> findByUserId(Long userId);
}

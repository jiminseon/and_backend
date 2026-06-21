package com.example.alert_module.notification.repository;

import com.example.alert_module.notification.entity.NotificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationTokenRepository extends JpaRepository<NotificationToken, Long> {
    Optional<NotificationToken> findByUserIdAndDeviceId(Long userId, String deviceId);
    List<NotificationToken> findByUserIdAndActiveTrue(Long userId);
}

package com.example.alert_module.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "notification_token", uniqueConstraints =
        @UniqueConstraint(name = "uk_notification_token_user_device", columnNames = {"user_id", "device_id"}))
public class NotificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "fcm_token")
    private String token;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public NotificationToken(Long userId, String deviceId) {
        this.userId = userId;
        this.deviceId = deviceId;
    }

    public void update(String token, boolean active) {
        if (token != null && !token.isBlank()) {
            this.token = token;
        }
        this.active = active;
    }
}

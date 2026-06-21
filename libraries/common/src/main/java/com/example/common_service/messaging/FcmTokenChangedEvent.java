package com.example.common_service.messaging;

public record FcmTokenChangedEvent(
        Long userId,
        String deviceId,
        String token,
        boolean active
) {
}

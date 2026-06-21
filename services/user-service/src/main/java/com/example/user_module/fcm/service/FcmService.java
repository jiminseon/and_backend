package com.example.user_module.fcm.service;

import com.example.user_module.fcm.repository.FcmRepository;
import com.example.common_service.messaging.FcmTokenChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FcmService {

    private final FcmRepository fcmRepository;
    private final FcmTokenEventPublisher eventPublisher;

    public void deactivateFcmToken(Long userId, String deviceId) {
        fcmRepository.deactivateToken(userId, deviceId);
        eventPublisher.publish(new FcmTokenChangedEvent(userId, deviceId, null, false));
    }
}

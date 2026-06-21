package com.example.alert_module.notification.message;

import com.example.alert_module.notification.entity.NotificationToken;
import com.example.alert_module.notification.repository.NotificationTokenRepository;
import com.example.common_service.config.RabbitMQConfig;
import com.example.common_service.messaging.FcmTokenChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FcmTokenChangedListener {

    private final NotificationTokenRepository repository;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.FCM_TOKEN_QUEUE)
    public void handle(FcmTokenChangedEvent event) {
        NotificationToken token = repository.findByUserIdAndDeviceId(event.userId(), event.deviceId())
                .orElseGet(() -> new NotificationToken(event.userId(), event.deviceId()));
        token.update(event.token(), event.active());
        repository.save(token);
    }
}

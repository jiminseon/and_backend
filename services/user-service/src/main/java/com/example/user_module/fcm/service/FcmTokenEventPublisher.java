package com.example.user_module.fcm.service;

import com.example.common_service.config.RabbitMQConfig;
import com.example.common_service.messaging.FcmTokenChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FcmTokenEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(FcmTokenChangedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ALERT_EXCHANGE,
                RabbitMQConfig.FCM_TOKEN_ROUTING_KEY,
                event);
    }
}

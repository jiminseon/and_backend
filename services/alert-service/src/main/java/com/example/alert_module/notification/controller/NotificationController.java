package com.example.alert_module.notification.controller;

import com.example.alert_module.notification.dto.PushMessage;
import com.example.alert_module.notification.factory.PushMessageFactory;
import com.example.alert_module.notification.service.NotificationService;
import com.example.common_service.security.AuthUser;
import com.example.alert_module.notification.entity.NotificationToken;
import com.example.alert_module.notification.repository.NotificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.el.util.MessageFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final PushMessageFactory messageFactory;
    private final NotificationTokenRepository notificationTokenRepository;

    @PostMapping("/push")
    public ResponseEntity<String> sendPush(@AuthUser Long userId, @RequestBody PushRequest request) {
        PushMessage pushMessage = messageFactory.test(request.title, request.body);

        List<NotificationToken> tokens = notificationTokenRepository.findByUserIdAndActiveTrue(userId);
        for (NotificationToken token : tokens) {
            log.info("Sending push notification: userId={}", userId);
            notificationService.send(token.getToken(), pushMessage);
        }

        return ResponseEntity.ok("푸시 전송 완료");
    }

    public record PushRequest(String title, String body) {}
}

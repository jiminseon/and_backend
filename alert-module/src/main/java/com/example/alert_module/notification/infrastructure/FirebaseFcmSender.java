package com.example.alert_module.notification.infrastructure;

import com.example.alert_module.notification.dto.PushMessage;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "alert.fcm.mode", havingValue = "real", matchIfMissing = true)
public class FirebaseFcmSender implements FcmSender {

    @Override
    public FcmMulticastResult sendMulticast(List<String> tokens, PushMessage message) {
        MulticastMessage multicastMessage = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putData("title", message.title())
                .putData("body", message.body())
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(multicastMessage);
            List<FcmSendFailure> failures = new ArrayList<>();
            List<SendResponse> responses = response.getResponses();

            for (int i = 0; i < responses.size(); i++) {
                SendResponse sendResponse = responses.get(i);
                if (sendResponse.isSuccessful()) {
                    continue;
                }

                FirebaseMessagingException ex = (FirebaseMessagingException) sendResponse.getException();
                MessagingErrorCode code = ex.getMessagingErrorCode();
                failures.add(new FcmSendFailure(tokens.get(i), String.valueOf(code), isRetryable(code)));
            }

            return new FcmMulticastResult(response.getSuccessCount(), failures);
        } catch (FirebaseMessagingException e) {
            log.error("[FCM] multicast failed code={}, message={}", e.getMessagingErrorCode(), e.getMessage());
            List<FcmSendFailure> failures = tokens.stream()
                    .map(token -> new FcmSendFailure(token, String.valueOf(e.getMessagingErrorCode()), false))
                    .toList();
            return new FcmMulticastResult(0, failures);
        }
    }

    @Override
    public String send(String token, PushMessage message) {
        Message fcmMessage = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putData("title", message.title())
                .putData("body", message.body())
                .build();

        try {
            return FirebaseMessaging.getInstance().send(fcmMessage);
        } catch (FirebaseMessagingException e) {
            log.error("[FCM] single send failed code={}, message={}", e.getErrorCode(), e.getMessage());
            return "";
        }
    }

    private boolean isRetryable(MessagingErrorCode code) {
        return code == MessagingErrorCode.INTERNAL || code == MessagingErrorCode.UNAVAILABLE;
    }
}

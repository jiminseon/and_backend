package com.example.alert_module.notification.infrastructure;

import com.example.alert_module.notification.dto.PushMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "alert.fcm.mode", havingValue = "fake")
public class FakeFcmSender implements FcmSender {

    private final long latencyMs;

    public FakeFcmSender(@Value("${alert.fcm.fake-latency-ms:20}") long latencyMs) {
        this.latencyMs = latencyMs;
    }

    @Override
    public FcmMulticastResult sendMulticast(List<String> tokens, PushMessage message) {
        sleep();
        log.debug("[FakeFCM] multicast tokens={}, title={}", tokens.size(), message.title());
        return new FcmMulticastResult(tokens.size(), List.of());
    }

    @Override
    public String send(String token, PushMessage message) {
        sleep();
        log.debug("[FakeFCM] single token={}, title={}", token, message.title());
        return "fake-fcm:" + token;
    }

    private void sleep() {
        if (latencyMs <= 0) {
            return;
        }

        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

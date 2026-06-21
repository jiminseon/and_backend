package com.example.alert_module.notification.service;

import com.example.alert_module.notification.dto.PushMessage;
import com.example.alert_module.notification.infrastructure.FcmMulticastResult;
import com.example.alert_module.notification.infrastructure.FcmSendFailure;
import com.example.alert_module.notification.infrastructure.FcmSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final FcmSender fcmSender;

    public void sendAll(List<String> tokens, PushMessage message) {
        if (tokens == null || tokens.isEmpty()) {
            log.warn("⚠️ FCM 토큰 없음, 전송 중단");
            return;
        }

        List<String> targets = new ArrayList<>(tokens);
        int maxAttempts = 3;
        int attempt = 1;

        while (attempt <= maxAttempts && !targets.isEmpty()) {
            FcmMulticastResult response = fcmSender.sendMulticast(targets, message);
            log.info("📊 [FCM 전송 결과 - 시도 {}회차] 전체={}, 성공={}, 실패={}",
                    attempt, targets.size(), response.successCount(), response.failureCount());

            List<String> retryTokens = new ArrayList<>();
            for (FcmSendFailure failure : response.failures()) {
                if (failure.retryable()) {
                    log.warn("🔁 [FCM 재시도 대상] token={}, error={}", failure.token(), failure.errorCode());
                    retryTokens.add(failure.token());
                } else {
                    log.error("⚠️ [FCM 재시도 제외] token={}, error={}", failure.token(), failure.errorCode());
                }
            }

            if (retryTokens.isEmpty()) {
                log.info("✅ [FCM 전송 완료] 시도 {}회차에 처리 완료", attempt);
                return;
            }

            targets = retryTokens;
            attempt++;
        }

        if (!targets.isEmpty()) {
            log.error("❌ [FCM 전송 최종 실패] 실패 토큰 개수={}", targets.size());
        }
    }

    public int sendEach(List<String> tokens, PushMessage message) {
        if (tokens == null || tokens.isEmpty()) {
            log.warn("⚠️ FCM 토큰 없음, 단건 반복 전송 중단");
            return 0;
        }

        int successCount = 0;
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }

            String response = fcmSender.send(token, message);
            if (response != null && !response.isBlank()) {
                successCount++;
            }
        }

        log.info("📊 [FCM 단건 반복 전송 완료] 전체={}, 성공={}", tokens.size(), successCount);
        return successCount;
    }


    public void send(String token, PushMessage message) {
        if (token == null || token.isBlank()) {
            log.warn("⚠️ 유효하지 않은 FCM 토큰: {}", token);
            return;
        }

        String response = fcmSender.send(token, message);
        log.info("✅ [단일 FCM 전송 완료] {}", response);
    }
}

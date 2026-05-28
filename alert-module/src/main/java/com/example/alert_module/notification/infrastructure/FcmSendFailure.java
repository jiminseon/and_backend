package com.example.alert_module.notification.infrastructure;

public record FcmSendFailure(
        String token,
        String errorCode,
        boolean retryable
) {
}

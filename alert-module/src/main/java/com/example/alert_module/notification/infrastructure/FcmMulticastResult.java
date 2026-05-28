package com.example.alert_module.notification.infrastructure;

import java.util.List;

public record FcmMulticastResult(
        int successCount,
        List<FcmSendFailure> failures
) {
    public int failureCount() {
        return failures == null ? 0 : failures.size();
    }
}

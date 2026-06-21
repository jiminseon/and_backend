package com.example.alert_module.notification.controller;

import com.example.alert_module.notification.dto.PushMessage;
import com.example.alert_module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@RestController
@RequiredArgsConstructor
@RequestMapping("/load-test/notifications")
@ConditionalOnProperty(name = "alert.load-test.enabled", havingValue = "true")
public class NotificationLoadTestController {

    private final NotificationService notificationService;

    @PostMapping("/multicast")
    public Map<String, Object> multicast(@RequestParam(defaultValue = "500") int tokenCount) {
        int safeTokenCount = Math.max(1, Math.min(tokenCount, 500));
        List<String> tokens = IntStream.range(0, safeTokenCount)
                .mapToObj(index -> "load-test-token-" + index)
                .toList();

        long startedAt = System.currentTimeMillis();
        notificationService.sendAll(tokens, new PushMessage("load-test", "multicast load test"));
        long elapsedMs = System.currentTimeMillis() - startedAt;

        return Map.of(
                "mode", "multicast",
                "requestedTokenCount", tokenCount,
                "sentTokenCount", safeTokenCount,
                "elapsedMs", elapsedMs
        );
    }

    @PostMapping("/single-loop")
    public Map<String, Object> singleLoop(@RequestParam(defaultValue = "500") int tokenCount) {
        int safeTokenCount = Math.max(1, Math.min(tokenCount, 500));
        List<String> tokens = IntStream.range(0, safeTokenCount)
                .mapToObj(index -> "load-test-token-" + index)
                .toList();

        long startedAt = System.currentTimeMillis();
        int successCount = notificationService.sendEach(tokens, new PushMessage("load-test", "single loop load test"));
        long elapsedMs = System.currentTimeMillis() - startedAt;

        return Map.of(
                "mode", "single-loop",
                "requestedTokenCount", tokenCount,
                "sentTokenCount", safeTokenCount,
                "successCount", successCount,
                "elapsedMs", elapsedMs
        );
    }
}

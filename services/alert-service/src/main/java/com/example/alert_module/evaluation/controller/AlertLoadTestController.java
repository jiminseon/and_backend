package com.example.alert_module.evaluation.controller;

import com.example.alert_module.evaluation.evaluator.service.AlertDetectService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/load-test/alerts")
@ConditionalOnProperty(name = "alert.load-test.enabled", havingValue = "true")
public class AlertLoadTestController {

    private final AlertDetectService alertDetectService;

    @PostMapping("/detect")
    public Map<String, Object> detect(@RequestParam(defaultValue = "005930") String stockCode) {
        long startedAt = System.currentTimeMillis();
        alertDetectService.detectForStock(stockCode);
        long elapsedMs = System.currentTimeMillis() - startedAt;

        return Map.of(
                "stockCode", stockCode,
                "elapsedMs", elapsedMs
        );
    }
}

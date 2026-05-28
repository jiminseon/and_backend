package com.example.alert_module.common.config;

import com.example.alert_module.evaluation.evaluator.service.AlertDetectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockUpdateListener implements MessageListener {

    private final AlertDetectService alertDetectService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String stockCode = new String(message.getBody()).replace("\"", "");
        log.info("📡 [SUBSCRIBE] Received stock update: {}", stockCode);

        try {
            alertDetectService.detectForStock(stockCode);
        } catch (Exception e) {
            log.error("❌ [SUBSCRIBE] stockCode={} 처리 중 예외 발생", stockCode, e);
        }
    }
}


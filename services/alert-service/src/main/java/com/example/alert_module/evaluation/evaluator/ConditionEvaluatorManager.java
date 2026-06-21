package com.example.alert_module.evaluation.evaluator;

import com.example.alert_module.evaluation.evaluator.type.ConditionTypeMapping;
import com.example.alert_module.management.entity.AlertConditionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ConditionEvaluatorManager {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ConditionEvaluator> evaluators = new HashMap<>();

    public ConditionEvaluatorManager(List<ConditionEvaluator> evaluatorList, StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        evaluatorList.forEach(evaluator -> {
            ConditionTypeMapping mapping = evaluator.getClass().getAnnotation(ConditionTypeMapping.class);
            if (mapping != null) {
                evaluators.put(mapping.value().name(), evaluator);
            }
        });
        log.info("✅ 등록된 Evaluator 수: {}", evaluators.size());
    }

    /**
     * (1) AlertCondition의 indicator로 evaluator 식별
     * (2) AlertCondition.dataScope으로 minute/daily Redis 선택
     */
    public boolean evaluate(AlertConditionManager manager, Map<String, Double> metrics) {
        String indicator = manager.getAlertCondition().getIndicator();
        ConditionEvaluator evaluator = evaluators.get(indicator);
        if (evaluator == null) {
            log.warn("⚠️ Evaluator 없음: {}", indicator);
            return false;
        }
        return evaluator.evaluate(manager, metrics);
    }

    /**
     * Redis에서 metric 데이터 로드 (minute/daily 선택)
     */
    public Map<String, Double> loadMetrics(AlertConditionManager manager) {
        String stockCode = manager.getAlert().getStockCode();
        String scope = manager.getAlertCondition().getDataScope();
        String redisKey = switch (scope) {
            case "daily" -> "daily:" + stockCode;
            case "minute" -> "minute:" + stockCode;
            default -> "stock:" + stockCode;
        };

        try {
            // ✅ Redis key 타입 확인
            var type = redisTemplate.type(redisKey);
            if (type == null) {
                log.warn("⚠️ [{}] Redis key {} not found", stockCode, redisKey);
                return Collections.emptyMap();
            }

            Map<String, Double> result = new HashMap<>();
            Map<Object, Object> raw;

            // ✅ 1️⃣ Hash 타입일 경우: HGETALL
            if (type.name().equalsIgnoreCase("hash")) {
                raw = redisTemplate.opsForHash().entries(redisKey);

                // ✅ 2️⃣ String(JSON) 타입일 경우: GET + JSON 파싱
            } else if (type.name().equalsIgnoreCase("string")) {
                String json = redisTemplate.opsForValue().get(redisKey);
                if (json == null || json.isBlank()) {
                    log.warn("⚠️ [{}] Redis key {} empty (string)", stockCode, redisKey);
                    return Collections.emptyMap();
                }
                Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
                raw = new HashMap<>(parsed);

                // ✅ 3️⃣ 기타 타입 (list, set 등)
            } else {
                log.warn("⚠️ [{}] Redis key {} has unsupported type: {}", stockCode, redisKey, type);
                return Collections.emptyMap();
            }

            // ✅ 공통 변환 로직 (Double로 변환 가능한 값만)
            raw.forEach((k, v) -> {
                try {
                    if (v instanceof Number n) {
                        result.put(k.toString(), n.doubleValue());
                    } else {
                        result.put(k.toString(), Double.parseDouble(v.toString()));
                    }
                } catch (Exception ignored) {
                    // date, stockCode 같은 문자열은 무시
                }
            });

            log.info("📊 [{}] metrics loaded from {} (type={}): {}", stockCode, redisKey, type, result.keySet());
            return result;

        } catch (Exception e) {
            log.error("❌ [{}] Redis loadMetrics 실패: {}", redisKey, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public Map<String, Double> loadMetricsForStock(AlertConditionManager manager, String stockCode) {
        String scope = manager.getAlertCondition().getDataScope();

        if ("minute".equalsIgnoreCase(scope)) {
            return readRedisMetrics("minute:" + stockCode);
        }

        if ("daily".equalsIgnoreCase(scope)) {
            return readRedisMetrics("daily:" + stockCode);
        }

        Map<String, Double> merged = new HashMap<>();
        merged.putAll(readRedisMetrics("daily:" + stockCode));
        merged.putAll(readRedisMetrics("minute:" + stockCode));
        return merged;
    }

    /**
     * Redis에서 key별 metric 데이터 읽기 (string/json 구조 모두 지원)
     */
    private Map<String, Double> readRedisMetrics(String redisKey) {
        try {
            String json = redisTemplate.opsForValue().get(redisKey);
            if (json == null || json.isBlank()) {
                log.warn("⚠️ Redis key {} not found or empty", redisKey);
                return Collections.emptyMap();
            }

            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Double> result = new HashMap<>();

            raw.forEach((k, v) -> {
                if (v instanceof Number n) result.put(k, n.doubleValue());
                else {
                    try { result.put(k, Double.parseDouble(v.toString())); }
                    catch (Exception ignored) {}
                }
            });

            log.debug("📊 Redis metrics loaded [{}]: {} keys", redisKey, result.keySet());
            return result;

        } catch (Exception e) {
            log.error("❌ Redis loadMetrics 실패 [{}]: {}", redisKey, e.getMessage());
            return Collections.emptyMap();
        }
    }


}

# 부하테스트용 코드 설명

이 문서는 부하테스트를 위해 추가한 코드와 설정이 어떤 역할을 하는지 설명합니다.

전체 목적은 세 가지입니다.

1. 로컬에서 MySQL, Redis, RabbitMQ, Grafana를 한 번에 띄운다.
2. 실제 Firebase를 호출하지 않고 FCM 전송 비용을 fake로 재현한다.
3. RabbitMQ 비동기 방식과 Direct 동기 방식을 같은 API로 비교한다.

## 1. 로컬 실행 환경

### `load-test/docker-compose.yml`

로컬 부하테스트에 필요한 인프라를 Docker로 띄웁니다.

포함 서비스:

| 서비스 | 역할 |
| --- | --- |
| MySQL | 사용자, 알림, 조건, 캔들 데이터 저장 |
| Redis | 알림 조건 평가에 필요한 실시간 metric 저장 |
| RabbitMQ | 알림 이벤트 비동기 처리 |
| Prometheus | Spring Boot actuator metric 수집 |
| InfluxDB | k6 실행 결과 저장 |
| Grafana | Prometheus/k6 결과 시각화 |

중요한 부분:

```yaml
mysql:
  volumes:
    - mysql-data:/var/lib/mysql
    - ./mysql:/docker-entrypoint-initdb.d:ro
```

MySQL container가 처음 만들어질 때 `load-test/mysql` 폴더 안의 SQL을 자동 실행합니다.

주의할 점:

MySQL init SQL은 volume이 처음 생성될 때만 실행됩니다. SQL을 바꿨는데 DB에 반영이 안 되면 아래 명령으로 volume을 지워야 합니다.

```bash
docker compose -f load-test/docker-compose.yml down -v
docker compose -f load-test/docker-compose.yml up -d
```

## 2. MySQL 스키마와 seed 데이터

### `load-test/mysql/01-schema.sql`

엔티티 기준으로 테이블을 생성합니다.

주요 매핑:

| Entity | Table |
| --- | --- |
| `UserEntity` | `user` |
| `FcmToken` | `fcm_token` |
| `Alert` | `alert` |
| `AlertCondition` | `alertCondition` |
| `AlertConditionManager` | `alertConditionManager` |
| `Company` | `company` |
| `AlertPrice` | `alertPrice` |
| `Preset` | `preset` |
| `PresetCondition` | `presetCondition` |
| `ConditionBase` | `condition_base` |
| `ConditionSearch` | `condition_search` |
| `ConditionSearchResult` | `condition_search_result` |
| `DailyCandleEntity`, `DailyCandle` | `daily_candle` |
| `MinuteCandleEntity` | `minuteCandle` |

예시:

```sql
CREATE TABLE IF NOT EXISTS alert (
  alert_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  is_actived BIT(1) NULL DEFAULT b'1',
  title VARCHAR(255) NULL,
  stock_code VARCHAR(50) NULL,
  is_triggered BIT(1) NULL DEFAULT b'0',
  ...
);
```

`AlertDetectService`는 아래 repository 메서드를 사용합니다.

```java
findByIsActivedAndStockCode(true, stockCode)
```

그래서 `alert` 테이블에는 `stock_code`, `is_actived` index를 추가했습니다.

```sql
KEY idx_alert_stock_active (stock_code, is_actived)
```

또 하나 중요한 부분:

```sql
CREATE TABLE IF NOT EXISTS alertConditionManager (
  alert_id BIGINT NOT NULL,
  alert_condition_id BIGINT NOT NULL,
  threshold DOUBLE NULL,
  threshold2 DOUBLE NULL,
  PRIMARY KEY (alert_id, alert_condition_id)
);
```

`AlertConditionManager`는 `@EmbeddedId` 복합키를 사용합니다. 그래서 `alert_id + alert_condition_id`가 PK입니다.

### `load-test/mysql/02-seed.sql`

부하테스트용 가상 데이터를 생성합니다.

생성되는 데이터:

| 데이터 | 개수 |
| --- | ---: |
| 사용자 | 100 |
| FCM 토큰 | 500 |
| 회사 | 5 |
| 알림 | 2,000 |
| 알림 조건 매핑 | 2,000 |
| 일봉 | 300 |
| 분봉 | 600 |

seed용 숫자 테이블을 먼저 만듭니다.

```sql
CREATE TABLE IF NOT EXISTS load_seed_numbers (
  n INT NOT NULL PRIMARY KEY
) ENGINE=Memory;
```

그리고 recursive CTE로 1부터 200까지 숫자를 채웁니다.

```sql
WITH RECURSIVE seq AS (
  SELECT 1 AS n
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 200
)
SELECT n FROM seq;
```

이 숫자를 이용해서 100명의 사용자와 2,000개의 알림을 생성합니다.

알림 ID는 계산식으로 고정합니다.

```sql
(u.n * 100000) + (c.company_no * 1000) + a.alert_no
```

예를 들어:

- user 1
- company 1
- alert 1

이면 alert id는 `101001`입니다.

이렇게 한 이유:

- seed를 여러 번 실행해도 `INSERT IGNORE`로 중복을 피할 수 있습니다.
- `alertConditionManager`, `condition_base`에서 같은 alert id를 쉽게 참조할 수 있습니다.

알림 조건은 4종류를 분산해서 넣었습니다.

| alert_no | condition | data_scope |
| --- | --- | --- |
| 1 | `PRICE_ABOVE` | `minute` |
| 2 | `RSI_UNDER` | `daily` |
| 3 | `SMA_20_UP` | `daily` |
| 4 | `VOLUME_CHANGE_PERCENT_UP` | `minute` |

이렇게 한 이유:

- Redis `minute:*`와 `daily:*`를 둘 다 읽게 만들기 위해서입니다.
- 가격, RSI, SMA, 거래량 조건이 골고루 평가됩니다.

## 3. Redis seed

### `load-test/scripts/seed-redis.sh`

알림 조건 평가 로직이 읽는 Redis 데이터를 넣습니다.

`ConditionEvaluatorManager`는 조건의 `dataScope`에 따라 Redis key를 선택합니다.

```java
case "daily" -> "daily:" + stockCode;
case "minute" -> "minute:" + stockCode;
```

그래서 seed script도 같은 이름으로 데이터를 넣습니다.

```bash
redis-cli set "minute:005930" '{"price":70500,...}'
redis-cli set "daily:005930" '{"rsi14":38,"sma20":69000,...}'
```

중요한 metric 이름:

| Redis field | 쓰는 evaluator |
| --- | --- |
| `price` | `PRICE_ABOVE`, `PRICE_BELOW` |
| `volumeRatio` | `VOLUME_CHANGE_PERCENT_UP/DOWN` |
| `rsi14` | `RSI_OVER`, `RSI_UNDER` |
| `sma20` | `SMA_20_UP/DOWN` |
| `bbUpper`, `bbLower` | Bollinger 조건 |

Redis 값이 없으면 조건 평가가 false가 됩니다. 그래서 MySQL seed만 넣고 Redis seed를 안 하면 알림이 거의 트리거되지 않습니다.

## 4. loadtest profile

### `alert-module/src/main/resources/application-loadtest.yml`

`alert-module`을 로컬 부하테스트 환경으로 실행하기 위한 설정입니다.

주요 설정:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/andDB...
  data:
    redis:
      host: localhost
      port: 6379
  rabbitmq:
    host: localhost
    port: 5672
```

기존 `application.yml`에는 배포용 RabbitMQ 주소가 들어있기 때문에, 로컬 테스트에서는 반드시 `loadtest` profile로 실행해야 합니다.

```bash
./gradlew :alert-module:bootRun --args='--spring.profiles.active=loadtest'
```

추가 설정:

```yaml
alert:
  load-test:
    enabled: true
  notification:
    dispatch-mode: rabbitmq
  fcm:
    mode: fake
    fake-latency-ms: 20
```

의미:

| 설정 | 의미 |
| --- | --- |
| `alert.load-test.enabled=true` | 부하테스트 전용 API 활성화 |
| `dispatch-mode=rabbitmq` | 기본은 RabbitMQ 비동기 방식 |
| `fcm.mode=fake` | 실제 Firebase 호출 대신 fake sender 사용 |
| `fake-latency-ms=20` | FCM 호출 1회당 20ms 지연 흉내 |

### `data-process-module/src/main/resources/application-loadtest.yml`

`data-process-module`도 같은 로컬 MySQL, Redis, RabbitMQ를 보도록 만든 profile입니다.

현재 실험은 주로 `alert-module`의 `/load-test/*` API로 진행하지만, 추후 `data-process-module`의 ingest API까지 연결해서 Redis publish 흐름을 테스트할 때 사용합니다.

## 5. FCM fake 처리 코드

실제 Firebase를 부하테스트에서 호출하면 문제가 많습니다.

- 외부 네트워크 상태가 결과에 섞입니다.
- Firebase rate limit 영향을 받습니다.
- 실제 사용자에게 알림이 갈 위험이 있습니다.
- 포트폴리오에서 재현 가능한 실험이 어렵습니다.

그래서 FCM 호출부를 interface로 분리했습니다.

### `FcmSender`

파일:

```text
alert-module/src/main/java/com/example/alert_module/notification/infrastructure/FcmSender.java
```

역할:

```java
public interface FcmSender {
    FcmMulticastResult sendMulticast(List<String> tokens, PushMessage message);
    String send(String token, PushMessage message);
}
```

`NotificationService`는 이제 Firebase SDK를 직접 알지 않고 `FcmSender`만 호출합니다.

### `FirebaseFcmSender`

실제 Firebase를 호출하는 구현체입니다.

활성 조건:

```java
@ConditionalOnProperty(name = "alert.fcm.mode", havingValue = "real", matchIfMissing = true)
```

즉, 기본값은 real입니다. 운영 환경에서는 기존처럼 실제 Firebase를 사용합니다.

하는 일:

1. `MulticastMessage` 생성
2. `FirebaseMessaging.getInstance().sendEachForMulticast(...)` 호출
3. 실패한 token을 `FcmSendFailure`로 변환
4. `INTERNAL`, `UNAVAILABLE`은 retryable로 표시

### `FakeFcmSender`

부하테스트용 fake 구현체입니다.

활성 조건:

```java
@ConditionalOnProperty(name = "alert.fcm.mode", havingValue = "fake")
```

`application-loadtest.yml`에서 `alert.fcm.mode=fake`로 설정했기 때문에 부하테스트 때는 이 클래스가 사용됩니다.

하는 일:

```java
Thread.sleep(latencyMs);
return new FcmMulticastResult(tokens.size(), List.of());
```

즉, 실제 Firebase로 보내지는 않고 설정한 시간만큼 기다린 뒤 모두 성공한 것처럼 응답합니다.

이 코드 덕분에 FCM 전송 비용을 실험에 포함하면서도 외부 의존성을 제거할 수 있습니다.

### `FcmInitializer`

기존에는 앱 시작 시 항상 Firebase credential 파일을 찾았습니다.

부하테스트 fake 모드에서는 Firebase가 필요 없기 때문에 아래 조건을 추가했습니다.

```java
@ConditionalOnProperty(name = "alert.fcm.mode", havingValue = "real", matchIfMissing = true)
```

이제 fake 모드에서는 Firebase 초기화를 하지 않습니다.

## 6. NotificationService 변경

파일:

```text
alert-module/src/main/java/com/example/alert_module/notification/service/NotificationService.java
```

기존 구조:

```java
FirebaseMessaging.getInstance().sendEachForMulticast(...)
```

변경 후:

```java
private final FcmSender fcmSender;
FcmMulticastResult response = fcmSender.sendMulticast(targets, message);
```

역할은 그대로입니다.

1. token 목록이 비어있으면 중단
2. 최대 3회 재시도
3. retryable 실패 token만 다시 시도
4. 최종 실패 로그 출력

달라진 점은 Firebase SDK에 직접 의존하지 않는다는 것입니다.

이렇게 하면:

- 운영에서는 `FirebaseFcmSender`
- 부하테스트에서는 `FakeFcmSender`

로 자동 전환됩니다.

## 7. RabbitMQ vs Direct 비교 코드

파일:

```text
alert-module/src/main/java/com/example/alert_module/notification/event/AlertEventPublisher.java
```

기존에는 무조건 RabbitMQ로 publish했습니다.

```java
rabbitTemplate.convertAndSend(exchange, routingKey, event);
```

변경 후에는 설정으로 분기합니다.

```java
@Value("${alert.notification.dispatch-mode:rabbitmq}")
private String dispatchMode;
```

Direct 모드:

```java
if ("direct".equalsIgnoreCase(dispatchMode)) {
    if ("CONDITION".equalsIgnoreCase(alertType)) {
        pushService.sendCondition(event);
    } else {
        pushService.send(event);
    }
    return;
}
```

RabbitMQ 모드:

```java
rabbitTemplate.convertAndSend(
    RabbitMQConfig.ALERT_EXCHANGE,
    routingKey,
    event
);
```

이 코드가 실험의 핵심입니다.

같은 API 요청과 같은 DB/Redis 데이터에서 아래 두 가지 방식만 바꿔 비교할 수 있습니다.

```bash
--alert.notification.dispatch-mode=rabbitmq
--alert.notification.dispatch-mode=direct
```

## 8. 부하테스트 전용 API

### `AlertLoadTestController`

파일:

```text
alert-module/src/main/java/com/example/alert_module/evaluation/controller/AlertLoadTestController.java
```

API:

```http
POST /load-test/alerts/detect?stockCode=005930
```

하는 일:

```java
alertDetectService.detectForStock(stockCode);
```

즉, Redis pub/sub 이벤트가 들어온 것처럼 특정 종목의 알림 평가를 강제로 실행합니다.

왜 만들었는가:

원래 흐름은 Redis pub/sub 메시지를 받아 `StockUpdateListener`가 실행됩니다. 하지만 k6로 Redis pub/sub을 직접 발생시키고 측정하기는 번거롭습니다. 그래서 같은 핵심 서비스인 `AlertDetectService`를 HTTP endpoint로 열어, k6가 쉽게 부하를 줄 수 있게 했습니다.

보안:

```java
@ConditionalOnProperty(name = "alert.load-test.enabled", havingValue = "true")
```

`loadtest` profile에서만 켜집니다.

응답:

```json
{
  "stockCode": "005930",
  "elapsedMs": 123
}
```

### `NotificationLoadTestController`

파일:

```text
alert-module/src/main/java/com/example/alert_module/notification/controller/NotificationLoadTestController.java
```

API:

```http
POST /load-test/notifications/multicast?tokenCount=500
POST /load-test/notifications/single-loop?tokenCount=500
```

하는 일:

1. fake token 목록 생성
2. multicast API는 `NotificationService.sendAll(...)` 호출
3. single-loop API는 `NotificationService.sendEach(...)` 호출
3. 걸린 시간 반환

token 수는 1부터 500 사이로 제한했습니다.

```java
int safeTokenCount = Math.max(1, Math.min(tokenCount, 500));
```

왜 500인가:

FCM multicast는 한 번에 최대 500개 token을 보내는 방식이 일반적입니다. 그래서 부하테스트도 500개를 상한으로 잡았습니다.

이 API의 핵심 비교 기준은 다음입니다.

| API | 비교 의미 |
| --- | --- |
| `/multicast` | 같은 토큰 묶음을 FCM multicast 1회로 처리 |
| `/single-loop` | 같은 토큰 묶음을 FCM 단건 전송 N회 반복으로 처리 |

`FakeFcmSender` 기준으로 multicast는 fake latency가 1번 발생하고, single-loop는 token 개수만큼 발생합니다. 그래서 500개 토큰 실험에서 두 방식의 차이가 분명하게 보입니다.

## 9. k6 스크립트

### `load-test/k6/alert-detect.js`

알림 평가 전체 흐름을 테스트합니다.

요청:

```javascript
http.post(`${BASE_URL}/load-test/alerts/detect?stockCode=${stockCode}`)
```

종목은 5개를 순환합니다.

```javascript
const STOCKS = ['005930', '000660', '035420', '035720', '005380'];
const stockCode = STOCKS[__ITER % STOCKS.length];
```

부하 패턴:

```javascript
stages: [
  { duration: '30s', target: 5 },
  { duration: '1m', target: 20 },
  { duration: '30s', target: 0 },
]
```

의미:

1. 30초 동안 VU 5까지 증가
2. 1분 동안 VU 20 유지
3. 30초 동안 종료

이 스크립트는 RabbitMQ 방식과 Direct 방식을 비교할 때 사용합니다.

### `load-test/k6/notification-multicast.js`

FCM multicast 처리만 따로 테스트합니다.

요청:

```javascript
http.post(`${BASE_URL}/load-test/notifications/multicast?tokenCount=${TOKEN_COUNT}`)
```

기본 token 수:

```javascript
const TOKEN_COUNT = __ENV.TOKEN_COUNT || '500';
```

실행 예:

```bash
TOKEN_COUNT=100 K6_OUT=influxdb=http://localhost:8086/k6 k6 run load-test/k6/notification-multicast.js
TOKEN_COUNT=500 K6_OUT=influxdb=http://localhost:8086/k6 k6 run load-test/k6/notification-multicast.js
```

이 스크립트는 multicast endpoint만 빠르게 확인할 때 사용합니다.

### `load-test/k6/notification-send-mode.js`

FCM multicast와 단건 반복 전송을 비교하는 메인 스크립트입니다.

실행 예:

```bash
SEND_MODE=multicast TOKEN_COUNT=500 K6_OUT=influxdb=http://localhost:8086/k6 k6 run load-test/k6/notification-send-mode.js
SEND_MODE=single-loop TOKEN_COUNT=500 K6_OUT=influxdb=http://localhost:8086/k6 k6 run load-test/k6/notification-send-mode.js
```

요청 endpoint는 `SEND_MODE`에 따라 달라집니다.

| SEND_MODE | endpoint |
| --- | --- |
| `multicast` | `/load-test/notifications/multicast` |
| `single-loop` | `/load-test/notifications/single-loop` |

이 스크립트는 같은 token 수에서 전송 방식만 바꿔 비교할 때 사용합니다.

## 10. Prometheus와 Grafana 설정

### `load-test/prometheus.yml`

Spring Boot actuator metric을 수집합니다.

```yaml
scrape_configs:
  - job_name: alert-module
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["host.docker.internal:8083"]
```

Docker container 안의 Prometheus가 host machine에서 실행 중인 Spring Boot 앱을 보려면 `host.docker.internal`을 사용합니다.

### `load-test/grafana/provisioning/datasources/datasources.yml`

Grafana datasource를 자동 등록합니다.

등록되는 datasource:

1. Prometheus
2. k6 InfluxDB

Prometheus는 JVM/HTTP metric을 보고, InfluxDB는 k6 결과를 봅니다.

## 11. 실제 비교 시 코드 흐름

### RabbitMQ 방식

1. k6가 `/load-test/alerts/detect` 호출
2. `AlertLoadTestController`
3. `AlertDetectService.detectForStock(stockCode)`
4. MySQL에서 활성 알림 조회
5. Redis에서 metric 조회
6. 조건 평가
7. `AlertEventPublisher.publish(...)`
8. RabbitMQ publish
9. `AlertNotifier`가 consume
10. `PushService`
11. FCM token 조회
12. `NotificationService`
13. `FakeFcmSender`

특징:

API 요청 흐름은 8번에서 알림 전송을 RabbitMQ로 넘기고 빠져나올 수 있습니다. 실제 push 처리는 consumer가 뒤에서 합니다.

### Direct 방식

1. k6가 `/load-test/alerts/detect` 호출
2. `AlertLoadTestController`
3. `AlertDetectService.detectForStock(stockCode)`
4. MySQL에서 활성 알림 조회
5. Redis에서 metric 조회
6. 조건 평가
7. `AlertEventPublisher.publish(...)`
8. `PushService` 직접 호출
9. FCM token 조회
10. `NotificationService`
11. `FakeFcmSender`
12. API 응답

특징:

알림 전송까지 요청 흐름 안에서 처리합니다. 구현은 단순하지만 요청 시간이 길어질 수 있습니다.

## 12. 왜 이렇게 설계했는가

### 비교 조건을 통제하기 위해

RabbitMQ 방식과 Direct 방식을 비교하려면 DB 데이터, Redis 값, 요청 패턴은 같아야 합니다. 그래서 `dispatch-mode` 하나만 바꿔서 실험하도록 만들었습니다.

### 외부 변수를 제거하기 위해

실제 Firebase를 호출하면 네트워크와 Firebase 서버 상태가 결과에 영향을 줍니다. 그래서 fake FCM을 넣었습니다.

### 포트폴리오에서 설명하기 쉽게

아래 질문에 답할 수 있는 구조로 만들었습니다.

- 비동기 메시징을 왜 썼는가?
- RabbitMQ를 쓰면 어떤 지표가 좋아지는가?
- 대신 어떤 운영 복잡도가 생기는가?
- FCM multicast와 단건 반복 전송은 응답시간이 얼마나 차이나는가?
- 부하테스트 결과를 어디서 확인했는가?

## 13. 발표할 때 말하면 좋은 요약

> 알림 조건 평가는 Redis의 실시간 가격/지표 데이터를 읽고 MySQL의 사용자 알림 조건과 비교한다. 조건이 충족되면 기존에는 알림 발행 이후 FCM 전송까지 한 흐름에서 처리될 수 있는데, 이 실험에서는 RabbitMQ를 사이에 둔 비동기 방식과 직접 호출 방식을 설정값 하나로 전환할 수 있게 만들었다. 또한 실제 Firebase 호출은 외부 변수라서 fake sender로 대체했고, k6 + Prometheus + Grafana로 p95 latency, 처리량, queue backlog, JVM/DB 상태를 비교했다.

## 14. 내가 추가한 코드 한 줄 요약

| 파일 | 한 줄 요약 |
| --- | --- |
| `FcmSender.java` | FCM 전송을 interface로 추상화 |
| `FirebaseFcmSender.java` | 실제 Firebase 전송 구현 |
| `FakeFcmSender.java` | 부하테스트용 fake FCM 구현 |
| `NotificationService.java` | Firebase 직접 호출 대신 `FcmSender` 사용 |
| `FcmInitializer.java` | real FCM 모드에서만 Firebase 초기화 |
| `AlertEventPublisher.java` | RabbitMQ/direct dispatch mode 분기 |
| `AlertLoadTestController.java` | 알림 평가 부하테스트용 API |
| `NotificationLoadTestController.java` | multicast 부하테스트용 API |
| `application-loadtest.yml` | 로컬 부하테스트 profile |
| `01-schema.sql` | 엔티티 기반 MySQL schema |
| `02-seed.sql` | 포트폴리오용 가상 데이터 |
| `seed-redis.sh` | Redis metric seed |
| `alert-detect.js` | 알림 평가 k6 시나리오 |
| `notification-multicast.js` | multicast k6 시나리오 |
| `notification-send-mode.js` | multicast vs 단건 반복 k6 시나리오 |

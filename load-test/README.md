# Local Load-Test Data

이 폴더는 포트폴리오 부하테스트용 로컬 인프라와 seed 데이터를 제공합니다.

## 포함 데이터

- users: 100명
- fcm_token: 사용자당 5개, 총 500개
- company: 5개 종목
- alert: 사용자당 20개, 총 2,000개
- alertConditionManager: 알림당 1개 조건
- daily_candle: 종목당 60개
- minuteCandle: 종목당 120개
- Redis `daily:*`, `minute:*` metric seed

## 실행

```bash
docker compose -f load-test/docker-compose.yml up -d
bash load-test/scripts/seed-redis.sh
```

MySQL init SQL은 volume이 처음 만들어질 때만 자동 실행됩니다. seed를 처음부터 다시 넣고 싶으면 `mysql-data` volume을 지우고 다시 올리면 됩니다.

```bash
docker compose -f load-test/docker-compose.yml down -v
docker compose -f load-test/docker-compose.yml up -d
```

Grafana: http://localhost:3000 (`admin` / `admin`)
RabbitMQ: http://localhost:15672 (`admin` / `admin123`)

## 비교 실험

RabbitMQ 비동기:

```bash
./gradlew :alert-module:bootRun --args='--spring.profiles.active=loadtest --alert.notification.dispatch-mode=rabbitmq'
```

동기 직접 처리:

```bash
./gradlew :alert-module:bootRun --args='--spring.profiles.active=loadtest --alert.notification.dispatch-mode=direct'
```

알림 평가 부하:

```bash
K6_OUT=influxdb=http://localhost:8086/k6 k6 run load-test/k6/alert-detect.js
```

FCM multicast 부하:

```bash
K6_OUT=influxdb=http://localhost:8086/k6 k6 run load-test/k6/notification-multicast.js
```

FCM multicast vs 단건 반복 비교:

```bash
SEND_MODE=multicast TOKEN_COUNT=500 K6_OUT=influxdb=http://localhost:8086/k6 k6 run load-test/k6/notification-send-mode.js
SEND_MODE=single-loop TOKEN_COUNT=500 K6_OUT=influxdb=http://localhost:8086/k6 k6 run load-test/k6/notification-send-mode.js
```

# nGrinder & Pinpoint 실험 가이드

이 문서는 `Direct 방식`과 `RabbitMQ 방식`의 부하 생성 및 병목 추적을 nGrinder와 Pinpoint로 확인하는 방법입니다.

역할은 명확히 나누면 됩니다.

| 도구 | 역할 |
| --- | --- |
| nGrinder | 같은 API에 일정한 부하를 생성 |
| Pinpoint | 요청 trace를 보고 시간이 어디서 쓰였는지 추적 |
| RabbitMQ Management | queue backlog, publish/consume rate 확인 |
| MySQL/Redis | seed 데이터와 metric 상태 확인 |

공식 참고:

- Pinpoint Docker는 Pinpoint 팀의 공식 dockerized components 저장소를 사용합니다: https://github.com/pinpoint-apm/pinpoint-docker
- Pinpoint 공식 문서도 Docker 설치는 공식 Pinpoint-Docker 저장소를 보라고 안내합니다: https://pinpoint-apm.github.io/pinpoint/docker.html
- nGrinder는 Controller와 Agent로 구성됩니다. Docker image는 `ngrinder/controller`, `ngrinder/agent`를 사용합니다: https://hub.docker.com/r/ngrinder/controller/

## 1. 실험에서 비교하는 것

비교 대상은 FCM 전송 방식이 아니라 **알림 이벤트 처리 구조**입니다.

| 실험 | 설정 | 의미 |
| --- | --- | --- |
| Direct | `--alert.notification.dispatch-mode=direct` | 조건 평가 요청 안에서 `PushService`까지 직접 처리 |
| RabbitMQ | `--alert.notification.dispatch-mode=rabbitmq` | 조건 평가 후 RabbitMQ에 이벤트를 넣고 consumer가 알림 처리 |

둘 다 같은 API에 부하를 줍니다.

```http
POST /load-test/alerts/detect?stockCode=005930
```

즉, nGrinder 스크립트는 똑같고, `alert-module` 실행 옵션만 바꿔서 비교합니다.

## 2. 애플리케이션 내부 흐름

### Direct

```text
nGrinder
-> AlertLoadTestController
-> AlertDetectService.detectForStock(stockCode)
-> AlertRepository.findByIsActivedAndStockCode(...)
-> Redis minute/daily metric 조회
-> AlertEvaluationService
-> AlertEventPublisher
-> PushService 직접 호출
-> FcmRepository.findByUserIdAndActivedTrue(...)
-> AlertHistoryRepository.save(...)
-> NotificationService
-> FakeFcmSender
-> HTTP 응답
```

Direct 방식은 요청 trace 안에 `PushService`, `FcmRepository`, `AlertHistoryRepository`, `NotificationService` 시간이 같이 보이는 것이 정상입니다.

### RabbitMQ

```text
nGrinder
-> AlertLoadTestController
-> AlertDetectService.detectForStock(stockCode)
-> AlertRepository.findByIsActivedAndStockCode(...)
-> Redis minute/daily metric 조회
-> AlertEvaluationService
-> AlertEventPublisher
-> RabbitTemplate.convertAndSend(...)
-> HTTP 응답

RabbitMQ consumer thread
-> AlertNotifier
-> PushService
-> FcmRepository.findByUserIdAndActivedTrue(...)
-> AlertHistoryRepository.save(...)
-> NotificationService
-> FakeFcmSender
```

RabbitMQ 방식은 HTTP 요청 trace가 짧아지고, 알림 처리는 별도 consumer thread trace로 분리되는 것이 정상입니다.

## 3. 준비

### 3.1 로컬 인프라 실행

```bash
docker compose -f load-test/docker-compose.yml up -d
```

### 3.2 Redis seed

```bash
bash load-test/scripts/seed-redis.sh
```

### 3.3 MySQL seed 확인

```bash
docker exec -it $(docker compose -f load-test/docker-compose.yml ps -q mysql) mysql -uroot -proot andDB
```

확인 SQL:

```sql
SELECT COUNT(*) FROM alert;
SELECT COUNT(*) FROM alertConditionManager;
SELECT COUNT(*) FROM fcm_token;
SELECT stock_code, COUNT(*) FROM alert GROUP BY stock_code;
```

기대값:

| 테이블 | 기대값 |
| --- | ---: |
| `alert` | 2000 |
| `alertConditionManager` | 2000 |
| `fcm_token` | 500 |

## 4. Pinpoint 붙이기

Pinpoint는 “부하 결과 숫자”보다 **trace 내부의 병목 위치**를 보는 도구입니다.

### 4.1 Pinpoint 서버 실행

Pinpoint는 공식 `pinpoint-docker` 저장소를 사용하는 것이 가장 안전합니다.

```bash
git clone https://github.com/pinpoint-apm/pinpoint-docker.git
cd pinpoint-docker
docker compose up -d
```

실행 후 Pinpoint Web UI에 접속합니다.

```text
http://localhost:8080
```

Pinpoint 버전에 따라 Web/Collector port와 agent 설정 키가 조금 다를 수 있습니다. 그래서 Pinpoint 서버는 공식 `pinpoint-docker`의 README 기준으로 실행하고, agent 설정도 해당 버전의 `pinpoint-agent` 문서를 따르는 것을 권장합니다.

### 4.1.1 Mac에서 3306 port 충돌이 날 때

아래 에러가 나면 Pinpoint docker-compose 안의 MySQL이 host의 3306 port를 잡으려다가 실패한 것입니다.

```text
ports are not available: exposing port TCP 0.0.0.0:3306
bind: address already in use
```

먼저 3306을 누가 쓰는지 확인합니다.

```bash
lsof -i :3306
docker ps --format 'table {{.Names}}\t{{.Ports}}'
```

해결 방법은 두 가지입니다.

방법 A. 기존 MySQL을 잠깐 끄기

```bash
brew services stop mysql
```

또는 Docker container가 3306을 쓰고 있으면 해당 container를 중지합니다.

```bash
docker stop <container-name>
```

방법 B. Pinpoint MySQL host port를 바꾸기

Pinpoint 내부 container끼리는 `mysql:3306`으로 통신하므로 host port는 꼭 3306일 필요가 없습니다. `pinpoint-docker/docker-compose.yml`에서 MySQL service의 ports를 찾습니다.

```yaml
ports:
  - "3306:3306"
```

왼쪽 host port만 바꿉니다.

```yaml
ports:
  - "3308:3306"
```

그 다음 다시 실행합니다.

```bash
docker compose up -d
```

이미 일부 container가 만들어진 상태라면 정리 후 다시 올립니다.

```bash
docker compose down
docker compose up -d
```

### 4.1.2 Apple Silicon platform 경고

M1/M2/M3 Mac에서 아래 경고가 보일 수 있습니다.

```text
The requested image's platform (linux/amd64) does not match the detected host platform (linux/arm64/v8)
```

이건 대부분 경고입니다. Docker가 amd64 image를 emulation으로 실행할 수 있으면 그대로 진행됩니다. 다만 실행이 불안정하거나 container가 바로 죽으면 아래처럼 platform을 명시해서 다시 실행합니다.

```bash
DOCKER_DEFAULT_PLATFORM=linux/amd64 docker compose up -d
```

그래도 안 되면 `docker-compose.yml`의 문제가 되는 service에 아래 설정을 추가합니다.

```yaml
platform: linux/amd64
```

현재 사용자가 만난 에러에서는 platform 경고보다 3306 port 충돌이 먼저 해결해야 할 문제입니다.

### 4.2 alert-module에 Pinpoint agent 붙이기

Pinpoint agent를 받은 뒤 `alert-module` 실행 시 JVM 옵션에 javaagent를 붙입니다.

예시:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
JAVA_TOOL_OPTIONS="-javaagent:/path/to/pinpoint-agent/pinpoint-bootstrap.jar \
-Dpinpoint.agentId=alert-module-direct \
-Dpinpoint.applicationName=and-alert-module" \
./gradlew :alert-module:bootRun --args='--spring.profiles.active=loadtest --alert.notification.dispatch-mode=direct'
```

RabbitMQ 방식은 `agentId`만 바꿔서 실행합니다.

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
JAVA_TOOL_OPTIONS="-javaagent:/path/to/pinpoint-agent/pinpoint-bootstrap.jar \
-Dpinpoint.agentId=alert-module-rabbitmq \
-Dpinpoint.applicationName=and-alert-module" \
./gradlew :alert-module:bootRun --args='--spring.profiles.active=loadtest --alert.notification.dispatch-mode=rabbitmq'
```

Pinpoint collector 주소를 따로 지정해야 하는 버전이면 agent 설정에 collector host를 추가합니다. 설정 키는 Pinpoint agent 버전에 따라 다르므로, 사용하는 agent의 `pinpoint-root.config` 또는 공식 문서를 기준으로 맞춥니다.

## 5. nGrinder로 부하 만들기

### 5.1 nGrinder 실행

간단히 Docker로 Controller와 Agent를 띄울 수 있습니다.

```bash
docker run -d --name ngrinder-controller \
  -p 8088:80 \
  -p 16001:16001 \
  -p 12000-12009:12000-12009 \
  ngrinder/controller
```

Agent:

```bash
docker run -d --name ngrinder-agent \
  --link ngrinder-controller:controller \
  ngrinder/agent
```

Controller 접속:

```text
http://localhost:8088
```

기본 계정은 nGrinder image/버전에 따라 다를 수 있으니 Docker Hub 또는 Controller 화면 안내를 확인합니다.

### 5.2 nGrinder script 업로드

사용할 스크립트:

```text
load-test/ngrinder/AlertDetectTest.groovy
```

이 스크립트는 아래 API를 반복 호출합니다.

```http
POST http://host.docker.internal:8083/load-test/alerts/detect?stockCode={stockCode}
```

종목은 5개를 순환합니다.

```groovy
["005930", "000660", "035420", "035720", "005380"]
```

### 5.3 nGrinder 테스트 설정 예시

처음에는 작게 시작합니다.

| 항목 | 값 |
| --- | --- |
| Agent | 1 |
| Vuser per agent | 10 |
| Duration | 3분 |
| Ramp-up | 30초 |

그다음 비교 실험에서는 같은 설정을 유지합니다.

| 실험 | alert-module 실행 옵션 | nGrinder 설정 |
| --- | --- | --- |
| Direct | `dispatch-mode=direct` | 동일 |
| RabbitMQ | `dispatch-mode=rabbitmq` | 동일 |

중요합니다. 부하 조건이 달라지면 비교가 깨집니다. nGrinder 설정은 두 실험에서 그대로 유지하세요.

## 6. Pinpoint에서 무엇을 볼까

### 6.1 Direct 방식에서 볼 것

Pinpoint transaction trace에서 아래 메서드들이 HTTP 요청 안에 길게 보이면 정상입니다.

```text
AlertLoadTestController.detect
AlertDetectService.detectForStock
AlertEvaluationService.evaluateAlert
AlertEventPublisher.publish
PushService.send
FcmRepository.findByUserIdAndActivedTrue
AlertHistoryRepository.save
NotificationService.sendAll
FakeFcmSender.sendMulticast
```

해석:

Direct는 요청 thread가 알림 처리까지 담당하므로, DB 조회/저장과 FCM 처리 시간이 HTTP 응답시간에 직접 포함됩니다.

### 6.2 RabbitMQ 방식에서 볼 것

HTTP 요청 trace에서는 주로 여기까지 보여야 합니다.

```text
AlertLoadTestController.detect
AlertDetectService.detectForStock
AlertEvaluationService.evaluateAlert
AlertEventPublisher.publish
RabbitTemplate.convertAndSend
```

그리고 별도 consumer trace에서 아래 흐름이 보여야 합니다.

```text
AlertNotifier.handleCompany
PushService.send
FcmRepository.findByUserIdAndActivedTrue
AlertHistoryRepository.save
NotificationService.sendAll
FakeFcmSender.sendMulticast
```

해석:

RabbitMQ는 HTTP 요청과 알림 처리를 분리합니다. 그래서 HTTP p95는 낮아질 수 있지만, queue backlog가 쌓이면 알림 도착은 늦어질 수 있습니다.

## 7. RabbitMQ Management에서 볼 것

주소:

```text
http://localhost:15672
```

확인할 queue:

```text
alert.company.queue
alert.condition.queue
```

볼 지표:

| 지표 | 의미 |
| --- | --- |
| Ready | 아직 consumer가 가져가지 않은 메시지 |
| Unacked | consumer가 처리 중인 메시지 |
| Publish rate | 메시지가 들어오는 속도 |
| Deliver rate | 메시지가 consumer로 전달되는 속도 |

RabbitMQ 방식에서 `Ready`가 계속 증가하면 consumer 처리량이 publish 속도를 못 따라가는 것입니다.

이 경우 포트폴리오 해석은 이렇게 할 수 있습니다.

> RabbitMQ를 적용해 HTTP 응답시간은 줄였지만, consumer 처리량이 부족하면 queue backlog가 증가해 알림 전송 지연이 생긴다. 따라서 비동기화 이후에는 consumer concurrency, prefetch, queue monitoring이 중요하다.

## 8. nGrinder 결과에서 볼 것

nGrinder report에서 볼 핵심 지표:

| 지표 | 의미 |
| --- | --- |
| TPS | 초당 처리 요청 수 |
| Mean Test Time | 평균 응답시간 |
| Peak TPS | 가장 높았던 처리량 |
| Errors | 실패 요청 |

비교 포인트:

| 기대 방향 | 설명 |
| --- | --- |
| RabbitMQ TPS 증가 | 요청 thread가 알림 전송까지 붙잡히지 않음 |
| RabbitMQ 평균 응답시간 감소 | Push/FCM/history 저장이 요청 밖으로 빠짐 |
| Direct 응답시간 증가 | 요청 안에서 알림 처리까지 수행 |
| RabbitMQ queue backlog 가능 | consumer가 느리면 메시지가 쌓임 |

## 9. 실험 전 상태 초기화

Direct와 RabbitMQ를 공정하게 비교하려면 실험 전 DB 상태를 초기화합니다.

```sql
UPDATE alert SET is_triggered = b'0', last_notified_at = NULL;
DELETE FROM alertHistory;
```

RabbitMQ queue도 비우는 것이 좋습니다.

RabbitMQ Management UI에서 queue purge를 하거나, container 안에서 CLI로 처리합니다.

```bash
docker exec -it $(docker compose -f load-test/docker-compose.yml ps -q rabbitmq) rabbitmqctl purge_queue alert.company.queue
docker exec -it $(docker compose -f load-test/docker-compose.yml ps -q rabbitmq) rabbitmqctl purge_queue alert.condition.queue
```

## 10. 추천 실험 순서

1. Docker 인프라 실행
2. Redis seed 입력
3. MySQL seed count 확인
4. Pinpoint 서버 실행
5. Pinpoint agent 붙여서 Direct 방식으로 alert-module 실행
6. nGrinder `AlertDetectTest.groovy` 실행
7. nGrinder report 저장
8. Pinpoint trace 캡처
9. DB/queue 초기화
10. Pinpoint agentId를 바꿔 RabbitMQ 방식으로 alert-module 실행
11. 같은 nGrinder 설정으로 재실행
12. RabbitMQ queue 지표와 Pinpoint consumer trace 확인

## 11. 포트폴리오에 적을 결론 틀

결론은 단순히 “RabbitMQ가 빠르다”가 아니라 이렇게 쓰는 게 좋습니다.

> Direct 방식은 구현이 단순하지만, 알림 조건 평가 이후 FCM 토큰 조회, 알림 이력 저장, FCM 전송 준비가 같은 요청 thread에서 실행되어 부하 증가 시 응답시간이 길어졌다. RabbitMQ 방식은 알림 이벤트를 queue로 넘겨 HTTP 요청과 알림 처리를 분리했기 때문에 nGrinder 기준 응답시간과 TPS가 개선되었다. 다만 Pinpoint와 RabbitMQ Management에서 확인한 것처럼, consumer 처리량이 부족하면 queue backlog가 쌓일 수 있어 consumer concurrency와 queue 모니터링이 필요하다.

## 12. 발표할 때 보여줄 캡처

추천 캡처:

1. nGrinder Direct report
2. nGrinder RabbitMQ report
3. Pinpoint Direct transaction trace
4. Pinpoint RabbitMQ HTTP trace
5. Pinpoint RabbitMQ consumer trace
6. RabbitMQ queue publish/deliver rate 화면

이 6개를 보여주면 “부하 생성 -> 병목 추적 -> 구조 개선 해석” 흐름이 자연스럽게 이어집니다.

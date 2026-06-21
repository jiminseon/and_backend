# AND backend

Java 17 기반 Spring Boot MSA입니다.

| Service | Responsibility |
| --- | --- |
| services/user-service | 사용자, 로그인, JWT 발급/갱신, 기기 등록 |
| services/alert-service | 알림 설정/평가/이력, FCM 발송 |
| services/market-data-service | 시세 수집/변환/저장/발행 |
| libraries/common | 서비스 간 계약과 공통 기반 코드(실행 불가) |

구조와 의존 규칙은 [ARCHITECTURE.md](ARCHITECTURE.md), Java 17 실행 방법은 [docs/operations/local-development.md](docs/operations/local-development.md)를 참고하세요.

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew clean check
```

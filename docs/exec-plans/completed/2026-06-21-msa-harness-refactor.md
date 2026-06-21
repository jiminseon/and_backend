# MSA harness refactor

Status: completed on 2026-06-21.

## Decisions

- Preserve three independent Spring Boot deployment units.
- Make the root Gradle project an aggregator.
- Make common code a non-executable library.
- Replace the alert-to-user compile dependency with JWT verification and an FCM token event/read model.
- Sign tokens only in user-service with an RSA private key.
- Enforce service import boundaries in `verifyArchitecture`.
- Standardize local and CI builds on Java 17.

## Verification

- `./gradlew clean check`
- `:user-module:bootJar`
- `:alert-module:bootJar`
- `:data-process-module:bootJar`

## Follow-up

See [tech-debt.md](../tech-debt.md) for outbox, backfill, JWKS rotation, and integration-test work.

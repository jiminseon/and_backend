# Repository map

This repository contains independently deployable Spring Boot services. Keep this file short; detailed decisions live under `docs/`.

## Start here

- `ARCHITECTURE.md`: service boundaries and dependency rules
- `docs/operations/local-development.md`: Java 17 setup and verification commands
- `docs/design-docs/authentication.md`: JWT issuance and validation flow
- `docs/exec-plans/tech-debt.md`: known follow-up work

## Non-negotiable rules

- Use Java 17.
- Services never import another service's implementation package.
- Only user-service signs access and refresh tokens.
- Resource services validate access tokens locally with the RSA public key.
- Refresh tokens and user credentials never leave user-service.
- Run `./gradlew clean check` before merging.
- Never commit private keys, Firebase credentials, `.env`, or real `application.yml` files.

## Services

- `services/user-service`: identity, credentials, refresh tokens, FCM device registration
- `services/alert-service`: alerts, evaluation, notification delivery, notification-token read model
- `services/market-data-service`: market-data ingestion, transformation, persistence and publishing
- `libraries/common`: small cross-service contracts and infrastructure configuration; it is not executable

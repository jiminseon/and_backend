# AND backend architecture

## Deployment units

The directories under `services/` are separate Spring Boot processes. Each service must be buildable, deployable, observable, and backed by data it owns.

`libraries/common` is a library. It must not contain a `main` method or import a service implementation package.

## Allowed compile-time dependencies

```text
services/user-service        ─┐
services/alert-service       ─┼──> libraries/common
services/market-data-service ─┘
```

No service may depend on another service project. `verifyArchitecture` enforces this rule.

## Runtime communication

- Synchronous client requests carry a user access token.
- Each resource service validates the token locally.
- Asynchronous integration uses RabbitMQ contracts from `libraries/common`.
- User-service publishes FCM token changes; alert-service stores an eventually consistent notification-token read model.

## Data ownership

- user-service owns users, credentials, refresh tokens and canonical device registrations.
- alert-service owns alerts, histories, presets and its notification-token read model.
- market-data-service owns candle and market-data ingestion records.

Cross-service database joins and repositories are forbidden.

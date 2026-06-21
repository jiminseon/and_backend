# Technical debt

- Introduce an outbox for FCM token events so a database commit and RabbitMQ publication cannot diverge.
- Backfill existing user-service FCM tokens into alert-service before cutover.
- Replace the shared RabbitMQ topology configuration with per-service producer/consumer configuration.
- Split API response, messaging contracts, and security contracts out of the broad common module.
- Publish JWKS with `kid` and automate RSA key rotation.
- Add Testcontainers integration tests for MySQL, Redis, and RabbitMQ.
- Add contract tests for events and public APIs.

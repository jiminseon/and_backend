# Authentication in the MSA

## Flow

1. user-service verifies the email and password.
2. user-service signs an RSA (`RS256`) access token with its private key.
3. The token contains `sub`, numeric `userId`, `iss`, `aud`, `iat`, and `exp` claims.
4. alert-service and market-data-service verify the signature with the public key and validate issuer, audience, and expiry.
5. Controllers receive `userId` only after successful verification through `@AuthUser`.
6. Refresh tokens are processed and persisted only by user-service.

Resource services do not query the user database during authentication. This keeps them available when user-service is temporarily unavailable and removes a cross-service database dependency.

## Key handling

- The private key is available only to user-service through a secret manager or mounted secret.
- The public key is distributed to resource services.
- Production should expose a JWKS endpoint and support overlapping keys during rotation.
- Do not put private keys in images, Git, application YAML files, or CI logs.

## Remaining production hardening

- Add `kid` and a JWKS endpoint for zero-downtime key rotation.
- Add short access-token lifetime and refresh-token reuse detection.
- Define service-to-service credentials for endpoints that do not act on behalf of a user.

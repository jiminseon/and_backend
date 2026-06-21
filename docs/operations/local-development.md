# Local development

## Runtime

Java 17 is required. Confirm with:

```shell
java -version
```

If another JDK is the system default on macOS:

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

## Configuration

Copy each service's `application-example.yml` to `application.yml`, provide separate databases, and mount the same RSA public key into all services. Mount the private key only into `services/user-service`.

Generate disposable local keys with:

```shell
./scripts/generate-dev-rsa-keys.sh
export JWT_PRIVATE_KEY="file:$PWD/.secrets/jwt/private-key.pem"
export JWT_PUBLIC_KEY="file:$PWD/.secrets/jwt/public-key.pem"
```

Never reuse these local keys in a deployed environment.

## Verification

```shell
./gradlew clean check
./gradlew :user-module:bootRun
./gradlew :alert-module:bootRun
./gradlew :data-process-module:bootRun
```

The root project is an aggregator and is not an executable application.

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        REGISTRY = 'ghcr.io'
        IMAGE_PREFIX = 'ghcr.io/jiminseon/and-backend'
        PROD_HOST = credentials('prod-host')
        PROD_USER = 'deploy'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.IMAGE_TAG = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                }
            }
        }

        stage('Test and package') {
            environment {
                JAVA_HOME = '/usr/lib/jvm/java-17-openjdk'
            }
            steps {
                sh '''
                    export PATH="$JAVA_HOME/bin:$PATH"
                    java -version
                    javac -version
                    ./gradlew clean check \
                      :user-module:bootJar \
                      :alert-module:bootJar \
                      :data-process-module:bootJar \
                      --no-daemon
                '''
            }
        }

        stage('Build and push images') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'ghcr-credentials',
                    usernameVariable: 'GHCR_USER',
                    passwordVariable: 'GHCR_TOKEN'
                )]) {
                    sh '''
                        set +x
                        echo "$GHCR_TOKEN" | docker login "$REGISTRY" -u "$GHCR_USER" --password-stdin
                        set -x

                        docker build -f services/user-service/Dockerfile \
                          -t "$IMAGE_PREFIX-user-service:$IMAGE_TAG" services/user-service
                        docker push "$IMAGE_PREFIX-user-service:$IMAGE_TAG"

                        docker build -f services/alert-service/Dockerfile \
                          -t "$IMAGE_PREFIX-alert-service:$IMAGE_TAG" services/alert-service
                        docker push "$IMAGE_PREFIX-alert-service:$IMAGE_TAG"

                        docker build -f services/market-data-service/Dockerfile \
                          -t "$IMAGE_PREFIX-market-data-service:$IMAGE_TAG" services/market-data-service
                        docker push "$IMAGE_PREFIX-market-data-service:$IMAGE_TAG"
                    '''
                }
            }
        }

        stage('Deploy production') {
            when {
                branch 'main'
            }
            steps {
                sshagent(credentials: ['prod-ssh-key']) {
                    withCredentials([usernamePassword(
                        credentialsId: 'ghcr-credentials',
                        usernameVariable: 'GHCR_USER',
                        passwordVariable: 'GHCR_TOKEN'
                    )]) {
                        sh '''
                            ssh -o StrictHostKeyChecking=yes "$PROD_USER@$PROD_HOST" \
                              'mkdir -p /opt/and-backend/infra/nginx /opt/and-backend/infra/mysql/init'

                            scp -o StrictHostKeyChecking=yes docker-compose.prod.yml \
                              "$PROD_USER@$PROD_HOST:/opt/and-backend/docker-compose.prod.yml"
                            scp -o StrictHostKeyChecking=yes infra/nginx/nginx.conf \
                              "$PROD_USER@$PROD_HOST:/opt/and-backend/infra/nginx/nginx.conf"
                            scp -o StrictHostKeyChecking=yes infra/mysql/init/01-create-databases.sql \
                              "$PROD_USER@$PROD_HOST:/opt/and-backend/infra/mysql/init/01-create-databases.sql"

                            set +x
                            echo "$GHCR_TOKEN" | ssh -o StrictHostKeyChecking=yes "$PROD_USER@$PROD_HOST" \
                              "docker login ghcr.io -u '$GHCR_USER' --password-stdin"
                            set -x

                            ssh -o StrictHostKeyChecking=yes "$PROD_USER@$PROD_HOST" \
                              "NEW_TAG='$IMAGE_TAG' bash -s" <<'REMOTE_DEPLOY'
                                set -Eeuo pipefail

                                cd /opt/and-backend

                                COMPOSE_FILE=docker-compose.prod.yml
                                DEPLOYED_TAG_FILE=.deployed-image-tag
                                APP_SERVICES=(user-service market-data-service alert-service)

                                check_health() {
                                    local attempt

                                    for attempt in $(seq 1 12); do
                                        if curl --fail --silent http://127.0.0.1/health/user >/dev/null \
                                            && curl --fail --silent http://127.0.0.1/health/alert >/dev/null \
                                            && curl --fail --silent http://127.0.0.1/health/market-data >/dev/null; then
                                            return 0
                                        fi

                                        sleep 5
                                    done

                                    return 1
                                }

                                if [ -s "$DEPLOYED_TAG_FILE" ]; then
                                    PREVIOUS_TAG=$(tr -d '[:space:]' < "$DEPLOYED_TAG_FILE")
                                else
                                    USER_CONTAINER=$(docker compose -f "$COMPOSE_FILE" ps -q user-service)
                                    PREVIOUS_IMAGE=$(docker inspect --format '{{.Config.Image}}' "$USER_CONTAINER")
                                    PREVIOUS_TAG=${PREVIOUS_IMAGE##*:}
                                fi

                                if ! printf '%s' "$PREVIOUS_TAG" | grep -Eq '^[0-9a-f]{40}$'; then
                                    echo "Cannot determine the previous immutable image tag: $PREVIOUS_TAG" >&2
                                    exit 1
                                fi

                                printf '%s\n' "$PREVIOUS_TAG" > "$DEPLOYED_TAG_FILE.tmp"
                                mv "$DEPLOYED_TAG_FILE.tmp" "$DEPLOYED_TAG_FILE"

                                rollback() {
                                    local deploy_status=$?

                                    trap - ERR
                                    echo "Deployment failed. Rolling back to $PREVIOUS_TAG" >&2

                                    if IMAGE_TAG="$PREVIOUS_TAG" docker compose -f "$COMPOSE_FILE" pull "${APP_SERVICES[@]}" \
                                        && IMAGE_TAG="$PREVIOUS_TAG" docker compose -f "$COMPOSE_FILE" up -d --no-deps --wait --wait-timeout 300 "${APP_SERVICES[@]}" \
                                        && docker compose -f "$COMPOSE_FILE" exec -T nginx nginx -s reload \
                                        && check_health; then
                                        printf '%s\n' "$PREVIOUS_TAG" > "$DEPLOYED_TAG_FILE.tmp"
                                        mv "$DEPLOYED_TAG_FILE.tmp" "$DEPLOYED_TAG_FILE"
                                        echo "Rollback completed and health checks passed." >&2
                                    else
                                        echo "Rollback failed. Manual recovery is required." >&2
                                    fi

                                    exit "$deploy_status"
                                }

                                trap rollback ERR

                                IMAGE_TAG="$NEW_TAG" docker compose -f "$COMPOSE_FILE" pull "${APP_SERVICES[@]}"
                                IMAGE_TAG="$NEW_TAG" docker compose -f "$COMPOSE_FILE" up -d --no-deps --wait --wait-timeout 300 "${APP_SERVICES[@]}"
                                docker compose -f "$COMPOSE_FILE" exec -T nginx nginx -s reload
                                check_health

                                printf '%s\n' "$NEW_TAG" > "$DEPLOYED_TAG_FILE.tmp"
                                mv "$DEPLOYED_TAG_FILE.tmp" "$DEPLOYED_TAG_FILE"
                                trap - ERR

                                echo "Deployment completed: $NEW_TAG"
REMOTE_DEPLOY
                        '''
                    }
                }
            }
            post {
                success {
                    sh '''
                        mkdir -p /var/lib/jenkins/and-deployments
                        printf '{"timestamp":"%s","event":"deployment","environment":"prod","status":"success","git_sha":"%s","build_number":"%s","build_url":"%s"}\n' \
                          "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$IMAGE_TAG" "$BUILD_NUMBER" "$BUILD_URL" \
                          >> /var/lib/jenkins/and-deployments/events.jsonl
                    '''
                }
                failure {
                    sh '''
                        mkdir -p /var/lib/jenkins/and-deployments
                        printf '{"timestamp":"%s","event":"deployment","environment":"prod","status":"failure","git_sha":"%s","build_number":"%s","build_url":"%s"}\n' \
                          "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$IMAGE_TAG" "$BUILD_NUMBER" "$BUILD_URL" \
                          >> /var/lib/jenkins/and-deployments/events.jsonl
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker logout ghcr.io || true'
            cleanWs(deleteDirs: true, disableDeferredWipeout: true)
        }
    }
}

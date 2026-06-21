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
            steps {
                sh '''
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
                              "cd /opt/and-backend && IMAGE_TAG='$IMAGE_TAG' docker compose -f docker-compose.prod.yml pull && IMAGE_TAG='$IMAGE_TAG' docker compose -f docker-compose.prod.yml up -d --remove-orphans --wait --wait-timeout 300"

                            ssh -o StrictHostKeyChecking=yes "$PROD_USER@$PROD_HOST" \
                              'curl --fail --silent http://127.0.0.1/health/user >/dev/null && curl --fail --silent http://127.0.0.1/health/alert >/dev/null && curl --fail --silent http://127.0.0.1/health/market-data >/dev/null'
                        '''
                    }
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

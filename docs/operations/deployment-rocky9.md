# Rocky Linux 9 + Vultr 배포

## 리소스 전제

- Jenkins 2GB: 2GB swap을 추가하고 동시 빌드를 금지한다.
- Prod 1GB: 현재 구성은 실행할 수 없다. 최소 4GB, 권장 8GB로 증설한다.
- 두 서버 모두 AMD64(x86_64) Rocky Linux 9를 기준으로 한다.

## Vultr 방화벽

Jenkins 서버는 관리자 IP에서 오는 `22`, `8080`만 허용한다. Prod 서버는 관리자 IP와 Jenkins 서버 IP의 `22`, 전체 인터넷의 `80`을 허용한다. `443`은 도메인과 TLS를 적용할 때 개방한다. 애플리케이션, MySQL, Redis, RabbitMQ 포트는 공개하지 않는다.

## 공통 Docker 설치

두 서버에서 root로 실행한다.

```bash
dnf -y update
dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
dnf -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
```

Prod 서버에는 배포 전용 사용자를 만든다.

```bash
useradd --create-home deploy
usermod -aG docker deploy
mkdir -p /home/deploy/.ssh /opt/and-backend/secrets/jwt /opt/and-backend/secrets/firebase
chown -R deploy:deploy /home/deploy/.ssh /opt/and-backend
chmod 700 /home/deploy/.ssh
```

Jenkins 서버의 배포 공개키를 `/home/deploy/.ssh/authorized_keys`에 등록하고 권한을 `600`, 소유자를 `deploy:deploy`로 설정한다.

## Jenkins 서버 준비

2GB swap을 추가한다.

```bash
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

Jenkins 컨트롤러용 Java 21과 프로젝트 빌드용 Java 17 JDK를 함께 설치한다. Jenkins는 Docker 컨테이너가 아니라 Rocky Linux의 systemd 서비스로 실행한다.

```bash
dnf -y install fontconfig java-21-openjdk java-17-openjdk-devel git wget
wget -O /etc/yum.repos.d/jenkins.repo https://pkg.jenkins.io/rpm-stable/jenkins.repo
rpm --import https://pkg.jenkins.io/rpm-stable/jenkins.io-2023.key
dnf -y install jenkins
usermod -aG docker jenkins
systemctl daemon-reload
systemctl enable --now jenkins
```

Jenkins 서비스는 Java 21로 실행하고 빌드는 Jenkinsfile이 `/usr/lib/jvm/java-17-openjdk`를 사용한다.

```bash
systemctl edit jenkins
```

편집기에 다음을 입력하고 저장한다.

```ini
[Service]
Environment="JENKINS_JAVA_CMD=/usr/lib/jvm/java-21-openjdk/bin/java"
```

설정을 반영하고 두 Java 버전을 확인한다.

```bash
systemctl daemon-reload
systemctl restart jenkins
sudo -u jenkins java -version
sudo -u jenkins /usr/lib/jvm/java-17-openjdk/bin/javac -version
sudo -u jenkins docker version
```

Jenkins 설치 후 `Pipeline`, `Git`, `SSH Agent`, `Credentials Binding`, `Workspace Cleanup` 플러그인을 설치한다. Jenkins가 Docker 그룹을 인식하도록 Jenkins를 한 번 재시작한다.

## Jenkins Credentials

다음 ID로 등록한다.

| ID | 종류 | 값 |
| --- | --- | --- |
| `ghcr-credentials` | Username with password | GitHub 사용자명 + `write:packages`, `read:packages` PAT |
| `prod-host` | Secret text | Prod 서버 공인 IP |
| `prod-ssh-key` | SSH Username with private key | Username `deploy` + 배포 개인키 |

Jenkins 서버의 `jenkins` 사용자로 Prod에 한 번 SSH 접속하여 서버 지문을 확인하고 `known_hosts`에 등록한다. 파이프라인은 등록되지 않은 서버 지문을 자동 수락하지 않는다.

## Prod 비밀값 준비

`/opt/and-backend/.env`를 `.env.prod.example`을 참고해 직접 만든다. Git이나 Jenkins workspace에 이 파일을 저장하지 않는다.

JWT 키는 다음 위치에 설치한다.

```text
/opt/and-backend/secrets/jwt/private-key.pem
/opt/and-backend/secrets/jwt/public-key.pem
```

Firebase를 실제 사용하면 서비스 계정 JSON을 다음 위치에 설치하고 `.env`의 `ALERT_FCM_MODE=real`로 변경한다.

```text
/opt/and-backend/secrets/firebase/firebase-admin.json
```

비밀 파일 권한은 `600`, 디렉터리는 `700`으로 제한한다.

## 첫 배포

GitHub public 저장소를 대상으로 Jenkins Pipeline job을 만들고 저장소의 `Jenkinsfile`을 사용한다. 처음에는 기능 브랜치에서 이미지 빌드까지만 검증하고, `main` 빌드가 Prod 배포를 수행한다.

도메인이 없는 동안 `http://PROD_IP`로 테스트하며 `.env`에서 `AUTH_COOKIE_SECURE=false`를 사용한다. 이 상태는 App Store 운영용이 아니다. 도메인과 HTTPS를 연결한 후 `AUTH_COOKIE_SECURE=true`로 반드시 변경한다.

## 롤백

Jenkins는 배포 전 현재 이미지 태그를 `/opt/and-backend/.deployed-image-tag`에 보관한다. 새 애플리케이션 이미지 배포, Nginx reload 또는 Health Check가 실패하면 앱 서비스 3개를 이전 태그로 자동 롤백하고 Health Check를 다시 수행한다. 롤백에 성공해도 문제가 있는 신규 배포를 나타내기 위해 Jenkins 빌드는 실패로 남는다.

자동 롤백까지 실패한 경우 이전 성공 빌드의 Git 커밋 해시를 사용해 수동 복구한다.

```bash
cd /opt/and-backend
IMAGE_TAG=<previous-commit-sha> docker compose -f docker-compose.prod.yml pull
IMAGE_TAG=<previous-commit-sha> docker compose -f docker-compose.prod.yml up -d --wait
```

현재 `JPA_DDL_AUTO=update`는 빈 DB의 최초 배포를 위한 임시 설정이다. 실제 사용자 데이터를 받기 전에 Flyway 마이그레이션을 추가하고 `validate`로 변경한다.

Prometheus, Grafana, Loki와 배포 로그 수집은 [monitoring.md](monitoring.md)를 따른다.

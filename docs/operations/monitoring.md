# Prometheus + Grafana + Loki monitoring

## Architecture

| Role | Private IP | Components |
| --- | --- | --- |
| Jenkins | `10.34.96.4` | Jenkins, Node Exporter, Alloy |
| Prod | `10.34.96.3` | Application stack, Node Exporter, Alloy |
| Monitoring | `10.34.96.5` | Prometheus, Loki, Grafana, Node Exporter |

Prometheus scrapes application, Jenkins, and host metrics over the Vultr VPC. Alloy sends Prod Docker logs and Jenkins logs to Loki. Grafana reads Prometheus and Loki and sends Grafana-managed alerts through the Slack Contact Point.

Do not expose Prometheus `9090`, Loki `3100`, Alloy `12345`, or Node Exporter `9100` to the public internet. Grafana `3000` must be restricted to the administrator IP until HTTPS and authentication are placed in front of it.

## 1. Start the monitoring server

Copy `infra/monitoring` to `/opt/and-monitoring` on the Monitoring server. The final directory must contain `docker-compose.monitoring.yml`, `prometheus`, `loki`, and `grafana` at the same level.

```bash
cd /opt/and-monitoring
cp .env.monitoring.example .env
chmod 600 .env
vi .env

docker compose --env-file .env -f docker-compose.monitoring.yml config
docker compose --env-file .env -f docker-compose.monitoring.yml pull
docker compose --env-file .env -f docker-compose.monitoring.yml up -d
docker compose --env-file .env -f docker-compose.monitoring.yml ps
```

Set a random `GRAFANA_ADMIN_PASSWORD` in `.env`. Do not commit the real `.env` file.

Verify the services from the Monitoring server.

```bash
curl --fail http://127.0.0.1:3000/api/health
curl --fail http://10.34.96.5:9090/-/ready
curl --fail http://10.34.96.5:3100/ready
```

## 2. Start the Prod collectors

Copy these files to `/opt/and-monitoring-agent` on the Prod server.

```text
infra/monitoring/agents/docker-compose.prod-agent.yml
infra/monitoring/agents/prod-config.alloy
```

Then start the collectors.

```bash
cd /opt/and-monitoring-agent
docker compose -f docker-compose.prod-agent.yml config
docker compose -f docker-compose.prod-agent.yml pull
docker compose -f docker-compose.prod-agent.yml up -d
docker compose -f docker-compose.prod-agent.yml ps
```

Node Exporter binds only to `10.34.96.3:9100`. Alloy discovers Docker containers through the Docker socket and sends their logs to `10.34.96.5:3100`.

The Docker socket grants broad read access to Docker metadata and logs. The Alloy container must not be exposed publicly or shared with untrusted users.

## 3. Start the Jenkins collectors

Create the deployment event directory before starting the Alloy container. Creating it through Docker first would make it owned by `root`, preventing the native Jenkins service from writing deployment events.

```bash
install -d -o jenkins -g jenkins -m 750 /var/lib/jenkins/and-deployments
mkdir -p /opt/and-monitoring-agent
```

Copy these files to `/opt/and-monitoring-agent` on the Jenkins server.

```text
infra/monitoring/agents/docker-compose.jenkins-agent.yml
infra/monitoring/agents/jenkins-config.alloy
```

Start the collectors.

```bash
cd /opt/and-monitoring-agent
docker compose -f docker-compose.jenkins-agent.yml config
docker compose -f docker-compose.jenkins-agent.yml pull
docker compose -f docker-compose.jenkins-agent.yml up -d
docker compose -f docker-compose.jenkins-agent.yml ps
```

Alloy collects:

- `jenkins.service` logs from systemd journal
- Jenkins build console logs modified in the last 24 hours
- structured deployment events from `/var/lib/jenkins/and-deployments/events.jsonl`

Jenkins Credentials Binding masks registered secrets in console output, but deployment scripts must still use `set +x` around secret-handling commands. Do not print tokens or secret file contents in pipeline scripts.

## 4. Deploy the application metrics route

The repository Nginx configuration exposes the following routes only to Monitoring IP `10.34.96.5`.

```text
/internal/metrics/user
/internal/metrics/alert
/internal/metrics/market-data
```

The next successful `main` pipeline copies the Nginx configuration and deploys application images that allow `/actuator/prometheus`. Requests from other source IPs receive `403`.

Verify from the Monitoring server after deployment.

```bash
curl --fail http://10.34.96.3/internal/metrics/user | head
curl --fail http://10.34.96.3/internal/metrics/alert | head
curl --fail http://10.34.96.3/internal/metrics/market-data | head
curl --fail http://10.34.96.4:8080/prometheus/ | head
curl --fail http://10.34.96.3:9100/metrics | head
curl --fail http://10.34.96.4:9100/metrics | head
```

## 5. Grafana and Slack

Grafana automatically provisions:

- Prometheus and Loki data sources
- `AND Production Overview` dashboard
- `AND Deployments` dashboard
- service-down, Jenkins-down, and HTTP-5xx Grafana-managed alert rules

In Grafana, open `Alerting > Notification policies` and make the existing Slack Contact Point the default receiver, or route alerts with these labels to Slack.

```text
environment = prod | ci
severity = critical | warning
```

Use `Send test notification` on the Slack Contact Point before relying on the alert rules.

## 6. Verification

Open Prometheus `Status > Targets`. Every target should report `UP`.

Expected targets:

```text
prometheus
monitoring-node
prod-node
jenkins-node
prod-user-service
prod-market-data-service
prod-alert-service
jenkins
```

Generate one normal application request and one Jenkins deployment. Confirm that:

1. Spring metrics appear in the Production dashboard.
2. Prod container logs appear in Grafana Explore with `{environment="prod"}`.
3. Jenkins logs appear with `{service=~"jenkins|jenkins-build"}`.
4. The deployment event appears with `{service="jenkins-deployment"}`.
5. The Slack Contact Point test arrives in the intended channel.

## Retention and backup

- Prometheus retention: 15 days or 20 GB, whichever is reached first.
- Loki retention: 7 days.
- Grafana dashboards are provisioned from Git; Grafana users and UI-created Contact Points remain in the `grafana-data` volume.

Back up the `grafana-data` volume or separately document the Slack Contact Point. Loki and Prometheus data are operational history and can be omitted from backups if losing old telemetry is acceptable.


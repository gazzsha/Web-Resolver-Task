# Monitoring stack — Prometheus + Grafana + Alertmanager (SLO/SLA)

## Запуск

```bash
# Базовая инфраструктура + полный мониторинг-стек
docker compose --profile monitoring up -d \
  prometheus alertmanager grafana \
  postgres-exporter kafka-exporter node-exporter

# Spring Boot приложение
./gradlew :MainApplication:bootRun
```

- Prometheus: http://localhost:9090
- Alertmanager: http://localhost:9093
- Grafana: http://localhost:3000 (anonymous Viewer; admin/admin для редактирования)
- Postgres exporter: http://localhost:9187/metrics
- Kafka exporter: http://localhost:9308/metrics
- Node exporter: http://localhost:9100/metrics — **на macOS не стартует**: Docker
  Desktop запрещает mount-propagation `rslave` на `/`. Полноценно работает на
  Linux-хосте (prod-стенд). На dev-машине просто игнорируется.

## Что собирается

| Слой | Метрики |
|------|---------|
| Spring Boot Actuator | `http_server_requests_seconds_*` (histogram + SLO buckets), `jvm_*`, `process_*` |
| AI Analyzer | `ai_analyzer_calls_total{variant,outcome}`, `ai_analyzer_latency_seconds_*`, `ai_analyzer_schema_valid_total` |
| Sandbox | `sandbox_execution_seconds_*{language,verdict}`, `sandbox_oom_killed_total` |
| Worker | `worker_processing_seconds_*`, `worker_submissions_total{status}` |
| PostgreSQL | `pg_up`, `pg_stat_*`, `pg_database_size_bytes`, `pg_settings_max_connections`, `pg_locks_count` |
| Kafka | `kafka_consumergroup_lag`, `kafka_topic_*`, `kafka_brokers` |
| Node | `node_cpu_*`, `node_memory_*`, `node_filesystem_*` (только Linux) |
| Alertmanager | `alertmanager_*` (self-monitoring) |

## Безопасность `/actuator/prometheus`

Endpoint защищён двумя слоями (defense-in-depth, `MainApplication/src/main/kotlin/ru/security/SecurityConfig.kt`):

1. **IP-allowlist** — приходят только запросы из CIDR `172.16.0.0/12` (Docker
   bridge-сеть), `127.0.0.1/32`, `::1`.
2. **Basic-auth** — пользователь `prometheus-scraper` с ролью `OPS` (in-memory
   UserDetailsManager). Пароль читается из `${PROMETHEUS_SCRAPER_PASSWORD}`
   (см. `.env.example`); для dev-стенда дефолт `scraper-dev-pass`.

Prometheus читает пароль из смонтированного файла `secrets/scraper_password`
(каталог `secrets/` в `.gitignore`). Для production:
- удалить дефолт в `application.properties`, добавить fail-fast валидацию;
- заменить in-memory store на внешний (Vault / Kubernetes Secret);
- ротировать пароль вместе с redeploy.

## Дашборды (auto-provisioned)

Provision'ятся автоматически из `monitoring/grafana/dashboards/`:

| Файл | UID | Назначение |
|------|-----|------------|
| `slo-dashboard.json` | `web-resolver-slo` | HTTP RPS, error-rate, latency p50/p95/p99, submission start, AI fallback, sandbox p95 |
| `jvm-dashboard.json` | `web-resolver-jvm` | Heap/non-heap memory, GC pauses, threads, classloader, CPU |
| `kafka-dashboard.json` | `web-resolver-kafka` | Consumer lag, partition offsets, throughput per topic |
| `postgres-dashboard.json` | `web-resolver-postgres` | Connections, transactions, cache hit ratio, locks, DB size |
| `sandbox-dashboard.json` | `web-resolver-sandbox` | Exec p95 per language, verdict distribution, OOM rate |
| `ai-dashboard.json` | `web-resolver-ai` | Calls by variant/outcome, fallback rate (5m vs 1h), schema valid ratio, latency p50/p95/p99 |

## SLO targets и burn-rate alert rules

Файл правил: `monitoring/rules/web-resolver.rules.yml` (8 групп, 40 правил —
recording + alert; валидируется `promtool check rules`).

| SLO | Target | Hard threshold | Group |
|-----|--------|----------------|-------|
| HTTP error rate | ≤ 1% (99%) | budget 30 дней | `http-slo` |
| HTTP p95 latency | ≤ 1 s | ≤ 3 s | `http-latency-slo` |
| Submission start p95 (PATCH `/task/start`) | ≤ 500 ms | ≤ 1 s | `http-latency-slo` |
| Sandbox p95 latency | ≤ 10 s | ≤ 15 s | `sandbox-slo` |
| AI fallback-rate | ≤ 10% (90%) | budget 30 дней | `ai-fallback-slo` |

Burn-rate схема (Google SRE Workbook, multi-window multi-burn-rate):
- **critical** — 14.4× burn на окнах 5m × 1h (бюджет сгорит за 2 дня), `severity=critical`;
- **high** — 6× burn на окнах 30m × 6h (бюджет сгорит за 5 дней), `severity=high`;
- **medium** — 1× burn на окнах 6h × 3d (тренд деградации), `severity=medium`.

Операционные алёрты (не SLO burn): `KafkaConsumerLagHigh`, `PostgresDown`,
`PostgresHighConnections`, `JvmMemoryPressure`, `InstanceDown`.

Все алёрты несут `severity`, `runbook_url` и читаемые `summary`/`description`.

## Alertmanager

Конфиг: `monitoring/alertmanager.yml` (минимальный для dev-стенда — все
алёрты дропаются в receiver `null`). Для production подменить на
slack/telegram — пример receiver-блоков закомментирован в файле.

## Тесты

```bash
# Полный compile + unit-тесты
./gradlew :MainApplication:test

# Интеграционные (требуют Docker для PostgreSQLContainer)
RUN_INTEGRATION_TESTS=true ./gradlew :MainApplication:test \
  --tests "ru.observability.MetricsExposureTest"

# Валидация alert rules через promtool в docker-контейнере
RUN_PROMTOOL_TESTS=true ./gradlew :MainApplication:test \
  --tests "ru.observability.AlertRulesValidationTest"
```

## Validation curl-cheat-sheet

```bash
curl -fsS http://localhost:9090/-/healthy                     # → 200
curl -fsS http://localhost:9090/-/ready                       # → 200
curl -fsS http://localhost:9090/api/v1/rules | jq '.data.groups | length'  # → 8
curl -fsS http://localhost:9093/-/healthy                     # → 200
curl -fsS http://localhost:9187/metrics | head -1             # → postgres
curl -fsS http://localhost:9308/metrics | head -1             # → kafka
curl -fsS -u prometheus-scraper:scraper-dev-pass \
  http://localhost:8080/actuator/prometheus | head -3         # → 200
curl -s -o /dev/null -w "%{http_code}\n" \
  http://localhost:8080/actuator/prometheus                   # → 401
```

## Production-checklist (вне scope MVP)

- ✅ basic-auth + IP-allowlist для `/actuator/prometheus`.
- ✅ Alertmanager в стеке с правилами по burn-rate (8 групп, 40 правил).
- ✅ Sidecar-exporters: postgres, kafka, node.
- ⬜ Долгосрочное хранилище метрик (Thanos / VictoriaMetrics).
- ⬜ Подменить receiver `null` на slack/telegram в `alertmanager.yml`.
- ⬜ Перевести `prometheus-scraper` пароль в Vault / Kubernetes Secret.
- ⬜ Loki / Tempo для трассировки и логов (за пределами этапа защиты).

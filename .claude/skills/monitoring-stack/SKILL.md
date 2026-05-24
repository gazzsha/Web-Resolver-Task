---
name: monitoring-stack
description: |
  End-to-end Prometheus + Grafana + Alertmanager monitoring for Spring Boot
  3.5 + Kafka + PostgreSQL + Docker-sandbox + LLM-integration stack. Use
  when adding custom metrics, designing SLO/SLI, writing alert rules,
  building Grafana dashboards, or hardening /actuator endpoints.
trigger:
  - Adding @Timed / Counter / MeterRegistry to Kotlin Spring Boot code.
  - Editing monitoring/prometheus.yml, monitoring/grafana/*.json.
  - Designing SLO with burn-rate alerts.
  - Adding Alertmanager / Loki / Tempo / kafka-exporter / postgres-exporter.
  - Securing /actuator/prometheus.
---

# monitoring-stack — Prometheus + Grafana for Spring Boot 3.5 + Kafka + LLM

## 1. Текущая база (этот проект)

| Слой | Состояние |
|------|-----------|
| Actuator | ✅ `/actuator/{health,info,prometheus,metrics}` опубликованы |
| Micrometer Prometheus registry | ✅ `runtimeOnly(libs.micrometer.registry.prometheus)` в `MainApplication` |
| HTTP histogram + SLO buckets | ✅ настроены в `application.properties` |
| Prometheus | ✅ `prom/prometheus:v2.55.0`, scrape interval 10 s |
| Grafana | ✅ `grafana/grafana:11.3.0` + auto-provision dashboard |
| Custom метрики (AI, sandbox, worker) | ❌ описаны в README, но не внедрены |
| Alertmanager | ❌ |
| kafka-exporter | ❌ |
| postgres-exporter | ❌ |
| Loki / Tempo | ❌ (вне scope защиты, опционально) |
| `/actuator/prometheus` hardening | ❌ открыт `permitAll` |

## 2. Соглашения по именованию метрик

Prometheus naming: snake_case, единица в суффиксе (`_seconds`, `_bytes`, `_total`).
Cardinality бюджет на тег — **≤ 10 значений**. Никогда не использовать UUID,
email, idem-ключи как теги.

Префиксы по доменам:
- `ai_analyzer_*`  — для модуля `ai-analyzer`.
- `sandbox_*`      — для `sandbox`.
- `worker_*`       — для `worker`.
- `task_resolver_*` — для REST-входа.

## 3. Конкретные метрики для нашего проекта

### 3.1. AI-analyzer (`ai-analyzer/.../GigaChatAnalyzer.kt`, `AstHybridAnalyzer.kt`)

```kotlin
@Component
class AiAnalyzerMetrics(registry: MeterRegistry) {
    val calls: Counter = Counter.builder("ai_analyzer_calls_total")
        .description("Total LLM calls grouped by variant and outcome")
        .tag("variant", "")            // 'gigachat' | 'ast-hybrid' | 'rule-based'
        .tag("outcome", "")            // 'success' | 'schema_error' | 'fallback'
        .register(registry)

    val latency: Timer = Timer.builder("ai_analyzer_latency_seconds")
        .description("End-to-end analyze() latency")
        .publishPercentileHistogram()
        .serviceLevelObjectives(
            Duration.ofMillis(500), Duration.ofSeconds(1),
            Duration.ofSeconds(2), Duration.ofSeconds(5))
        .register(registry)

    val schemaValid: Counter = Counter.builder("ai_analyzer_schema_valid_total")
        .tag("valid", "")              // 'true' | 'false'
        .register(registry)
}
```

### 3.2. Sandbox (`sandbox/.../DockerSandboxService.kt`)

```kotlin
val sandboxLatency = Timer.builder("sandbox_execution_seconds")
    .tag("language", "")               // 'python' | 'java'
    .tag("verdict", "")                // 'OK' | 'WA' | 'TLE' | 'RTE' | 'CE'
    .publishPercentileHistogram()
    .serviceLevelObjectives(
        Duration.ofSeconds(1), Duration.ofSeconds(3),
        Duration.ofSeconds(10), Duration.ofSeconds(30))
    .register(registry)

val sandboxOom = Counter.builder("sandbox_oom_killed_total")
    .description("Container terminated by OOM killer")
    .register(registry)
```

### 3.3. Worker (`worker/.../WorkerKafkaListener.kt`)

```kotlin
val submissionsProcessed = Counter.builder("worker_submissions_total")
    .tag("status", "")                 // 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILED' | 'ERROR'
    .register(registry)

val processingTime = Timer.builder("worker_processing_seconds")
    .publishPercentileHistogram()
    .register(registry)
```

## 4. Compose с Alertmanager + exporters

Добавить в `docker-compose.yml` под профиль `monitoring`:

```yaml
  alertmanager:
    image: prom/alertmanager:v0.27.0
    container_name: web-resolver-alertmanager
    command:
      - "--config.file=/etc/alertmanager/alertmanager.yml"
      - "--storage.path=/alertmanager"
    ports: ["9093:9093"]
    volumes:
      - ./monitoring/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro
      - alertmanager_data:/alertmanager
    networks: [web-resolver-network]
    profiles: [monitoring, all]

  postgres-exporter:
    image: prometheuscommunity/postgres-exporter:v0.16.0
    environment:
      DATA_SOURCE_NAME: "postgresql://${POSTGRES_USER:-postgres}:${POSTGRES_PASSWORD:-postgres}@postgres:5432/${POSTGRES_DB:-web_resolver}?sslmode=disable"
    ports: ["9187:9187"]
    networks: [web-resolver-network]
    profiles: [monitoring, all]

  kafka-exporter:
    image: danielqsj/kafka-exporter:v1.8.0
    command: ["--kafka.server=kafka:29092"]
    ports: ["9308:9308"]
    networks: [web-resolver-network]
    profiles: [monitoring, all]

  node-exporter:
    image: prom/node-exporter:v1.8.2
    pid: host
    volumes:
      - "/:/host:ro,rslave"
    command: ["--path.rootfs=/host"]
    ports: ["9100:9100"]
    networks: [web-resolver-network]
    profiles: [monitoring, all]
```

Дополнить `prometheus.yml` job'ами:
```yaml
  - job_name: 'postgres'      ; static_configs: [{targets: ['postgres-exporter:9187']}]
  - job_name: 'kafka'         ; static_configs: [{targets: ['kafka-exporter:9308']}]
  - job_name: 'node'          ; static_configs: [{targets: ['node-exporter:9100']}]
  - job_name: 'alertmanager'  ; static_configs: [{targets: ['alertmanager:9093']}]
```

И секцию `alerting:` указывающую на `alertmanager:9093` + `rule_files: ['/etc/prometheus/rules/*.yml']`.

## 5. SLO + burn-rate alert rules

Каноническая стратегия по SRE Workbook (Google) — **multi-window multi-burn-rate**. Для SLO 99,9 % за 30 дней:

| Window | Burn rate | Trigger | Severity |
|--------|-----------|---------|----------|
| 5m × 1h | 14,4× | exhausts monthly budget in 2 days | **critical** (page) |
| 30m × 6h | 6× | exhausts in 5 days | high (page) |
| 6h × 3d | 1× | trend deterioration | medium (ticket) |

Файл `monitoring/rules/web-resolver.rules.yml`:
```yaml
groups:
- name: http-slo
  interval: 30s
  rules:
  - record: job:http_errors:ratio_rate5m
    expr: |
      sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
      / sum(rate(http_server_requests_seconds_count[5m]))
  - alert: HighErrorBudgetBurn
    expr: |
      job:http_errors:ratio_rate5m > (14.4 * (1 - 0.999))
    for: 2m
    labels: {severity: critical}
    annotations: {summary: "Error budget burn 14.4× (2-day exhaustion)"}

- name: ai-fallback-slo
  rules:
  - record: ai_analyzer:fallback_rate5m
    expr: |
      sum(rate(ai_analyzer_calls_total{outcome="fallback"}[5m]))
      / sum(rate(ai_analyzer_calls_total[5m]))
  - alert: AiFallbackHigh
    expr: ai_analyzer:fallback_rate5m > 0.30
    for: 5m
    labels: {severity: high}
    annotations: {summary: "AI fallback-rate > 30% — GigaChat may be down"}
```

## 6. Hardening `/actuator/prometheus`

В `SecurityConfig.kt`:
```kotlin
http.authorizeHttpRequests { auth ->
    auth.requestMatchers("/actuator/health", "/actuator/info").permitAll()
    auth.requestMatchers("/actuator/prometheus")
        .access(IpAddressMatcher("172.16.0.0/12")::matches)
    auth.requestMatchers("/actuator/**").hasRole("OPS")
}
```
Либо basic-auth (одиночный сервис-аккаунт `prometheus-scraper`).

## 7. Grafana dashboards (provisioned)

Структура `monitoring/grafana/dashboards/`:
```
slo-dashboard.json        ← существует, 9 панелей
jvm-dashboard.json        ← добавить (heap, GC, threads, classloader)
kafka-dashboard.json      ← добавить (consumer lag, partition assignment)
postgres-dashboard.json   ← добавить (connections, slow queries, locks)
sandbox-dashboard.json    ← добавить (exec time per language, OOM-rate)
ai-dashboard.json         ← добавить (fallback-rate, schema-valid, p50/p95)
```

Готовые ID с grafana.com для импорта (как стартовая база):
- JVM (Micrometer): **4701**
- Kafka Lag: **11963**
- PostgreSQL Database: **9628**

После импорта — экспорт JSON, кастомизация под наши job-имена, коммит в репозиторий.

## 8. Чек-лист внедрения (для team-lead'а)

1. Добавить bean `AiAnalyzerMetrics`, `SandboxMetrics`, `WorkerMetrics` (см. §3).
2. Подключить `MeterRegistry` инъекцией в `GigaChatAnalyzer`, `AstHybridAnalyzer`, `DockerSandboxService`, `WorkerKafkaListener`.
3. Обернуть критические методы в `metrics.latency.record { ... }` либо `@Timed`.
4. Расширить `docker-compose.yml` сервисами `alertmanager`, `postgres-exporter`, `kafka-exporter`, `node-exporter`.
5. Дополнить `monitoring/prometheus.yml` новыми job'ами + секцией `alerting:`.
6. Создать `monitoring/rules/*.rules.yml` с SLO-recording и burn-rate alert'ами.
7. Создать `monitoring/alertmanager.yml` (route по severity → slack/telegram-receiver, для защиты можно `null` receiver).
8. Сгенерировать дополнительные dashboard'ы (см. §7).
9. Hardening `/actuator/prometheus` (см. §6).
10. Тесты: интеграционный `MetricsExposureTest` (Testcontainers + Spring), проверяющий, что `/actuator/prometheus` возвращает 200 и содержит ключевые имена.
11. README обновить: запуск с Alertmanager (`docker compose --profile monitoring up -d`), URL новых дашбордов.

## 9. Анти-паттерны (запрещено)

- ❌ Использование UUID, email, IP, request-id как тегов меток.
- ❌ `Counter`, который иногда уменьшается (use `Gauge` если значение колеблется).
- ❌ Запись метрик в hot-loop'е без `BatchTimer` или async-recording.
- ❌ Дашборды с PromQL-запросами `topk(N, ...)` без `by (label)` — даёт высокую cardinality.
- ❌ Долгосрочное хранение в локальном Prometheus — для production использовать Thanos / VictoriaMetrics (out of scope для защиты).
- ❌ Открытый `/actuator/prometheus` в production (см. §6).

## 10. Связанные скиллы

- `spring-boot` — Spring Boot 3.x идиомы.
- `jpa-patterns` — для метрик БД-доступа.
- `logging-patterns` — для согласования логов и метрик (один `traceId`).
- `insecure-defaults` — для аудита открытого actuator.

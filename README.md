# Web-Resolver-Task

Дипломный проект «Автоматизированная проверка задач по программированию с применением ИИ».

Стек: **Kotlin 2.2 / JDK 21**, **Spring Boot 3.5** (multi-module Gradle, модульный монолит), **PostgreSQL 16**, **Apache Kafka**, **Vite + React 18 + TypeScript + MUI**, **GigaChat** (LLM, с rule-based fallback), **Docker** sandbox.

---

## Запуск за минуту

Требуется: **Docker Desktop**, **JDK 21**, **Node 18+**, `jq` (для smoke-теста).

```bash
git clone <repo> && cd aidar_diplom

# 1. Конфигурация
cp .env.example .env                       # править необязательно — defaults подходят для dev

# 2. Стек инфраструктуры
docker compose up -d postgres kafka zookeeper

# 3. Backend (модульный монолит: worker, task-resolver, ai-analyzer, sandbox
# и scenario-runner упакованы в один процесс через MainApplication. Отдельного
# :worker:bootRun нет — у `worker/` нет своего `@SpringBootApplication`,
# модуль работает только в контексте MainApplication.)
./gradlew :MainApplication:bootRun         # терминал 1, http://localhost:8080

# 4. Frontend
cd frontend && npm install && npm run dev  # терминал 2, http://localhost:3000
```

После старта backend Flyway автоматически:
- создаст схему (V1__init.sql),
- засеет 5 задач (V4__seed_tasks.sql),
- засеет 3 пользователя (V3__seed_users.sql).

---

## Demo-аккаунты (созданы V3-миграцией)

| Email | Пароль | Роль |
|-------|--------|------|
| `teacher@diplom.local` | `Teacher123!` | TEACHER |
| `student1@diplom.local` | `Student123!` | STUDENT |
| `student2@diplom.local` | `Student123!` | STUDENT |

Открытая регистрация на `/register` всегда создаёт STUDENT.

---

## Демо-сценарий «Two Sum»

1. Открыть **http://localhost:3000** → редирект на `/login`.
2. Войти `student1@diplom.local` / `Student123!` → откроется список из 5 задач.
3. **Two Sum** → «Решить» → в Monaco-редактор вставить:

```java
import java.util.*;
public class Solution {
  public static void main(String[] args) {
    Scanner sc = new Scanner(System.in);
    String[] parts = sc.nextLine().split(",");
    int target = Integer.parseInt(sc.nextLine());
    int n = parts.length;
    int[] nums = new int[n];
    for (int i = 0; i < n; i++) nums[i] = Integer.parseInt(parts[i]);
    for (int i = 0; i < n; i++)
      for (int j = i+1; j < n; j++)
        if (nums[i] + nums[j] == target) { System.out.println(i + " " + j); return; }
  }
}
```

4. Submit → редирект на `/results/{submissionId}`.
5. Через 5–15 с (первый запуск sandbox-image тянет Docker образ): зелёный SUCCESS, табличка test cases, AI-блок.
6. Кнопка **Resubmit** возвращает на форму с уже подставленным кодом.

---

## E2E smoke-тест

```bash
./e2e_smoke.sh                       # 17 тестов: auth + RBAC (полминуты)
WITH_SUBMISSION=1 ./e2e_smoke.sh     # +submission flow через Kafka/sandbox (1-2 минуты)
```

Скрипт делает full-flow: регистрация → логин → list tasks → отправка решения → polling результата → assert SUCCESS.
Требует `jq` (`brew install jq` на macOS).

---

## Конфигурация (.env)

| Переменная | Дефолт | Зачем |
|---|---|---|
| `POSTGRES_HOST/PORT/DB/USER/PASSWORD` | `localhost / 5432 / web_resolver / postgres / postgres` | Подключение к Postgres |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Брокер Kafka |
| `JWT_SECRET` | пусто (dev-fallback) | Подпись HS256-токена. **В проде задать ≥ 32 байта** (`openssl rand -hex 32`) |
| `JWT_ACCESS_TTL_MINUTES` / `JWT_REFRESH_TTL_DAYS` | `60` / `7` | TTL access/refresh-токенов |
| `GIGACHAT_AUTH_KEY` | пусто | base64(client_id:client_secret) Сбера. **Без него LLM работает в fallback-режиме** на `SimpleRuleBasedAnalyzer` — UI покажет «AI-анализ недоступен»; pipeline всё равно проходит. |
| `GIGACHAT_SCOPE` | `GIGACHAT_API_PERS` | Скоуп GigaChat API |
| `GIGACHAT_MODEL` | `GigaChat` | Модель GigaChat |

---

## Модули (Gradle subprojects)

| Модуль | Назначение |
|--------|-----------|
| `MainApplication` | Spring Boot entrypoint, security, точка входа REST API. **Включает worker и task-resolver как модули.** |
| `task-resolver` | контроллеры (реализуют сгенерированные `web.*Api`), services, JPA-репозитории, JWT-фильтр |
| `api-generator` | OpenAPI YAML → Java-классы (`web.*Api` + `model.*` DTO). Авто-генерится перед `compileKotlin` |
| `db` | JPA-сущности, Flyway-миграции, репозитории |
| `sandbox` | изолированное исполнение пользовательского кода в Docker. См. [sandbox/THREAT_MODEL.md](sandbox/THREAT_MODEL.md) |
| `worker` | Kafka-listener + test-engine (исполняет задачи через `:sandbox`). Линкуется в `MainApplication` — отдельного процесса нет (модульный монолит) |
| `ai-analyzer` | GigaChat-клиент + rule-based fallback. Caffeine-кэш (TTL 1 ч) |
| `scenario-runner` | runner для сценарных тестов (за пределами MVP) |
| `common` | общий код (Kafka utils, базовые типы) |
| `frontend` | React + Vite SPA. Аутентификация через JWT |

---

## API endpoints (основные)

Все `/api/v1/**` требуют `Authorization: Bearer <accessToken>`. `/auth/**` и `/actuator/health` открыты.

| Метод | Путь | Что делает |
|-------|------|-----------|
| POST | `/auth/register` | Создаёт STUDENT, возвращает пару JWT |
| POST | `/auth/login` | Логин по email+password |
| POST | `/auth/refresh` | Обновляет access по refresh |
| GET | `/api/v1/tasks` | Список задач |
| GET | `/api/v1/tasks/{testId}` | Детали задачи |
| PATCH | `/api/v1/task-resolver/task/start` | Отправить решение → submissionId |
| GET | `/api/v1/task-results/{submissionId}` | Результат + summary AI |
| GET | `/api/v1/ai-analysis/{submissionId}` | Полный AI-анализ (issues + recommendations) |
| GET | `/actuator/health` | Liveness |

---

## Тесты

```bash
./gradlew clean build                                # все unit-тесты
RUN_INTEGRATION_TESTS=true ./gradlew :MainApplication:test  # + AuthFlowIntegrationTest на Linux/CI
./e2e_smoke.sh                                       # бизнес-flow на живом стенде
WITH_SUBMISSION=1 ./e2e_smoke.sh                     # +Kafka/worker/sandbox
```

Замечание про macOS Docker Desktop: `AuthFlowIntegrationTest` использует Testcontainers и ищет socket по `/var/run/docker.sock`. На macOS он по адресу `~/.docker/run/docker.sock` — Gradle Test task пробрасывает `DOCKER_HOST` автоматически, но если случай нестандартный — можно создать симлинк:

```bash
sudo ln -s ~/.docker/run/docker.sock /var/run/docker.sock
```

---

## Kafka DLT (Dead Letter Topic)

Worker-listener (`WorkerKafkaListener`) обёрнут в `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` (см. `worker/.../KafkaWorkerConfig`, F-16): после 3 неудачных fast-retry сообщение уходит в топик `task-execution.DLT` с сохранением оригинального ключа/значения и заголовков. Этот же путь используется для «отравленных» сообщений (например, невалидный JSON).

```bash
# Посмотреть содержимое DLT (требует kafkacat / kcat):
docker exec -it $(docker ps -qf name=kafka) kafka-console-consumer \
  --bootstrap-server localhost:9092 --topic task-execution.DLT --from-beginning --max-messages 5

# Ре-обработка одного сообщения вручную: скопировать payload и опубликовать
# обратно в task-execution тем же ключом.
docker exec -i $(docker ps -qf name=kafka) kafka-console-producer \
  --bootstrap-server localhost:9092 --topic task-execution --property "parse.key=true" \
  --property "key.separator=:"
```

Дашборд **Kafka** в Grafana содержит панель **«DLT messages per second (task-execution.DLT)»** (Step 6a / B.7). Скачок на этой панели = upstream-сбой обработки.

---

## Покрытие тестами (JaCoCo)

```bash
./gradlew jacocoTestReport                       # report HTML/XML по всем модулям
open MainApplication/build/reports/jacoco/test/html/index.html   # пример
```

XML-отчёты лежат в `<module>/build/reports/jacoco/test/jacocoTestReport.xml` и подходят для CI-интеграции (Codecov / SonarCloud / GitHub Actions).

---

## Команды разработки

```bash
./gradlew build                                  # full build
./gradlew :api-generator:compileJava             # перегенерить OpenAPI
./gradlew :MainApplication:bootRun               # backend (modular monolith — worker внутри)
cd frontend && npm run dev                       # frontend
cd frontend && npm run build                     # production-сборка фронта
docker compose down                              # остановить инфру (volume сохранится)
docker compose down -v                           # +удалить volume (полная очистка БД)
```

---

## FAQ

**Почему Resubmit не подставляет код?** — Подставляет, начиная с Phase 9. `TaskResultResponse` теперь содержит `code` и `language`, кнопка передаёт их через `location.state` на `/submit/{taskId}`.

**AI-блок пустой / «недоступен».** — Это нормально, если не задан `GIGACHAT_AUTH_KEY` и rule-based fallback не нашёл проблем (например, на корректном решении). Pipeline всё равно проходит, тесты выполнены, прохождение/непрохождение определяется судейским сервисом.

**Sandbox долго стартует на первом submission.** — Первый submission тянет docker-образ `eclipse-temurin:21-jdk-alpine` (~200 МБ). `SandboxImageManager` пред-кэширует образы при старте приложения через `ApplicationReadyEvent`, но если Docker daemon стартовал недавно — кэш пустой. Подожди 30-60 сек.

**Как добавить новую задачу?** — Через миграцию `db/src/main/resources/db/migration/V5__more_tasks.sql` по образцу V4 (две вставки: в `test` и `test_resolve`). После рестарта Flyway применит автоматически.

**Можно ли оторвать worker и задеплоить отдельно?** — Да, но это требует разнести `worker/build.gradle.kts` обратно в Spring Boot app + переписать MainApplication так чтобы оно НЕ зависело от `:worker`. Для MVP оставлен модульный монолит.

---

Подробности фаз и cut-line — в [`DEVELOPMENT_PLAN.md`](DEVELOPMENT_PLAN.md).
Threat-модель sandbox — в [`sandbox/THREAT_MODEL.md`](sandbox/THREAT_MODEL.md).

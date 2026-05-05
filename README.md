# Web-Resolver-Task

Дипломный проект «Автоматизированная проверка задач по программированию с применением ИИ».

Стек: Kotlin 2.2 / JDK 21, Spring Boot 3.5 (multi-module Gradle), PostgreSQL 16, Apache Kafka, Vite + React 18 + TypeScript + MUI.

## Документы

- [`CLAUDE.md`](CLAUDE.md) — инструкции для AI-агентов и обзор кодовой базы.
- [`DEVELOPMENT_PLAN.md`](DEVELOPMENT_PLAN.md) — план разработки по фазам, scope MVP, граф зависимостей.

## Быстрый запуск (dev)

Требуется Docker Desktop, JDK 21, Node 18+.

```bash
cp .env.example .env       # заполнить GIGACHAT_AUTH_KEY, JWT_SECRET и т.п.
docker compose up -d postgres kafka zookeeper
./gradlew :MainApplication:bootRun        # терминал 1, порт 8080
./gradlew :worker:bootRun                 # терминал 2
cd frontend && npm install && npm run dev # терминал 3, порт 5173
```

## Модули

| Модуль | Назначение |
|--------|-----------|
| `MainApplication` | Spring Boot entrypoint API |
| `task-resolver` | контроллеры (реализуют сгенерированные интерфейсы из OpenAPI) |
| `api-generator` | OpenAPI → Spring interfaces + DTO |
| `db` | JPA-сущности, миграции Flyway |
| `sandbox` | изолированное исполнение пользовательского кода в Docker |
| `worker` | фоновое исполнение задач, Kafka consumer/producer |
| `ai-analyzer` | LLM-интеграция (GigaChat) + rule-based fallback |
| `scenario-runner` | runner тестовых сценариев |
| `common` | общий код |
| `frontend` | React + Vite SPA |

## Команды

```bash
./gradlew build                                  # full build (генерит OpenAPI, тесты)
./gradlew :api-generator:generateApiTaskResolver # пересобрать API-классы
./gradlew :MainApplication:bootRun
cd frontend && npm run dev
```

Подробности фаз и cut-line — в [`DEVELOPMENT_PLAN.md`](DEVELOPMENT_PLAN.md).

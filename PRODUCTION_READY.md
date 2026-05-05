# Web Resolver - Production Ready Implementation

## ✅ Все компоненты работают с реальными данными

### Архитектура

```
┌─────────────┐     Kafka       ┌─────────────┐
│   Frontend  │                 │   Worker    │
│  (React)    │     ┌──────┐    │  (Kotlin)   │
│  Port 3000  │     │ Kafka│    │  Port 8081  │
└──────┬──────┘     └──────┘    └──────┬──────┘
       │ HTTP                          │
       ▼                               │
┌─────────────┐                        │
│     Main    │◄───────────────────────┘
│ Application │
│  (Kotlin)   │
│  Port 8080  │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ PostgreSQL  │
│  Port 5432  │
└─────────────┘
```

## 🚀 Запуск

### 1. Инфраструктура (PostgreSQL + Kafka)
```bash
docker-compose up -d
```

### 2. Main Application
```bash
./gradlew :MainApplication:bootRun
```

### 3. Worker Service
```bash
./gradlew :worker:bootRun
```

### 4. Frontend
```bash
cd frontend
npm install
npm run dev
```

### 5. Доступ
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Worker API: http://localhost:8081

## 🔧 Конфигурация

### Kafka Topics
- `task-execution` - Задачи от main app к worker
- `task-results` - Результаты от worker к main app

### База данных
- Host: `localhost:5432`
- Database: `web_resolver`
- User: `postgres`
- Password: `postgres`

## 📝 Реализованные компоненты

### Backend (Main Application)
- ✅ Task API (`GET /api/v1/tasks`, `GET /api/v1/tasks/{id}`)
- ✅ Submission API (`PATCH /api/v1/task-resolver/task/start`)
- ✅ Result API (`GET /api/v1/task-results/{taskId}`)
- ✅ Kafka producer (отправка задач в worker)
- ✅ Kafka consumer (получение результатов от worker)
- ✅ Сохранение результатов в PostgreSQL

### Worker Service
- ✅ Kafka consumer (получение задач)
- ✅ Kafka producer (отправка результатов)
- ✅ **Docker Sandbox** для безопасного выполнения кода
- ✅ Test Engine для проверки тестовых кейсов
- ✅ Scenario Runner для stateful тестов
- ✅ AI Analyzer для анализа кода

### Frontend
- ✅ Список задач (из API)
- ✅ Детали задачи
- ✅ Редактор кода (Java/Kotlin/Python)
- ✅ Отправка решений (реальное API)
- ✅ Просмотр результатов

## 🧪 Тестирование

### 1. Отправка решения
```bash
curl -X PATCH http://localhost:8080/api/v1/task-resolver/task/start \
  -H "Content-Type: application/json" \
  -u admin:admin \
  -d '{
    "testId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "code": "class Solution { public int[] twoSum(int[] nums, int target) { java.util.Map<Integer, Integer> map = new java.util.HashMap<>(); for (int i = 0; i < nums.length; i++) { int complement = target - nums[i]; if (map.containsKey(complement)) { return new int[] { map.get(complement), i }; } map.put(nums[i], i); } throw new IllegalArgumentException(\"No solution\"); } }",
    "language": "java"
  }'
```

### 2. Проверка результатов
```bash
curl http://localhost:8080/api/v1/task-results/{taskId} -u admin:admin
```

## 🔒 Безопасность

### Docker Sandbox
- Изолированные контейнеры
- Отключение сети (`--network=none`)
- Read-only файловая система
- Лимиты CPU и памяти
- Timeout защита

### Запрещенные операции
- Сетевые вызовы (Socket, URL, HttpClient)
- Доступ к файловой системе
- Выполнение процессов
- Рефлексия
- Системные свойства
- Native code

## 📊 Доступные задачи

1. **Two Sum** (Easy) - `a1b2c3d4-e5f6-7890-abcd-ef1234567890`
2. **Valid Parentheses** (Easy) - `b2c3d4e5-f6a7-8901-bcde-f12345678901`
3. **Merge Two Sorted Lists** (Easy) - `c3d4e5f6-a7b8-9012-cdef-123456789012`
4. **Valid Palindrome** (Easy) - `e5f6a7b8-c9d0-1234-ef01-345678901234`
5. **Median of Two Sorted Arrays** (Hard) - `c5d6e7f8-a9b0-1234-8901-345678901234`

## 🎯 Поддерживаемые языки

- **Java** (eclipse-temurin:21-jre-alpine)
- **Python** (python:3.11-alpine)
- **Kotlin** (kotlin:1.9-jre-alpine)

## 📈 Мониторинг

### Actuator Endpoints
- `/actuator/health` - Health check
- `/actuator/metrics` - Metrics

### Kafka Consumer Groups
```bash
docker exec web-resolver-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe
```

## 🛠️ Production Deployment

### Docker Compose (Production)
```yaml
services:
  main-app:
    build:
      context: .
      dockerfile: MainApplication/Dockerfile
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/web_resolver
      KAFKA_BOOTSTRAP_SERVERS: kafka:29092
  
  worker:
    build:
      context: .
      dockerfile: worker/Dockerfile
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:29092
```

### Переменные окружения
```bash
# Database
DIPLOM_DB_NAME=web_resolver
DIPLOM_DB_USER=postgres
DIPLOM_DB_PASSWORD=postgres

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# AI (optional)
AI_CHATGPT_API_KEY=your-api-key
```

## 📚 Примеры решений

См. `SOLUTIONS.md` для готовых решений на Java, Kotlin и Python.

## ⚠️ Важно для macOS

Для работы Docker Sandbox на macOS требуется Docker Desktop:
```bash
# Проверка
docker ps

# Если не работает - запустите Docker Desktop
open -a Docker
```

## 🎯 Следующие шаги

1. **Настроить CI/CD** для автоматического деплоя
2. **Добавить аутентификацию** пользователей
3. **Интегрировать с ChatGPT** для AI анализа
4. **Добавить leaderboard** для задач
5. **Настроить мониторинг** (Prometheus + Grafana)

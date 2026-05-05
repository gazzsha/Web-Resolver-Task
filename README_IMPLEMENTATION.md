# Web Resolver Task - Implementation

## Overview

This is the implementation of a **modular service-oriented architecture** for a programming task resolution system with AI-powered code analysis.

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed architectural documentation.

### Key Components

```
Frontend → API → Kafka → Worker → Sandbox → Test Engine → AI → DB
```

## Project Structure

```
Web-Resolver-Task/
├── frontend/                 # React + TypeScript frontend application
├── MainApplication/          # Main API Gateway (Spring Boot)
├── common/                   # Shared utilities and Kafka config
├── api-generator/            # OpenAPI specifications
├── task-resolver/            # Task resolution logic
├── task-process/             # Task processing utilities
├── db/                       # Database entities and repositories
├── sandbox/                  # Docker-based code execution sandbox
├── worker/                   # Async task processor
├── ai-analyzer/              # AI code analysis module
└── scenario-runner/          # Interactive task runner
```

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Java 21+
- Gradle 8.7+
- (Optional) OpenAI API key for AI features

### 1. Clone and Configure

```bash
# Copy environment template
cp .env.example .env

# Edit .env with your values
# - Database credentials
# - OpenAI API key (optional)
```

### 2. Start Infrastructure

```bash
# Start PostgreSQL and Kafka
docker-compose up -d postgres zookeeper kafka
```

### 3. Build the Project

```bash
# Build all modules
./gradlew build

# Or build specific modules
./gradlew :sandbox:build
./gradlew :worker:build
./gradlew :MainApplication:build
```

### 4. Install Frontend Dependencies

```bash
cd frontend
npm install
```

### 5. Run Services

```bash
# Run Main Application
./gradlew :MainApplication:bootRun

# Run Worker (in separate terminal)
./gradlew :worker:bootRun

# Run Frontend (in another terminal)
cd frontend
npm run dev
```

The application will be available at:
- **Frontend:** http://localhost:3000
- **Backend API:** http://localhost:8080
- **API Docs:** http://localhost:8080/swagger-ui.html

### 6. Or use Docker Compose

```bash
# Run all services
docker-compose up -d

# View logs
docker-compose logs -f
```

## Module Descriptions

### Frontend Module

**Purpose:** Modern, user-friendly interface for the task resolution platform

**Technology Stack:**
- React 18 + TypeScript
- Vite (build tool)
- Material-UI (MUI) v5
- Monaco Editor (VS Code's editor)
- Zustand (state management)
- Recharts (data visualization)

**Features:**
- **Dashboard** - Statistics, activity charts, achievements
- **Task Browser** - Search, filter, and select tasks by difficulty
- **Code Editor** - Syntax highlighting for Java/Kotlin/Python
- **Submission System** - Real-time results with detailed metrics
- **AI Analysis** - Code quality scores and recommendations
- **Dark/Light Mode** - Theme customization
- **Responsive Design** - Works on desktop, tablet, and mobile

**Pages:**
1. `/` - Dashboard with statistics
2. `/tasks` - Task list with search
3. `/tasks/:id` - Task details
4. `/submit/:id` - Code editor and submission
5. `/results/:id` - Results and AI analysis

**Quick Start:**
```bash
cd frontend
npm install
npm run dev
```

See [frontend/README.md](frontend/README.md) for detailed documentation.

### Sandbox Module

**Purpose:** Secure code execution using Docker containers

**Features:**
- Docker-based isolation
- Resource limits (CPU, memory, time)
- Network disabled
- Read-only filesystem
- Support for Java, Kotlin, Python

**Security:**
```kotlin
// Resource limits
CPU: 1 core
Memory: 256 MB
Timeout: 5 seconds

// Docker security
--network=none
--read-only
--cap-drop=ALL
--no-new-privileges
```

### Worker Module

**Purpose:** Async task processing

**Features:**
- Kafka consumer for task queue
- Orchestrates execution flow
- Collects results
- Publishes to result queue

**Scalability:**
- Multiple worker instances
- Horizontal scaling
- Load balancing via Kafka

### AI Analyzer Module

**Purpose:** Code analysis and educational feedback

**Features:**
- ChatGPT integration (optional)
- Rule-based fallback analyzer
- Code quality scoring
- Error explanation
- Personalized recommendations

**Hybrid Approach:**
1. Test Engine → Factual errors
2. AI → Explanation & recommendations
3. (Optional) AST → Structural confirmation

### Scenario Runner Module

**Purpose:** Interactive/stateful task testing

**Features:**
- Multi-step scenario execution
- State-dependent validation
- Menu-based program support
- Behavior flow validation

**Innovation:** This is a key feature for the thesis - supporting complex interactive tasks.

## API Endpoints

### Task Resolution

```
PATCH /api/v1/task-resolver/task/start
Body: { testId, code, language }
Response: { id, result: { success } }
```

### Results

```
GET /api/v1/task-results/{taskId}
Response: Task result with test details

GET /api/v1/task-results/test/{testId}
Response: List of all results for test
```

### AI Analysis

```
GET /api/v1/ai-analysis/{taskId}
Response: AI analysis with quality score, issues, recommendations

POST /api/v1/ai-analysis/{taskId}/explain
Body: { error, testInput }
Response: Error explanation
```

## Security Features

### 1. Code Isolation

- Docker containers for each execution
- No network access
- Read-only filesystem
- Dropped capabilities

### 2. Resource Limits

```yaml
CPU Limit: 1.0 cores
Memory Limit: 256 MB
Timeout: 5 seconds
```

### 3. Forbidden Operations

- Network sockets
- File system writes
- Process execution
- System exit
- Reflection abuse

### 4. Code Validation

```kotlin
SandboxSecurityConfig.validateCode(code)
```

Checks for:
- Forbidden patterns
- Infinite loops
- Malicious code

## Kafka Topics

| Topic | Purpose | Producer | Consumer |
|-------|---------|----------|----------|
| `task-execution` | Tasks to execute | API | Worker |
| `task-results` | Execution results | Worker | API/DB |
| `ai-analysis` | AI requests | Worker | AI Service |

## Database Schema

### task_results

```sql
- id (BIGINT, PK)
- task_id (UUID)
- test_id (UUID)
- user_id (UUID)
- status (ENUM)
- total_tests (INT)
- passed_tests (INT)
- test_results (JSONB)
- scenario_results (JSONB)
- ai_analysis_id (FK)
- created_at (TIMESTAMP)
```

### ai_analysis

```sql
- id (BIGINT, PK)
- task_result_id (FK, UNIQUE)
- code_quality_score (INT)
- issues (JSONB)
- recommendations (TEXT[])
- explanation (TEXT)
- complexity (ENUM)
- created_at (TIMESTAMP)
```

## Testing

### Run Tests

```bash
# All tests
./gradlew test

# Specific module
./gradlew :sandbox:test
./gradlew :worker:test
```

### Test Coverage

The system includes:
- Unit tests for services
- Integration tests for Kafka
- Sandbox security tests
- AI analyzer tests

## Configuration

### Environment Variables

```bash
# Database
DIPLOM_DB_NAME=web_resolver
DIPLOM_DB_USER=postgres
DIPLOM_DB_PASSWORD=secret

# AI (Optional)
AI_CHATGPT_API_KEY=sk-...

# Admin
ADMIN_PASSWORD=admin
```

### Application Properties

See:
- `MainApplication/src/main/resources/application.yml`
- `worker/src/main/resources/application.yml`

## Scaling

### Horizontal Scaling

```bash
# Scale workers
docker-compose up -d --scale worker=3
```

### Resource Allocation

```yaml
worker:
  deploy:
    replicas: 3
    resources:
      limits:
        cpus: '2.0'
        memory: 1G
```

## Monitoring

### Health Checks

```bash
# Main application
curl http://localhost:8080/actuator/health

# Worker
curl http://localhost:8081/actuator/health
```

### Logs

```bash
docker-compose logs -f worker
docker-compose logs -f main-app
```

## Development

### Add New Language

1. Add language support in `DockerSandboxService`
2. Add Docker image for language
3. Update API models
4. Add tests

### Add New Test Type

1. Extend `TestEngine` interface
2. Implement in `DefaultTestEngine`
3. Add API endpoint
4. Update database schema

## Troubleshooting

### Docker Socket Access

```bash
# Grant Docker socket access
sudo chmod 666 /var/run/docker.sock
```

### Kafka Connection Issues

```bash
# Check Kafka is running
docker-compose ps kafka

# View Kafka logs
docker-compose logs kafka
```

### AI Not Working

- Check API key is set
- Verify network connectivity
- Check rate limits
- Fallback to rule-based analyzer

## Thesis Alignment

This implementation directly supports the thesis:

### Scientific Novelty

**Hybrid Verification Approach:**
- Test Engine → Factual errors
- AI → Explanation
- Scenario Runner → Behavior validation

### Architecture Justification

**Why Service-Oriented:**
- ✓ Isolation (security)
- ✓ Independent scaling
- ✓ Flexibility
- ✓ Extensibility

### Key Features

1. **Sandbox Security** - Docker isolation with resource limits
2. **AI Analysis** - ChatGPT + rule-based fallback
3. **Scenario Runner** - Interactive task support
4. **Async Processing** - Kafka-based queue
5. **Scalability** - Horizontal worker scaling

## License

This is part of a diploma project.

## Contact

For questions about the implementation, refer to the thesis documentation.

# Web Resolver Task - System Architecture

## Chapter 2. System Design

### 2.1 System Requirements

#### Functional Requirements

1. **Task Loading**
   - Load programming tasks from database
   - Support multiple difficulty levels (Easy, Medium, Hard)
   - Display task description and requirements

2. **Solution Submission**
   - Accept code in multiple languages (Java, Kotlin, Python)
   - Validate code syntax and structure
   - Store submissions for analysis

3. **Code Execution**
   - Execute user code in isolated environment
   - Support multiple test cases per task
   - Measure execution time and memory usage

4. **Testing**
   - Run automated tests against solutions
   - Support custom checkers
   - Scenario-based testing for interactive tasks

5. **AI Analysis**
   - Analyze code quality
   - Explain errors in natural language
   - Provide personalized recommendations
   - Detect bugs and code smells

#### Non-Functional Requirements

1. **Security (Critical)**
   - Isolated code execution using Docker containers
   - Resource limits (CPU, memory, time)
   - Network access disabled
   - Forbidden operations detection

2. **Scalability**
   - Horizontal scaling of worker nodes
   - Message queue for load balancing
   - Independent scaling of components

3. **Fault Tolerance**
   - Graceful handling of execution failures
   - Retry mechanism for transient errors
   - Dead letter queue for failed tasks

### 2.2 Architectural Approach

#### Why NOT Monolith

1. **Code Execution Scaling Issues**
   - Running user code is resource-intensive
   - In monolith, all components share resources
   - Difficult to scale execution independently

2. **Security Concerns**
   - Sandbox inside main process is unsafe
   - Compromised sandbox could affect entire system
   - Isolation is critical for user code execution

3. **AI Module Expansion**
   - AI analysis requires different resources (GPU, API calls)
   - Need to update AI models independently
   - Different scaling requirements

#### Why Modular Service-Oriented Architecture

**Selected Approach: Modular Service-Oriented Architecture**

This architecture provides:

1. **Component Isolation**
   - Each service runs in its own process/container
   - Failure in one service doesn't affect others
   - Independent deployment and updates

2. **Independent Scaling**
   - Workers can be scaled based on submission volume
   - AI analyzer can be scaled separately
   - Database can be optimized independently

3. **Security**
   - Sandbox service runs with minimal privileges
   - Docker containers provide strong isolation
   - Network policies restrict communication

4. **Flexibility**
   - Easy to add new programming languages
   - Can swap AI providers without affecting core
   - Support for different execution strategies

**Thesis Statement:**

> "В качестве архитектурного подхода выбрана модульная сервис-ориентированная архитектура, обеспечивающая независимость компонентов, масштабируемость и возможность интеграции интеллектуальных модулей анализа."

### 2.3 System Architecture

#### Core Architecture Decision

```
Frontend → API → Queue → Worker → Sandbox → Test Engine → AI Module → DB
```

#### Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client Layer                             │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │   Web UI    │  │  Mobile App  │  │  API Clients │           │
│  └─────────────┘  └──────────────┘  └──────────────┘           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         API Gateway                              │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              Main Application (Spring Boot)              │   │
│  │  - Task Management  - Authentication  - Result API      │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Message Queue (Kafka)                       │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │task-execution│  │task-results  │  │ai-analysis   │           │
│  └─────────────┘  └──────────────┘  └──────────────┘           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Worker Services                             │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              Worker (Multiple Instances)                 │   │
│  │  - Consume tasks from queue                             │   │
│  │  - Orchestrate execution                                │   │
│  │  - Collect results                                      │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Execution Layer                             │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │   Sandbox   │  │Test Engine   │  │Scenario Runner│           │
│  │  (Docker)   │  │              │  │              │           │
│  └─────────────┘  └──────────────┘  └──────────────┘           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      AI Analysis Layer                           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              AI Analyzer (ChatGPT / Rules)               │   │
│  │  - Code quality assessment                              │   │
│  │  - Error explanation                                    │   │
│  │  - Recommendations                                      │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Data Layer                                  │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │  PostgreSQL │  │File Storage  │  │   Redis      │           │
│  │  (Results)  │  │  (Tests)     │  │   (Cache)    │           │
│  └─────────────┘  └──────────────┘  └──────────────┘           │
└─────────────────────────────────────────────────────────────────┘
```

#### Component Descriptions

##### 1. Client Application (Frontend)

**Technology:** React / Web UI

**Functions:**
- Code editor with syntax highlighting
- Task selection and display
- Results visualization
- AI feedback display

**Why Separate:**
- UI/Logic separation
- Independent scaling
- Better user experience

##### 2. Backend (API Layer)

**Technology:** Spring Boot (Kotlin)

**Functions:**
- Receive solutions
- Task management
- Authentication & Authorization
- Result retrieval

**Why Centralized:**
- Single entry point
- Security control
- Request validation

##### 3. Message Queue (Kafka)

**Technology:** Apache Kafka

**Functions:**
- Asynchronous task processing
- Load balancing
- Buffer for peak loads

**Thesis Justification:**

> "Очередь задач обеспечивает асинхронную обработку и балансировку нагрузки. Выполнение кода — тяжёлая операция, которую нельзя делать синхронно."

**Topics:**
- `task-execution` - Tasks to be executed
- `task-results` - Execution results
- `ai-analysis` - AI analysis requests

##### 4. Worker Service

**Technology:** Spring Boot + Kotlin

**Functions:**
- Consume tasks from queue
- Orchestrate execution flow
- Collect and aggregate results
- Publish results

**Why Separate:**
- Independent scaling (multiple workers)
- Resource isolation
- Fault tolerance

##### 5. Sandbox Service (Critical)

**Technology:** Docker + Kotlin

**Functions:**
- Compile user code
- Execute in isolated container
- Enforce resource limits
- Security enforcement

**Security Measures:**

```kotlin
// Resource Limits
- CPU: 1 core (configurable)
- Memory: 256 MB (max 512 MB)
- Timeout: 5 seconds (max 10 seconds)

// Docker Security
- Network: disabled (--network=none)
- Filesystem: read-only (--read-only)
- Capabilities: dropped (--cap-drop=ALL)
- Privileges: no escalation (--no-new-privileges)
```

**Thesis Justification:**

> "Sandbox обеспечивает безопасность через изоляцию пользовательского кода в Docker контейнерах с ограничением ресурсов (CPU, память, timeout)."

##### 6. Test Engine

**Functions:**
- Run test cases
- Compare outputs
- Support custom checkers
- Performance measurement

**Test Types:**
- Standard I/O tests
- Custom checker tests
- Performance tests

##### 7. Scenario Runner (Innovation)

**Functions:**
- Multi-step interaction testing
- State-dependent validation
- Menu-based program support

**Thesis Innovation:**

> "Scenario Runner — это уникальная особенность системы для проверки сложных интерактивных задач с меню и состоянием."

**Example Use Cases:**
- Menu-driven programs
- Interactive consoles
- State machines

##### 8. AI Analysis Module (Core Innovation)

**Technology:** ChatGPT API + Rule-based analyzer

**Functions:**
- Code quality analysis
- Error explanation
- Personalized recommendations
- Bug detection

**Hybrid Verification Approach (Scientific Novelty):**

```
┌─────────────┐
│ Test Engine │ → Factual errors (PASS/FAIL)
└─────────────┘
       ↓
┌─────────────┐
│     AI      │ → Explanation & Recommendations
└─────────────┘
       ↓
┌─────────────┐
│    AST      │ → Structural confirmation (optional)
└─────────────┘
```

**Thesis Justification:**

> "Гибридная проверка: Test Engine определяет факт ошибки, AI объясняет причину и даёт рекомендации. Это и есть научная новизна работы."

##### 9. Database

**Technology:** PostgreSQL

**Stored Data:**
- Tasks and test cases
- User submissions
- Execution results
- AI analysis reports

##### 10. File Storage

**Purpose:**
- Test case data
- Input/output files
- Custom checker scripts

### Execution Flow

```
1. User submits code
   ↓
2. API validates and saves submission
   ↓
3. Task sent to Kafka queue
   ↓
4. Worker consumes task
   ↓
5. Code executed in Sandbox (Docker)
   ↓
6. Test Engine runs test cases
   ↓
7. Scenario Runner (if needed) runs scenarios
   ↓
8. AI Module analyzes results
   ↓
9. Results stored in database
   ↓
10. Response sent to user
```

### Security Architecture

#### Isolation Strategy

```
User Code → Docker Container → Resource Limits → No Network → Read-only FS
```

#### Security Layers

1. **Network Layer**
   - Docker network disabled
   - No external connections
   - Internal service communication via Kafka

2. **Resource Layer**
   - CPU limits (CFS quota)
   - Memory limits (OOM killer)
   - Time limits (timeout)

3. **Filesystem Layer**
   - Read-only root filesystem
   - Temporary filesystem for /tmp
   - Bind mounts for code only

4. **Capability Layer**
   - Drop all Linux capabilities
   - No privilege escalation
   - No new privileges

5. **Code Analysis Layer**
   - Forbidden pattern detection
   - Infinite loop detection
   - Malicious code scanning

### Scalability

```
                    ┌──────────┐
                    │   Kafka  │
                    └──────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
   ┌────────┐      ┌────────┐      ┌────────┐
   │Worker 1│      │Worker 2│      │Worker N│
   └────────┘      └────────┘      └────────┘
        │                │                │
        └────────────────┼────────────────┘
                         │
                    ┌──────────┐
                    │ Sandbox  │
                    │ (Docker) │
                    └──────────┘
```

**Scaling Strategy:**
- Workers: Horizontal scaling based on queue depth
- Sandbox: Per-execution containers (auto-scaled)
- AI: Cache results, rate limit API calls
- Database: Read replicas for results

### Fault Tolerance

1. **Queue Persistence**
   - Kafka persists messages
   - At-least-once delivery
   - Dead letter queue for failures

2. **Worker Resilience**
   - Multiple worker instances
   - Automatic retry on failure
   - Circuit breaker for AI calls

3. **Sandbox Safety**
   - Container auto-cleanup
   - Timeout enforcement
   - Resource limit enforcement

### Technology Stack

| Component | Technology | Justification |
|-----------|-----------|---------------|
| Backend | Spring Boot + Kotlin | Type safety, productivity |
| Message Queue | Kafka | High throughput, persistence |
| Sandbox | Docker | Strong isolation, resource control |
| Database | PostgreSQL | ACID, JSON support |
| AI | ChatGPT API | Advanced NLP capabilities |
| Frontend | React | Modern UI, component-based |

### Why This Architecture is Correct

#### ✓ Scalability
- Workers can be scaled independently
- Queue buffers peak loads
- Sandbox containers are ephemeral

#### ✓ Security
- Docker provides strong isolation
- Resource limits prevent abuse
- Network disabled in containers

#### ✓ Flexibility
- Easy to add new languages
- Pluggable AI providers
- Different execution strategies

#### ✓ Extensibility
- AI module can be improved separately
- New test types can be added
- Custom checkers supported

#### ✓ Support for Complex Tasks
- Scenario Runner for interactive tasks
- State-dependent validation
- Multi-step testing

### Conclusion

This modular service-oriented architecture provides the right balance of:
- **Security** through Docker isolation
- **Scalability** through message queue and workers
- **Flexibility** through modular design
- **Innovation** through AI analysis and scenario testing

The architecture directly supports the scientific novelty of the thesis: **hybrid verification combining automated testing with AI-powered explanation and recommendations**.

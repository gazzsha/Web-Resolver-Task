# ГЛАВА 3. ПРОГРАММНАЯ РЕАЛИЗАЦИЯ

В настоящей главе изложена программная реализация веб-сервиса автоматизированной проверки решений задач по программированию. Описание сгруппировано по Gradle-модулям репозитория и отражает фактическое состояние кодовой базы на момент защиты, включая выполненные в фазе подготовки к защите расширения функциональности (фильтр задач по тематике, импорт задач из CSV-файлов, наблюдаемость на базе Prometheus [34] и Grafana, сквозное тестирование пользовательского интерфейса). Все упоминаемые элементы кода сопровождаются ссылками на файлы репозитория в формате `путь/файл.kt:строка`; полные листинги ключевых классов вынесены в Приложение А.

## 3.1. Реализация модуля sandbox

Модуль `sandbox` реализует ключевую с точки зрения безопасности подсистему — изолированное выполнение недоверенного пользовательского кода. Центральный класс — `DockerSandboxService` (`sandbox/src/main/kotlin/ru/sandbox/service/DockerSandboxService.kt`), оркестрирующий жизненный цикл одноразовых Docker-контейнеров через прямые вызовы `docker` CLI посредством `ProcessBuilder`. Выбор Docker-в-Docker на базе host-side daemon, а не gVisor или Firecracker, обоснован в разделе 1.3: гипервизор-уровневая изоляция оверкилл для образовательной нагрузки, тогда как комплекс harden-флагов даёт сопоставимый уровень защиты при значительно меньшей операционной сложности.

### 3.1.1. Hardening-флаги docker run

При запуске пользовательского контейнера применяется следующий набор флагов (см. `DockerSandboxService.kt:249–266`), формируемый методом `runInDocker`:

— `--network=none` — полное отключение сетевого стека внутри контейнера, что исключает класс атак, связанных с эксфильтрацией данных и подключением к управляющим серверам;
— `--read-only` — корневая файловая система контейнера монтируется только на чтение, любые попытки записи в системные каталоги (`/etc`, `/usr`, `/var`) приводят к ошибке EROFS;
— `--cap-drop=ALL` — сброс всего набора Linux capabilities, включая `CAP_NET_RAW`, `CAP_SYS_ADMIN`, `CAP_SYS_PTRACE`, что блокирует операции, требующие административных привилегий;
— `--tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m` — единственная доступная для записи область смонтирована как tmpfs с ограничением размера и атрибутами, запрещающими исполнение, повышение привилегий через SUID-биты и доступ к device-файлам;
— `--pids-limit=64` — ограничение числа процессов в namespace pids, что предотвращает fork-бомбы;
— `--security-opt=no-new-privileges:true` — установка флага `no_new_privs` в пространстве пользователя ядра, после чего исполняемые файлы внутри контейнера не могут повысить эффективные привилегии посредством SUID/SGID;
— `--stop-timeout=0` — отключение grace-периода при `docker stop`, гарантирующее немедленное прекращение работы контейнера; основной hard-timeout реализуется внешним watchdog;
— `-m ${memoryLimitMb}m` — лимит оперативной памяти, нарушение которого приводит к срабатыванию OOM-killer и установке флага `OOMKilled` в `docker inspect`;
— `--cpus ${cpuLimit}` — ограничение по CPU-shares через cgroup `cpu.max`.

Каталог с пользовательским исходным кодом монтируется внутрь контейнера как `/app` (read-write для записи `.peak_memory_bytes`, см. далее), рабочий каталог устанавливается в `/app`. Контейнер запускается в detached-режиме без флага `--rm`, поскольку после завершения требуется выполнить `docker inspect` для извлечения метрик; удаление контейнера явно выполняется в блоке `finally` методом `removeContainer` (`DockerSandboxService.kt:475–484`).

### 3.1.2. Watchdog hard-timeout

Существенным дефектом раннего прототипа была невозможность гарантированно прекратить выполнение зависшего контейнера: `docker stop` подразумевает grace-период по умолчанию 10 секунд, что при таймауте задачи в 5 секунд давало wall-time до 15 секунд. В рамках техдолгового пункта P0-2 реализован внешний watchdog на базе `ScheduledExecutorService` (`DockerSandboxService.kt:51–53`, `316–323`). При запуске контейнера планируется отложенная задача через `pollScheduler.schedule(...)` с задержкой `timeoutSeconds * 1000L` миллисекунд; при срабатывании задача устанавливает атомарный флаг `timedOutFlag` и вызывает `killContainer`, отправляющий контейнеру SIGKILL через `docker kill --signal=SIGKILL`.

Поверх watchdog введена страховка через `docker wait` с явным таймаутом `waitFor(timeoutSeconds + 2L, TimeUnit.SECONDS)` (`DockerSandboxService.kt:326–344`). Дополнительные 2 секунды компенсируют возможные задержки `ScheduledExecutorService` под высокой нагрузкой. Если процесс `docker wait` не завершился даже после страховочного интервала, вызывается `destroyForcibly` и контейнеру повторно отправляется SIGKILL. Таким образом, для любого пользовательского кода wall-time контейнера не превосходит `timeoutSeconds + 2` секунд независимо от характера зависания.

### 3.1.3. Разрешение verdict’а

Метод `resolveVerdict` (`DockerSandboxService.kt:426–443`) формирует канонический вердикт исполнения по сигналам, наблюдаемым со стороны хоста. Порядок приоритета условий критически важен: ранние версии помещали проверку `exitCode == 137` перед проверкой флага `timedOut`, что приводило к ошибочной классификации убитых watchdog’ом контейнеров как `MEMORY_LIMIT_EXCEEDED`. Это связано с тем, что SIGKILL преобразуется ядром Linux в код выхода 137 независимо от причины. Текущая последовательность:

1. `timedOut == true` → `TIME_LIMIT_EXCEEDED`;
2. `oomKilled == true` (флаг из `docker inspect .State.OOMKilled`) → `MEMORY_LIMIT_EXCEEDED`;
3. `exitCode == 137` → `MEMORY_LIMIT_EXCEEDED` (фактический OOM, watchdog не сработал);
4. `exitCode == 124` → `TIME_LIMIT_EXCEEDED` (страховочный путь);
5. `exitCode == 139` → `RUNTIME_ERROR` (SIGSEGV);
6. `exitCode != 0` → `RUNTIME_ERROR`;
7. шаблон stderr указывает на ошибку → `RUNTIME_ERROR`;
8. иначе → `Verdict.OK` (заменяется на `WRONG_ANSWER`/`PRESENTATION_ERROR` методом `compareOutput` в языковых лаунчерах).

### 3.1.4. Сбор пиковой памяти

Точное измерение пика потребления оперативной памяти при wall-time порядка нескольких сотен миллисекунд представляет нетривиальную инженерную задачу: polling `docker stats` с интервалом 100 мс на коротких программах пропускает фактический максимум, тогда как чтение cgroup-файла после SIGKILL может не успеть выполниться. В рамках пункта P1-6 реализован комбинированный подход (`DockerSandboxService.kt:361–369`, `wrapForPeakMemoryCapture`):

— **Запись из контейнера.** Команда пользователя оборачивается в shell-конструкцию, которая перед завершением читает cgroup v2-файл `/sys/fs/cgroup/memory.peak` и сохраняет значение в `/app/.peak_memory_bytes`. Каталог `/app` смонтирован с хоста, поэтому файл становится доступен после остановки контейнера.
— **Polling с хоста.** Параллельно работает фоновая задача `pollScheduler.scheduleAtFixedRate` (`DockerSandboxService.kt:303–306`), каждые 100 миллисекунд вызывающая `docker stats --no-stream --format "{{.MemUsage}}"` и обновляющая `AtomicLong peakMemoryBytes` через `compareAndSet`-цикл.
— **Финальное значение.** `effectivePeakBytes = maxOf(peakMemoryBytes.get(), cgroupPeakBytes)`. Подход устойчив к обоим режимам отказа: короткие программы получают значение из cgroup-файла, долгие — из polling, при принудительном SIGKILL контейнером без shutdown-hook’а используется накопленный polling-максимум.

### 3.1.5. Поддерживаемые языки

Текущая версия sandbox поддерживает два пользовательских языка: Java на базе образа `eclipse-temurin:21-jdk-alpine` и Python на базе `python:3.11-alpine` (`DockerSandboxService.kt:101–197`). Лаунчер Java записывает исходный файл с именем, извлечённым из `extractClassName`, и запускает командой `javac $className.java && java $className < input.txt`. Лаунчер Python запускает `python solution.py < input.txt`. Поддержка Kotlin исключена из MVP по результатам пункта P0-1: компилятор `kotlinc` имеет cold start свыше 10 секунд внутри Alpine-контейнера, что приводит к систематическим TLE даже на пустой программе. Код для Kotlin сохранён в репозитории (`DockerSandboxService.kt:134–167`) для возможного возврата при наличии pre-built builder-образа с jit-кэшем.

Алгоритм sandbox-исполнения, выполненный по нотации блок-схем ГОСТ 19.701-90 [7], показан на рисунке 3.1.

![Рисунок 3.1 — Блок-схема алгоритма sandbox-исполнения (ГОСТ 19.701-90)](../figures/07_sandbox_flowchart.png)

Рисунок 3.1 — Блок-схема алгоритма sandbox-исполнения (ГОСТ 19.701-90)

## 3.2. Реализация модуля ai-analyzer

Модуль `ai-analyzer` реализует нейро-символьный конвейер анализа решения. Архитектурно он представляет собой цепочку обработчиков `SimpleRuleBased ← GigaChatAnalyzer ← AstHybridAnalyzer`, где каждый последующий слой обогащает предыдущий и при сбоях прозрачно откатывается на резервный анализатор.

### 3.2.1. Сборка user-prompt в AnalyzerPrompts.userPromptFull

Метод `AnalyzerPrompts.userPromptFull` (`ai-analyzer/src/main/kotlin/ru/aianalyzer/prompt/AnalyzerPrompts.kt:117–163`) формирует пользовательскую часть сообщения для LLM из четырёх блоков, выводимых в строго фиксированном порядке:

1. **Язык программирования.** Идентификатор языка нормализуется фильтром, оставляющим только буквенно-цифровые символы и знаки `+`, `-` (защита от инъекций через атрибут `language`).
2. **Условие задачи.** Если `AnalyzeContext.taskDescription` не пуст, добавляется блок «Условие задачи:» с обрезкой до 4000 символов — это предотвращает съедание токенного бюджета описанием при сохранении достаточного контекста для содержательного разбора.
3. **Результат проверки sandbox.** При наличии агрегатов теста добавляется блок «Результат проверки sandbox:» с полями «Пройдено тестов: passed из total», «Итоговый verdict» и «Первая ошибка» (обрезка до 500 символов, очистка через `stripUnsafeOutput`).
4. **AST-факты.** Если предоставлен блок `astFactsBlock` (см. раздел 3.3), он встраивается с обёрткой `<AST_FACTS>...</AST_FACTS>`.
5. **Код студента.** Помещается между sentinel-маркерами `<<<STUDENT_CODE_BEGIN>>>` и `<<<STUDENT_CODE_END>>>`. Любое вхождение `<<<STUDENT_CODE_END>>>` внутри кода заменяется на литерал `###STUDENT_CODE_END_LITERAL###`, что исключает «закрытие» блока кода изнутри и инъекцию инструкций после маркера.

Системный prompt (`AnalyzerPrompts.kt:24–71`) явно фиксирует иерархию авторитетности: вердикт sandbox > AST-факты > суждение LLM, а также содержит анти-инъекционные правила (игнорирование инструкций из тела кода, запрет смены роли, запрет раскрытия system-prompt). Поддерживаются два варианта [35] — `ZERO_SHOT` и `FEW_SHOT` (с подмешиванием примеров разборов из `prompts/few-shot-examples.txt`); выбор управляется параметром `PromptVariant`.

Последовательность построения user-prompt из контекста анализа, AST-фактов и кода представлена в виде блок-схемы на рисунке 3.2.

![Рисунок 3.2 — Блок-схема построения user-prompt для AI-анализатора](../figures/08_user_prompt_build.png)

Рисунок 3.2 — Блок-схема построения user-prompt для AI-анализатора

### 3.2.2. GigaChatClient — OAuth2 и retry

Класс `GigaChatClient` (`ai-analyzer/src/main/kotlin/ru/aianalyzer/client/GigaChatClient.kt`) реализует низкоуровневый клиент к API GigaChat от ПАО «Сбербанк». Используется JDK `java.net.http.HttpClient` вместо Spring `WebClient`: в живых испытаниях reactor-netty демонстрировал ошибки 404 на запросах к `/api/v1/chat/completions` через HTTP/1.1 и HTTP/2, в то время как `curl` и JDK-клиент успешно отрабатывали (предположительная причина — отвергаемый edge-прокси `Transfer-Encoding: chunked`). Аутентификация реализована по схеме OAuth 2.0 client_credentials (`obtainAccessToken`): авторизационный ключ передаётся в заголовке `Authorization: Basic <authKey>`, scope — `GIGACHAT_API_PERS`. Полученный access-token кэшируется в `AtomicReference<CachedToken>` с TTL 30 минут; за 60 секунд до истечения выполняется превентивный refresh.

Метод `chatCompletion` оборачивает запрос в политику повторных попыток: до трёх попыток на 5xx-ответах и transient-ошибках сети (`HttpTimeoutException`, `IOException`) с экспоненциальной задержкой и jitter в диапазоне `±20%`. Базовая задержка `initialBackoff = 500ms`, последующие — `500 * 2^n` мс. Это устраняет редкие, но воспроизводимые провалы запросов на стороне Сбер-инфраструктуры в часы пиковой нагрузки.

### 3.2.3. GigaChatAnalyzer.callAndParse

Метод `GigaChatAnalyzer.callAndParse` (`ai-analyzer/.../service/GigaChatAnalyzer.kt:118–159`) реализует один полный цикл «построение prompt → запрос к LLM → парсинг → валидация». Если переданы `taskContext` или непустой блок AST, для построения user-сообщения используется `userPromptFull`; в противном случае — упрощённый `userPrompt`. Ответ LLM очищается от markdown-обёрток `stripJsonFences`, валидируется по JSON Schema через `schemaValidator.parseAndValidate`, после чего десериализуется в `GigaChatAnalysisPayload`. При несоответствии схеме делается одна повторная попытка с подсказкой в prompt (`userPromptRetry`), при повторном провале — fallback на rule-based анализатор.

Метод `mapPayload` (`GigaChatAnalyzer.kt:161–198`) транслирует ответ LLM во внутреннюю модель `AIAnalysisResult` и применяет verdict-guards. Поле `codeQuality` приводится в диапазон `0..100`, после чего применяется каскад верхних границ (раздел 3.2.6).

### 3.2.4. SchemaValidator — JSON Schema Draft 7

Класс `SchemaValidator` (`ai-analyzer/.../validation/SchemaValidator.kt`) загружает схему `explanation.json` (`ai-analyzer/src/main/resources/schemas/explanation.json`) по стандарту JSON Schema Draft 7. Схема описывает ожидаемые поля ответа LLM (`codeQuality: integer 0..100`, `issues: array of string ≤ 500 chars`, `recommendations: array`, `explanation: string ≤ 4000 chars`, `complexity: enum LOW|MEDIUM|HIGH|VERY_HIGH`) и ключевое свойство `additionalProperties=false`. Последнее принципиально для устойчивости к инъекциям: попытки модели вернуть дополнительные ключи (`override_quality`, `system_message`, `_admin_note`) приводят к ошибке валидации и принудительному повтору либо fallback’у. Out-of-range значения `codeQuality` (например, `codeQuality: 1000`) также отсекаются на этапе валидации.

### 3.2.5. InputSanitizer — нормализация и санитизация

Класс `InputSanitizer` (`ai-analyzer/.../sanitize/InputSanitizer.kt`) выполняет три задачи:

— **Нормализация Unicode NFKC** (`normalizeUnicode`) — приведение «совместимых» представлений символов к канонической форме; устраняет атаки на основе полноширинных символов (`ｉｆ` вместо `if`), которые иначе обходят как AST-анализатор, так и человеческий глаз;
— **Удаление управляющих, bidi- и zero-width-символов** — фильтрация диапазонов `U+0000..U+001F` (кроме `\n` и `\t`), `U+200B..U+200F` (zero-width), `U+2028..U+202F` (bidi-override), `U+FEFF` (BOM); это закрывает класс атак, в которых код визуально читается как корректный, но реально содержит скрытую логику;
— **Ограничение размера** (`enforceSizeLimit`) — превышение порога 16 КБ приводит к выбросу `InputTooLargeException`, что предотвращает token-exhaustion на стороне LLM и DoS на стороне токенизатора.

Метод `stripUnsafeOutput` применяется к строковым полям ответа LLM (`explanation`, элементы `issues` и `recommendations`) и вычищает HTML-теги, опасные URL-схемы (`javascript:`, `data:`) и фрагменты, выглядящие как код подмены роли.

### 3.2.6. Verdict-guards

В рамках пункта P0-4 реализован каскадный механизм ограничения `codeQuality` поверх ответа LLM (`GigaChatAnalyzer.kt:29–30, 161–178`). Логика:

— `total > 0 && passed == 0` (полный провал) → `codeQuality` не выше 40;
— `total > 0 && passed < total` (частичный провал) → `codeQuality` не выше 60;
— `passed == total` → ограничение не применяется.

Этот механизм решает воспроизводимую галлюцинацию из эксперимента V4: для решения `print("hello world")` на задаче «Valid Parentheses» baseline-LLM возвращал «code is great, well-structured» с `codeQuality=85`. При том, что sandbox сообщил `passed=0`, подобный ответ дезориентировал студента. Каскад каппов гарантирует, что обучающее объяснение и оценка качества не противоречат детерминированному вердикту тестов. В классе `AstHybridAnalyzer` (`AstHybridAnalyzer.kt:18, 105–114`) добавлен второй слой защиты: при `suspiciousReturnsConstant == true` и `codeQuality > 60` оценка приводится к 60 даже при формально пройденных тестах (типичная ситуация — недоопределённый набор тестов).

### 3.2.7. Caffeine-cache

В модуль внедрён локальный кэш на базе библиотеки Caffeine. Ключ кэша строится в методе `cacheKey` (`GigaChatAnalyzer.kt:252–276`) как SHA-256-дайджест от конкатенации: `language` → `code` → `promptVariant` → флаг наличия AST → fingerprint списка sandbox-вердиктов → префикс `taskDescription` (до 2048 символов). Включение всех этих компонентов в ключ принципиально: без `verdict-fingerprint` два сабмита с одинаковым кодом, но разными исходами тестов получили бы кэшированный результат, что обошло бы V4-каскад; без `promptVariant` запуск варианта B1f загрязнял бы кэш baseline B1; без `taskDescription` редакция текста задачи тихо возвращала бы устаревший разбор.

## 3.3. Реализация AST-экстрактора

AST-подмодуль предоставляет детерминированные структурные факты о коде, которые передаются LLM в составе user-prompt и используются в verdict-guards. Подсистема расположена в `ai-analyzer/src/main/kotlin/ru/aianalyzer/ast/`.

Класс данных `AstFact` (`AstFact.kt`) описывает девять полей: `language`, `hasLoop`, `hasRecursion`, `hasComparison`, `cyclomaticComplexity`, `methodCount`, `maxNestingDepth`, `suspiciousReturnsConstant`, `lineCount`. Метод `toPromptJson` сериализует факт в компактный JSON для встраивания в prompt, а функция расширения `spotlightForPrompt` оборачивает результат в блок `<AST_FACTS>...</AST_FACTS>`. Метод-фабрика `AstFact.empty(language)` возвращает «нулевой» факт; вызывающие используют его при ошибках анализа, чтобы LLM-конвейер никогда не падал из-за дефектов AST-парсера.

`JavaAstAnalyzer` использует библиотеку JavaParser версии 3.26: входной текст парсится в `CompilationUnit`, после чего AST обходится посредником `GenericVisitorAdapter`. Подсчитываются `ForStmt`, `WhileStmt`, `DoStmt`, `ForEachStmt` (детектор циклов), `MethodCallExpr` с совпадающим `MethodDeclaration.name` (детектор рекурсии), бинарные сравнения, узлы решений (для McCabe-сложности), глубина блоков. Признак `suspiciousReturnsConstant` поднимается, когда единственное тело метода-кандидата сводится к `return <literal>`.

`PythonRegexAnalyzer` реализует ту же логику на основе регулярных выражений — стандартный JVM-стек не предоставляет встроенного AST-парсера для Python, а подключение внешних рантаймов (Jython, GraalPy) увеличило бы артефакт-сборку на десятки мегабайт. Регулярные выражения покрывают определения функций (`def \w+`), циклы (`for `, `while `), сравнения, конструкции `if`/`elif`/`else`. Для обнаружения рекурсии после обхода всех `def`-имен проверяется наличие соответствующих вызовов внутри тела функции.

Диспетчер `AstMetricsService` (`AstMetricsService.kt`) принимает идентификатор языка, обрезает его до 32 символов (защита от 100 КБ-«языков», способных раздуть downstream-prompt), приводит к нижнему регистру и направляет вызов в соответствующий язык-специфический анализатор. Любое исключение, возникшее в недрах парсера, перехватывается обёрткой `runSafely` и заменяется на `AstFact.empty(language)`, что гарантирует устойчивость пайплайна.

## 3.4. Реализация модулей worker и task-resolver

Подсистема обработки решения состоит из двух независимых Spring Boot приложений, взаимодействующих через брокер Kafka.

### 3.4.1. Kafka-обмен

Производитель `TaskClusterProducer` в модуле `task-resolver` использует `org.springframework.kafka.core.KafkaTemplate<String, WorkerTaskMessage>` для публикации сообщений в топик `task-execution` с `acks=all`. Параметры топика настраиваются через `kafka.config.task-cluster.producer.*` в `application.properties`. Consumer-сторона worker’а представлена `WorkerKafkaListener` с аннотацией `@KafkaListener(topics = "task-execution", groupId = "worker-group", containerFactory = ...)`. Симметричная пара (worker-producer → task-resolver-listener) работает на топике `task-results`.

### 3.4.2. WorkerService.processTask

Метод `WorkerService.processTask` (`worker/src/main/kotlin/ru/worker/service/WorkerService.kt:21–128`) выполняет основной алгоритм проверки решения. Последовательность шагов:

1. **Прогон тестов с early-stop.** Для каждого `testCase` из сообщения вызывается `testEngine.runTest(code, language, testCase)`, который, в свою очередь, обращается к `DockerSandboxService`. При первом результате со статусом, отличным от `PASSED`, устанавливается флаг `stopped`; все последующие тесты не запускаются, а их результаты записываются как `TestStatus.SKIPPED` с пояснением «Пропущено — первый невалидный тест уже прерывает проверку». Это обеспечивает обратную связь студенту за время одного упавшего теста, а не за `N × timeout`, при сохранении показателя `passed/total` в полном объёме задачи.
2. **Прогон сценарных тестов** (если присутствуют) — делегируется в `ScenarioRunner`.
3. **Определение итогового статуса** через `determineTaskStatus`: все тесты пройдены → `SUCCESS`, есть `ERROR` → `FAILED`, есть `PASSED` среди упавших → `PARTIAL_SUCCESS`, иначе → `FAILED`.
4. **Построение AnalyzeContext.** Агрегируются `passedTestsCount`, `totalTestsCount`, первая неудача (для извлечения `overallVerdict` и `firstError`), пробрасывается `taskDescription` из исходного сообщения. Контекст передаётся в `aiAnalyzer.analyze(...)`, где используется при построении prompt и применении verdict-guards.
5. **Сохранение результата.** Через consumer-сторону `task-resolver` (`TaskResultKafkaListener`) сообщение десериализуется и сохраняется как `TaskResultEntity` с привязанным `AIAnalysisEntity` (отношение `@OneToOne` с `FetchType.LAZY` и `cascade=ALL`).

Класс `TaskResolverController` реализует сгенерированный из OpenAPI интерфейс `web.TaskResolverApi`; модель данных и схемы запросов/ответов происходят из `api-generator/resources/api/task-resolver-api.yml` и `task-results-api.yml`. Это устраняет дублирование DTO между фронтендом и бэкендом: TypeScript-клиенты на стороне SPA генерируются из тех же YAML-спецификаций.

## 3.5. Реализация фронтенда

Фронтенд — single-page-приложение на стеке Vite 5 + React 18 + TypeScript 5.3 + Material UI v5 + Zustand + `@monaco-editor/react`. Расположение исходников — каталог `frontend/`.

### 3.5.1. Роутинг

Маршрутизация реализована через `react-router-dom`. Зарегистрированы страницы: `/login` (`Login.tsx`), `/register` (`Register.tsx`), `/` (`Dashboard.tsx`), `/tasks` (`TaskList.tsx`), `/tasks/:id` (`TaskDetail.tsx`), `/tasks/:id/submit` (`Submission.tsx`), `/submissions` (`Submissions.tsx`), `/statistics` (`Statistics.tsx`), `/results/:id` (`Results.tsx`) и добавленная при подготовке к защите `/admin/import` (`AdminImport.tsx`, доступна только пользователям с ролью `TEACHER`). Защищённые маршруты обёрнуты в HOC `RequireAuth`, перенаправляющий неавторизованных пользователей на `/login` с сохранением `next`-параметра.

### 3.5.2. Редактор Monaco

Компонент `EditorPane` (`frontend/src/components/submission/EditorPane.tsx`) инстанцирует `@monaco-editor/react`. Атрибут `language` динамически переключается между `java` и `python` в зависимости от выбора в селекторе. Активированы live-completions (`quickSuggestions: true`), подсветка синтаксиса, автоматическое сворачивание, минимальный размер `minimap.enabled=false`. Установлен тёмный шаблон `vs-dark`. Ввод языка ограничен набором {Java, Python}, что соответствует возможностям sandbox (раздел 3.1.5).

### 3.5.3. Polling submissions

Страница результатов (`Results.tsx`) ожидает асинхронного завершения проверки. Применяется опрос API: `setInterval(() => fetchResult(id), 2000)`. Опрос останавливается при попадании `status` в множество терминальных значений `{SUCCESS, FAILED, ERROR, PARTIAL_SUCCESS}`, после чего рендерится финальный экран с разбивкой по тестам и результатом AI-анализа. Дополнительно реализован максимальный таймаут опроса (3 минуты), по истечении которого выводится сообщение об ошибке.

### 3.5.4. Zustand store и axios interceptor

Состояние аутентификации хранится в Zustand store (`frontend/src/store/auth.ts`): поля `accessToken`, `refreshToken`, `user`. Сохранение в `localStorage` через middleware `persist`. Axios-инстанс (`frontend/src/services/api.ts`) сконфигурирован с `baseURL` из переменной окружения `VITE_API_BASE_URL` и двумя перехватчиками: request-interceptor добавляет `Authorization: Bearer <accessToken>`, response-interceptor при HTTP 401 пробует обновить токен через `POST /auth/refresh` и повторяет исходный запрос; при повторном 401 — принудительный logout и редирект на `/login`.

## 3.6. Расширения функциональности перед защитой

В дополнение к функционалу, описанному в разделах 3.1–3.5, в фазе подготовки к защите реализованы четыре крупных расширения, востребованных в учебном процессе кафедры. Перечень и обоснование выбора этих фич восходит к функциональным требованиям, сформулированным в НИР 2025 года, но не материализованным в коде на момент промежуточной отчётности.

### 3.6.1. Фильтр задач по тематике

**Уровень БД.** Миграция Flyway `V8__add_category.sql` (`db/src/main/resources/db/migration/V8__add_category.sql`) добавляет колонку `category VARCHAR(64)` к таблице `test` инструкцией `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`, создаёт частичный индекс `idx_test_category` для ускорения фильтрации и выполняет backfill категорий для seed-задач из миграций V4 и V5. Классификация соответствует таксономии LeetCode/Codeforces: «Array», «Stack», «Math», «String», «LinkedList», «SlidingWindow» и собирательная категория «General» для задач без явной классификации.

**Уровень JPA.** Сущность `Test` (`db/src/main/kotlin/ru/db/entity/Test.kt`) расширена полем `var category: String?` с аннотацией `@Column(name = "category", length = 64)`. В репозитории `TestRepository` добавлен JPQL-запрос `searchByFilters(category: String?, difficulty: Difficulty?)` с условиями вида `(:category IS NULL OR t.category = :category) AND (:difficulty IS NULL OR t.difficulty = :difficulty)`, что позволяет применять нулевые фильтры без динамической генерации SQL. Дополнительно реализован запрос `findDistinctCategories()`, возвращающий упорядоченный список ненулевых категорий для построения чипов на фронтенде.

**Уровень OpenAPI.** Спецификация `api-generator/resources/api/task-results-api.yml` дополнена параметрами запроса `?category=` и `?difficulty=` для `GET /api/v1/tasks` и новым ресурсом `GET /api/v1/tasks/categories`, возвращающим массив строк.

**Уровень фронтенда.** Страница `TaskList.tsx` (`frontend/src/pages/TaskList.tsx`) отрисовывает над таблицей задач полосу чипов MUI `<Chip variant="outlined" />`. При монтировании страница вызывает `taskService.getCategories()` для предварительной загрузки набора, после чего применяется клиентская фильтрация по уже загруженному списку задач — это даёт мгновенный отклик при переключении категорий и снимает нагрузку с бэкенда при небольшом объёме каталога. Серверная фильтрация задействуется только при первой загрузке списка.

### 3.6.2. CSV-импорт задач

Функция предоставляет преподавателям возможность массовой загрузки задач из CSV-файла без необходимости ручного создания каждой задачи через UI.

**Endpoint.** Добавлен `POST /api/v1/admin/tasks/import` (multipart/form-data), описанный в `api-generator/resources/api/task-results-api.yml`. Контроллер `TasksController` в модуле `task-resolver` принимает `MultipartFile`, делегирует обработку в `TaskImportService` и возвращает DTO `TaskImportResult { importedCount, skippedCount, errors: [{line, message}] }`. Доступ ограничен аннотацией `@PreAuthorize("hasRole('TEACHER')")`, что включается за счёт `@EnableMethodSecurity` в `SecurityConfig`.

**Сервис.** Класс `TaskImportService` (`task-resolver/src/main/kotlin/ru/taskresolver/service/TaskImportService.kt`) использует библиотеку `commons-csv 1.12.0`. Ожидаемый формат заголовков:
`title,difficulty,category,description,return_type,arguments_json,tests_json`. Парсинг реализован через `CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true)`. Сложные структуры (`arguments_json`, `tests_json`) десериализуются Jackson’ом по `TypeReference`. Защита от дублей — case-insensitive сравнение `title`: предзагружаются все существующие title в `Set<String>` (с приведением к нижнему регистру), при коллизии строка пропускается со счётчиком `skippedCount`. Метод аннотирован `@Transactional`: при необработанной ошибке транзакция откатывается, при штатных ошибках строки (например, некорректный JSON в `tests_json`) сообщение записывается в `errors`, остальные строки обрабатываются.

**Фронтенд.** Страница `AdminImport.tsx` (`frontend/src/pages/AdminImport.tsx`) содержит file-input для CSV, кнопку скачивания CSV-шаблона (генерируется на клиенте, blob-URL) и панель с результатом импорта (статистика + список ошибок). В сайдбаре навигации (`frontend/src/components/layout/Sidebar.tsx`) пункт «Импорт задач» отображается только для пользователей с `role === 'TEACHER'`, что обеспечивается атрибутом `requireRole: 'TEACHER'` в декларации маршрутов.

### 3.6.3. Наблюдаемость: Grafana, Prometheus и SLO/SLA

**Метрики на стороне приложения.** В `MainApplication/build.gradle.kts` добавлена зависимость `io.micrometer:micrometer-registry-prometheus:1.13.6`. В `MainApplication/src/main/resources/application.properties` включён Actuator-endpoint `/actuator/prometheus` и настроены распределения латентности HTTP-запросов:

— `management.metrics.distribution.percentiles-histogram.http.server.requests=true` — публикация полных histogram-bucket’ов, необходимых для расчёта p50/p95/p99 на стороне Prometheus через функцию `histogram_quantile`;
— `management.metrics.distribution.percentiles.http.server.requests=0.5,0.95,0.99` — публикация заранее вычисленных перцентилей (для быстрых панелей без histogram-агрегации);
— `management.metrics.distribution.slo.http.server.requests=200ms,500ms,1s,3s,10s,30s` — SLO-bucket’ы, соответствующие ступенчатой шкале целевых значений.

**Custom-метрики доменного слоя.** В дополнение к стандартным HTTP/JVM/process-метрикам внедрены три Spring-бина для доменно-специфичных измерений:

— `ai-analyzer/src/main/kotlin/ru/aianalyzer/metrics/AiAnalyzerMetrics.kt` экспортирует `ai_analyzer_calls_total{variant,outcome}` (теги `variant` ∈ {gigachat, ast-hybrid, rule-based}, `outcome` ∈ {success, schema_error, fallback, cache_hit}), histogram `ai_analyzer_latency_seconds` со SLO-bucket'ами 500мс/1с/2с/5с и счётчик `ai_analyzer_schema_valid_total{valid}`. Инструментация выполнена в `GigaChatAnalyzer.analyzeWithExtraContext` и `AstHybridAnalyzer.analyze` через `Timer.Sample`, что корректно обрабатывает все пути возврата (success / schema-retry / fallback);
— `sandbox/src/main/kotlin/ru/sandbox/metrics/SandboxMetrics.kt` экспортирует histogram `sandbox_execution_seconds{language,verdict}` со SLO-bucket'ами 1с/3с/10с/30с и счётчик `sandbox_oom_killed_total`. Инструментация выполнена в `DockerSandboxService.runInDocker`; тег `language` нормализуется до закрытого множества {java, python, kotlin, unknown} во избежание неограниченной кардинальности;
— `worker/src/main/kotlin/ru/worker/metrics/WorkerMetrics.kt` экспортирует histogram `worker_processing_seconds` и счётчик `worker_submissions_total{status}` (значения `status` соответствуют enum `TaskStatus`).

Все три бина получают `MeterRegistry` через constructor injection. Параметры конструкторов сервисов сделаны nullable (`AiAnalyzerMetrics? = null`), чтобы изолированный экспериментальный runner в `ai-analyzer/experiment/Runner.kt` продолжал работать без Spring-контекста.

**Hardening /actuator/prometheus.** Endpoint защищён двумя слоями (defense-in-depth) в `MainApplication/src/main/kotlin/ru/security/SecurityConfig.kt`:

1. IP-allowlist через `IpAddressMatcher` — допускаются только CIDR `172.16.0.0/12` (Docker bridge), `127.0.0.1/32` и `::1`;
2. Basic-auth с in-memory пользователем `prometheus-scraper` (роль `OPS`), пароль читается из `${PROMETHEUS_SCRAPER_PASSWORD}` (для dev-стенда дефолт `scraper-dev-pass`).

Логика собрана в `AuthorizationManager<RequestAuthorizationContext>`, который последовательно применяет оба правила: запрос проходит только при одновременном выполнении обоих условий. Prometheus-контейнер читает пароль из bind-mount файла `secrets/scraper_password` (каталог `secrets/` в `.gitignore`). Подход устраняет фактический риск утечки внутренней структуры (имена бинов, GC-параметры, JVM-стек) при компрометации сетевого периметра.

**Инфраструктура.** В `docker-compose.yml` под профилем `monitoring` подняты шесть сервисов:

— `prometheus:v2.55.0` с конфигурацией `monitoring/prometheus.yml`, смонтированной директорией `monitoring/rules/` (rule files) и каталогом `secrets/` (пароль scraper'а), порт 9090;
— `alertmanager:v0.27.0` с конфигом `monitoring/alertmanager.yml` (минимальный dev-receiver `null`; для production заготовлены закомментированные блоки slack/telegram), порт 9093;
— `grafana:11.3.0` с автоматическим провижионингом datasource `prometheus` и шести дашбордов, порт 3000;
— `prometheuscommunity/postgres-exporter:v0.16.0` — метрики `pg_up`, `pg_stat_*`, `pg_database_size_bytes`, `pg_locks_count`, порт 9187;
— `danielqsj/kafka-exporter:v1.8.0` — метрики `kafka_consumergroup_lag`, `kafka_topic_*`, `kafka_brokers`, порт 9308;
— `prom/node-exporter:v1.8.2` — CPU/RAM/disk хост-машины, порт 9100 (на macOS Docker Desktop запрещает mount-propagation `rslave` на корневой каталог, поэтому контейнер не стартует на dev-машине; для production-Linux работает штатно).

**Дашборды.** В `monitoring/grafana/dashboards/` шесть автопровижионных JSON-файлов:

— `slo-dashboard.json` (девять панелей, Web Resolver — SLO / SLA), приведён в Приложении Б на рисунке Б.10;
— `jvm-dashboard.json` — heap/non-heap память, GC pauses p95, threads, classloader, CPU usage (рисунок Б.11);
— `kafka-dashboard.json` — consumer lag, throughput per topic, partition counts, commit rate (рисунок Б.12);
— `postgres-dashboard.json` — connections (всего и по state), transactions commit/rollback, buffer cache hit ratio, locks by mode, database size (рисунок Б.13);
— `sandbox-dashboard.json` — execution p95 по языкам, verdict distribution, OOM-rate, execution rate per language (рисунок Б.14);
— `ai-dashboard.json` — calls by variant/outcome, fallback rate (5m vs 1h тренд), schema valid ratio, latency p50/p95/p99 (рисунок Б.15).

**SLO-targets и burn-rate alert rules.** Файл `monitoring/rules/web-resolver.rules.yml` содержит восемь групп и 40 правил (recording + alert), валидируется командой `promtool check rules`. Стратегия алертинга — multi-window multi-burn-rate (Google SRE Workbook): для каждого ratio-SLI создаётся три правила с разными окнами и порогами burn-rate. Для SLO HTTP error rate (≤ 1%, бюджет 30 дней):

— `HttpErrorBudgetBurnFast` (severity=critical): окна 5m × 1h, burn 14.4× — бюджет сгорит за двое суток;
— `HttpErrorBudgetBurnMedium` (severity=high): окна 30m × 6h, burn 6× — за пять суток;
— `HttpErrorBudgetBurnSlow` (severity=medium): окна 6h × 3d, burn 1× — тренд деградации.

Аналогичная схема применена к AI fallback-rate (SLO ≤ 10%, критический порог в формуле клампирован до 0.5 как максимально возможное значение ratio). Латентность HTTP/sandbox/submission проверяется статическим порогом по p95 за 5m, поскольку для одностороннего SLI без бюджета burn-rate неприменим. Операционные алёрты вне SLO-burn: `KafkaConsumerLagHigh`, `PostgresDown`, `PostgresHighConnections`, `JvmMemoryPressure`, `InstanceDown` — все с `runbook_url` для on-call.

**Целевые значения SLO** зафиксированы в `monitoring/README.md` и согласуются с производственными требованиями к интерактивному веб-сервису: HTTP error rate ≤ 1% (hard 5%), HTTP p95 ≤ 1 с (hard 3 с), submission start p95 ≤ 500 мс (hard 1 с), sandbox p95 ≤ 10 с (hard 15 с), fallback-rate AI ≤ 10% (hard 30%).

**Тесты подсистемы наблюдаемости.** В `MainApplication/src/test/kotlin/ru/observability/`:

— `MetricsExposureTest` (Spring Boot integration test с `@Testcontainers` + PostgreSQL 16) — проверяет, что `/actuator/prometheus` отдаёт 200 при наличии basic-auth `prometheus-scraper:scraper-dev-pass` и 401 без авторизации, что в теле ответа присутствуют ключевые метрики (`http_server_requests_seconds_count`, `jvm_*`, `worker_processing_seconds`), и что счётчик HTTP-запросов инкрементируется ровно на единицу после каждого вызова `/actuator/health`;
— `AlertRulesValidationTest` — запускает `docker run --rm prom/prometheus:v2.55.0 promtool check rules` против файла `monitoring/rules/web-resolver.rules.yml` через `ProcessBuilder`, проверяет exit-code 0 и наличие маркера `SUCCESS` в выводе. Гейт активируется переменной окружения `RUN_PROMTOOL_TESTS=true`, по умолчанию пропускается, чтобы не требовать Docker daemon в каждом локальном прогоне.

### 3.6.4. Сквозное тестирование интерфейса (E2E)

В каталоге `frontend/e2e/` развёрнут стек Playwright 1.49.0 с проектом Chromium. Конфигурация `frontend/playwright.config.ts` устанавливает `testDir: './e2e'`, параллельное выполнение тестов (`fullyParallel: true`), сборку отчёта в форматах list и HTML. Раздел `webServer` поднимает Vite через `npm run dev -- --port 5173` и ожидает готовности до 60 секунд; при перезапуске `reuseExistingServer` повторно использует уже работающий dev-сервер.

Ключевая инженерная особенность — отсутствие зависимости от поднятого бэкенда. Файл `e2e/fixtures.ts` содержит:

— функцию `loginAs(page, role)`, которая программно записывает в `localStorage` валидную модель пользователя с указанной ролью (STUDENT/TEACHER), эмулируя пройденную аутентификацию;
— функцию `mockApi(page)`, перехватывающую через `page.route('**/api/v1/**', ...)` все REST-запросы и возвращающую детерминированные ответы (списки задач, описания, результаты submission, выдачу импорта CSV).

Это устраняет требование к локальному запуску Postgres, Kafka, MainApplication и Worker для прогона E2E; стенд для E2E не нужен. Реализовано десять сценариев в трёх spec-файлах: `auth.spec.ts` (регистрация, логин, защита маршрутов, refresh-токен), `tasks-filter.spec.ts` (фильтр по категориям, переключение чипов, сохранение фильтра в URL), `admin-import.spec.ts` (доступ только для TEACHER, валидация CSV, отображение списка ошибок). В `package.json` добавлены скрипты `e2e` (headless-прогон), `e2e:ui` (интерактивный режим Playwright) и `e2e:report` (открытие HTML-отчёта последнего запуска).

Диаграмма состояний submission, отражающая допустимые переходы между статусами PENDING → PROCESSING → SUCCESS/PARTIAL_SUCCESS/FAILED/ERROR, приведена на рисунке 3.3.

![Рисунок 3.3 — Диаграмма состояний жизненного цикла submission](../figures/09_submission_state.png)

Рисунок 3.3 — Диаграмма состояний жизненного цикла submission

### 3.6.5. Двухпроходный security-аудит и устранение найденных дефектов

После завершения функциональной разработки и подсистемы наблюдаемости был выполнен двухпроходный security-аудит ветки `feature` по методологии Trail of Bits (skill `differential-review`). Аудит проводился сабагентом `security-auditor` в режиме SURGICAL: первый проход покрыл периметр (Spring Security, GigaChat-клиент, sandbox, CSV-импорт), второй — внутренние подсистемы (JWT-провайдер, Kafka-конвейер worker, AST-экстракторы, scenario-runner). Артефактом является файл `DIFFERENTIAL_REVIEW_REPORT.md` в корне репозитория.

Аудит проводился относительно угроз, систематизированных в главе 1: набора атак на контейнерную изоляцию (подраздел 1.3 — container escape, исчерпание ресурсов), классификации OWASP применительно к большим языковым моделям (подраздел 1.4 — LLM01 prompt injection, LLM02 небезопасная обработка выходных данных модели, LLM06 раскрытие системного промпта, LLM09 чрезмерное доверие к вердикту модели), а также требования детерминированности символьного слоя в нейро-символьной композиции (подраздел 1.5 — иерархия авторитетности «вердикт песочницы → AST → LLM»). Каждая найденная дефектность сопоставлена с соответствующим пунктом обзора, что позволяет показать практическое замыкание исследования: декларированные в обзоре риски действительно проявлялись в реализации и были устранены явным конструктивным способом.

Всего зафиксировано 27 находок с распределением по severity: 1 CRITICAL, 1 HIGH-граничный CRITICAL, 6 HIGH, 10 MEDIUM, 8 LOW, 1 INFO. Все 27 устранены в шести фиксирующих коммитах с конвенцией `fix(security): address differential-review F-…`. Наиболее значимые из них приведены ниже.

**F-1 (CRITICAL) — инверсия IP-allowlist в `SecurityConfig.prometheusAccess()`.** Кастомный `AuthorizationManager` возвращал `null` при провале IP-проверки; Spring Security 6 в `AuthorizationFilter#doFilter` трактует `null` как «abstain», что не вызывает `AuthorizationDeniedException` и фактически означает «grant». В результате `/actuator/prometheus` был открыт всему интернету при любом deployment вне Docker-bridge сети. По классификации главы 1 (подраздел 1.3) этот класс дефектов относится к расширению поверхности атаки контейнерной инфраструктуры за пределы намеренного периметра. Исправлено заменой на `AuthorizationDecision(false)`; добавлен MockMvc-регрессионный тест с источником `8.8.8.8`, проверяющий 403.

**F-15 (HIGH/CRITICAL-class) — JWT-провайдер допускал fallback на публично известный secret.** В `JwtTokenProvider` присутствовала ветка `if (secret.length < 32) { useDevSecret() }`, печатавшая WARN и инициализировавшая HMAC-ключ строкой `dev-secret-32-bytes-padded-here!!`, опубликованной в репозитории. Любой деплой без `JWT_SECRET` в окружении получал предсказуемый ключ, позволявший forge'нуть токен с ролью TEACHER и достучаться до `/api/v1/admin/tasks/import`. Исправлено заменой на `require(secret.length >= 32) { ... }` — fail-fast при некорректной конфигурации.

**F-16, F-17 (HIGH) — поглощение poison-сообщений Kafka и отсутствие cap'а на размер `code`-поля.** `WorkerKafkaListener` оборачивал всю обработку в `try/catch` с безусловным `acknowledgment.acknowledge()`, что означало тихую потерю каждого сбоя без DLQ. Параллельно отсутствовал `MAX_PARTITION_FETCH_BYTES_CONFIG` и валидация `WorkerTaskMessage.code.length`, что при `worker.concurrency=3` создавало вектор OOM через крупные подачи. Исправлено: добавлены `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` в `KafkaWorkerConfig` (3 retry × `FixedBackOff(500ms)` → `task-execution.DLT`), удалён `try/catch` из листенера, добавлены лимиты на уровне consumer-фабрики (`MAX_PARTITION_FETCH_BYTES=1 MiB`, `MAX_POLL_RECORDS=1`) и валидация размера в листенере (`MAX_CODE_LENGTH=65 536`).

**F-19, F-20 (MEDIUM) — отсутствие thread-safety в AST-экстракторах.** `JavaAstAnalyzer` использовал `StaticJavaParser`, который документирован проектом javaparser как небезопасный для concurrent-доступа. `KotlinAstAnalyzer` явно отмечал в javadoc, что `KtPsiFactory` не thread-safe, но был сконфигурирован как singleton. При `worker.concurrency=3` это давало некорректные AST-факты для случайных subset'ов сабмишенов. Поскольку именно AST-слой формирует второй по авторитетности источник в иерархии нейро-символьной композиции (подраздел 1.5), его недетерминированность подрывает теоретическое обоснование архитектуры: «верифицируемые символьные факты», на которых строится защита от галлюцинаций LLM, перестают быть верифицируемыми. Исправлено: `JavaAstAnalyzer` использует локальный `JavaParser()` per call с проверкой `isSuccessful`; `KotlinAstAnalyzer.analyze()` обёрнут в `synchronized(psiLock)`, добавлен `AutoCloseable`-контракт для корректного освобождения IntelliJ `Disposable` (исправлен попутный leak). Регрессионные тесты `JavaAstAnalyzerConcurrentTest` и `KotlinAstAnalyzerConcurrentTest` запускают 150 параллельных извлечений против sequential baseline и фиксируют поведение, ожидаемое для детерминированного символьного слоя.

**F-21 (MEDIUM) — `scenario-runner` был привязан к stub-бину «always PASSED».** В `WorkerServiceConfig.scenarioRunner()` была anonymous-реализация `ScenarioRunner`, возвращавшая `status="PASSED"` для каждого шага вне зависимости от кода, языка и входа. Реальная реализация `DefaultScenarioRunnerImpl` существовала в модуле `scenario-runner`, но не подключалась. Любая задача с `scenarioTests` грейдилась тривиально (вывод фиксированной строки давал полный балл). Исправлено: stub удалён, в фабрике инжектится `DefaultScenarioRunnerImpl(dockerSandboxService)`; попутно `runStep` заменён с `contains(ignoreCase=true)` на `expected.trim() == actual.trim()` для устранения F-26.

Особое значение для подтверждения положений главы 1 имеет блок находок F-4, F-8, F-9, относящихся к подсистеме обращения к LLM-провайдеру. **F-4** — небезопасный TLS-trust-all в `GigaChatClient`, открывавший возможность MITM-атаки на канал к GigaChat и кражи долгоживущего OAuth-ключа; данный дефект соответствует расширенному толкованию LLM06 (раскрытие чувствительной информации в цепочке поставок LLM), сформулированному в OWASP LLM Top 10 (подраздел 1.4). **F-8** — полный пользовательский промпт, включая исходный код обучающегося, выводился в INFO-лог GigaChat-клиента, что в случае пересылки логов в централизованный коллектор противоречит требованию минимизации передачи персональных данных и косвенно — LLM06. **F-9** — путь повторного запроса при провале JSON-схемы (`userPromptRetry`) использовал ослабленный промпт без AST-блока и без агрегатов sandbox-вердикта; это создавало вектор практической атаки, в которой обучающийся, форсирующий single schema-failure (например, через инструкцию модели «ответь только словом OK»), смещал свою подачу на путь анализа с потерянным символьным контекстом и тем самым обходил часть защит, обоснованных в подразделе 1.5. Все три дефекта устранены: TLS-trust-all закрыт за флагом `gigachat.tls.trust-all` с loud-warn на каждый старт, полный промпт перенесён с INFO на TRACE, `userPromptRetry` теперь принимает те же `astFactsBlock` и `taskContext`, что и `userPromptFull`.

Прочие 19 находок покрывают: CSV formula-injection в `TaskImportService` (F-7) — латентная уязвимость, проявляющаяся при появлении в системе функции экспорта каталога; race в дедупликации CSV-импорта на уровне БД (F-10) — закрыта case-insensitive `UNIQUE INDEX` в миграции V9 с обработкой `DataIntegrityViolationException` в сервисе; валидация subject как UUID и clock-skew в JWT (F-22, F-23) — отнесены к категории требований OWASP к подсистеме аутентификации; генерализация error-сообщений (F-11), понижение уровня verbose-логов до DEBUG (F-25), truncation полезной нагрузки в shared-утилитах Kafka (F-27). Каждая находка имеет короткий fix (1–10 строк) и регрессионный тест либо MockMvc-кейс.

Подсистема наблюдаемости получила собственные регрессионные тесты по результатам аудита: `SecurityConfigTest` (5 кейсов, F-1/F-2), `KafkaWorkerConfigTest` (reflection-проверка `commonErrorHandler` — F-16), `WorkerKafkaListenerTest` (5 кейсов propagate-not-ack семантики — F-16/F-17), `JavaAstAnalyzerConcurrentTest` и `KotlinAstAnalyzerConcurrentTest` (по 150 параллельных извлечений против sequential baseline — F-19/F-20), `WorkerServiceConfigTest` (pin runtime-класса бина — F-21), а также расширенный `JwtTokenProviderTest` (11 кейсов вместо 4). Все тесты unit-уровня, прогоняются в обычной сборке `./gradlew test`; integration-тесты (`MetricsExposureTest`, `AlertRulesValidationTest`) гейтятся `RUN_INTEGRATION_TESTS=true` и `RUN_PROMTOOL_TESTS=true` соответственно.

Итоговая корреляция между угрозами, идентифицированными в обзоре главы 1, и обнаруженными в ходе аудита практическими дефектами реализации сведена в таблице 3.1. Каждая строка таблицы устанавливает соответствие между разделом аналитического обзора, классом угрозы и конкретными находками с указанием их исходных идентификаторов; подобное сопоставление подтверждает методологическую полноту работы — заявленные в постановочной части риски действительно исследовались и были закрыты на этапе реализации.

Таблица 3.1 Соответствие угроз главы 1 и закрытых дефектов аудита

| Раздел главы 1 | Класс угрозы | Закрытые findings |
|---|---|---|
| 1.3 (контейнерная изоляция) | Расширение поверхности атаки за пределы периметра | F-1, F-2, F-3, F-15 (default-credential vectors) |
| 1.4 (LLM01) | Prompt injection в исходном коде обучающегося | F-9 (retry path), реализация sentinel-меток (см. 3.2.1) |
| 1.4 (LLM02) | Небезопасная обработка выходных данных модели | F-8 (логирование полного ответа), `stripUnsafeOutput` (см. 3.2.5) |
| 1.4 (LLM06) | Раскрытие чувствительной информации в цепочке LLM | F-4 (MITM на GigaChat), F-8 (PII в логах) |
| 1.4 (LLM09) | Чрезмерное доверие к вердикту модели | F-9 (защита retry-пути), реализация verdict-guards (см. 3.2.6) |
| 1.5 (детерминированность символьного слоя) | Недетерминированность AST-фактов | F-19, F-20 (thread-safety AST) |
| Не входит в обзор (Kafka-конвейер) | Потеря сообщений / DoS через unbounded payload | F-16, F-17, F-18 |
| Не входит в обзор (CSV-импорт) | Injection-векторы в смежных подсистемах | F-5, F-7, F-10, F-11, F-12 |
| Не входит в обзор (стандартные JWT-практики) | Криптографические дефекты | F-15, F-22, F-23 |

## 3.7. Решения по MVP-границам

Жёсткое ограничение сроков разработки (14 дней до даты защиты) и принцип «лучше работающая подсистема, чем декларированная» потребовали явных решений об исключении части функциональности из MVP. Решения зафиксированы в `DEVELOPMENT_PLAN.md` и здесь систематизированы.

**Kotlin как пользовательский язык — исключён.** Эмпирически подтверждено (пункт P0-1), что cold start `kotlinc` внутри образа `web-resolver/kotlin:1.9.22` (Alpine + Kotlin Compiler) превышает 10 секунд даже на пустой программе. При типичном таймауте задачи 5 секунд это даёт детерминированный TLE на компиляции, что неприемлемо в учебном контексте. Бэкенд возвращает HTTP 400 «Unsupported language: kotlin», селектор языка во фронтенде не предлагает Kotlin. Sandbox-код для Kotlin (`DockerSandboxService.kt:134–167`) сохранён и будет восстановлен при наличии pre-built builder-образа с предкомпилированным компилятором.

**E-mail подтверждение регистрации — отложено.** Декларировано в НИР 2025 года, но исключено по явному решению научного руководства: для академического стенда внутри корпоративной сети факультета подтверждение по электронной почте не несёт измеримой ценности. Регистрация выполняется одношагово по логину/паролю; адрес электронной почты сохраняется в профиле без верификации.

**Сценарные тесты — реализованы в sandbox, но в UI выведены ограниченно.** Класс `ScenarioRunner` и обработка `scenarioResults` в `WorkerService.processTask` функциональны и используются на стороне worker’а; однако фронтенд показывает только агрегированный статус сценарной части, без пошаговой разбивки. Полноценный UI для сценарных тестов отнесён в категорию задач P2 и реализуется как направление развития.

## 3.8. Развёртывание

Развёртывание системы реализовано через единый файл `docker-compose.yml` в корне репозитория. Поднимаются следующие сервисы:

— `postgres:16-alpine` — основная реляционная СУБД, том `/var/lib/postgresql/data`, порт 5432;
— `confluentinc/cp-zookeeper:7.5.0` и `confluentinc/cp-kafka:7.5.0` — координационный сервис и брокер сообщений;
— `main-application` (собираемый локально из `MainApplication/Dockerfile`) — основной HTTP-API на порту 8080;
— `worker` (`worker/Dockerfile`) — отдельный экземпляр для асинхронной обработки;
— `prometheus:v2.55.0`, `alertmanager:v0.27.0`, `grafana:11.3.0`, а также три sidecar-экспортёра (`postgres-exporter:v0.16.0`, `kafka-exporter:v1.8.0`, `node-exporter:v1.8.2`) — production-grade стек наблюдаемости, активируется профилем `monitoring`.

Конфигурация задаётся через файл окружения `.env`, потребляемый docker-compose: переменные `JWT_SECRET` (HS256-ключ ≥ 32 байт), `GIGACHAT_AUTH_KEY` (выпускается в личном кабинете developers.sber.ru), `GIGACHAT_AUTH_SCOPE` (обычно `GIGACHAT_API_PERS`), параметры базы данных `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_HOST`, `POSTGRES_PORT`. Локальные сценарии разработки описаны в корневом `README.md`; типичный полный запуск стенда — `docker compose --profile monitoring up -d`, сборка модулей — `./gradlew build`, локальный bootRun отдельных сервисов — `./gradlew :MainApplication:bootRun`. Фронтенд в режиме разработки запускается командой `cd frontend && npm run dev` на порту 5173 с прокси на `http://localhost:8080`.

## 3.9. Выводы по главе

В главе детально изложена программная реализация всех функциональных подсистем веб-сервиса. Получены следующие практические результаты.

Для модуля sandbox реализован комплекс harden-флагов docker run, watchdog hard-timeout с гарантированным wall-time не более `timeoutSeconds + 2`, корректный порядок разрешения вердиктов (`timedOut → TLE` перед `exitCode == 137 → MLE`) и комбинированный сбор пиковой памяти, устойчивый как к коротким, так и к долгим программам. Поддерживаемые языки — Java 21 и Python 3.11; обоснованно исключён Kotlin.

Для модуля ai-analyzer реализована нейро-символьная цепочка `SimpleRuleBased ← GigaChatAnalyzer ← AstHybridAnalyzer` с явной иерархией авторитетности «sandbox > AST > LLM». Защита от инъекций обеспечивается на четырёх уровнях: системный prompt с анти-инъекционными правилами, sentinel-маркеры пользовательского кода, JSON Schema c `additionalProperties=false`, нормализация и санитизация входа. Защита от галлюцинаций — каскад verdict-guards (`cap=40` при `passed=0`, `cap=60` при partial, `cap=60` при `suspiciousReturnsConstant`). Производительность поддерживается локальным Caffeine-кэшем с многокомпонентным ключом.

Реализован AST-экстрактор для Java (на JavaParser 3.26) и Python (на регулярных выражениях). Сервис `AstMetricsService` гарантированно возвращает результат даже при ошибках парсинга, что делает LLM-пайплайн устойчивым.

Реализован асинхронный конвейер обработки на основе Kafka, разделяющий HTTP-фронтенд (`task-resolver`) и тяжёлую обработку (`worker`). Реализован early-stop при первой неудаче с сохранением показателя `passed/total` для UX.

Реализован SPA-фронтенд на Vite 5 + React 18 + TypeScript 5.3 + MUI v5 с Monaco-редактором, polling-механизмом отслеживания submission и Zustand-стором с автоматическим refresh JWT-токенов.

В фазе подготовки к защите дополнительно реализованы четыре функциональных расширения: фильтр задач по тематике (миграция V8, обновление OpenAPI, чипы на фронтенде), CSV-импорт задач для роли TEACHER (commons-csv, защита от дублей, транзакционность), наблюдаемость на базе Prometheus и Grafana с SLO/SLA-дашбордом из девяти панелей, сквозное тестирование интерфейса на Playwright с десятью сценариями в трёх spec-файлах. Все четыре расширения интегрированы в общий поток развёртывания через docker-compose.

Зафиксированы и обоснованы решения по MVP-границам: исключение Kotlin как пользовательского языка, отложенное подтверждение электронной почты, ограниченный UI для сценарных тестов. Развёртывание системы унифицировано через `docker-compose.yml` с поддержкой профилей и параметризацией через `.env`. Таким образом, программная реализация образует целостную систему, обеспечивающую полный цикл проверки решения от приёма HTTP-запроса до выдачи структурированного AI-разбора, с измеримыми гарантиями безопасности, производительности и устойчивости к атакам.

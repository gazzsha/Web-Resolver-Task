# ГЛАВА 3. ПРОГРАММНАЯ РЕАЛИЗАЦИЯ

В настоящей главе изложена программная реализация веб-сервиса автоматизированной проверки решений задач по программированию. Общая архитектура и декомпозиция на модули спроектированы в разделах 2.1–2.2, модель данных — в разделе 2.4, контракт OpenAPI — в разделе 2.3; настоящая глава описывает фактическое воплощение этих решений в коде. Описание сгруппировано по Gradle-модулям репозитория и отражает фактическое состояние кодовой базы на момент защиты, включая выполненные в фазе подготовки к защите расширения функциональности (фильтр задач по тематике, импорт задач из CSV-файлов, наблюдаемость на базе Prometheus [34] и Grafana, сквозное тестирование пользовательского интерфейса). Все упоминаемые элементы кода сопровождаются ссылками на файлы репозитория в формате `путь/файл.kt:строка`; полные листинги ключевых классов вынесены в Приложение А.

## 3.1. Реализация модуля изолированного исполнения (sandbox)

Архитектура изоляции и модель угроз для этого модуля обоснованы в разделах 2.6 и 2.8 (решение 5); здесь изложена фактическая реализация. Модуль `sandbox` реализует ключевую с точки зрения безопасности подсистему — изолированное выполнение недоверенного пользовательского кода. Центральный класс — `DockerSandboxService` (`sandbox/src/main/kotlin/ru/sandbox/service/DockerSandboxService.kt`), оркестрирующий жизненный цикл одноразовых Docker-контейнеров через прямые вызовы `docker` CLI посредством `ProcessBuilder`.

### 3.1.1. Флаги усиления безопасности контейнера

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

### 3.1.2. Принудительное прерывание по тайм-ауту (watchdog)

Для гарантированного прекращения выполнения зависшего контейнера реализован внешний watchdog на базе `ScheduledExecutorService` (`DockerSandboxService.kt:51–53`, `316–323`). При запуске контейнера планируется отложенная задача через `pollScheduler.schedule(...)` с задержкой `timeoutSeconds * 1000L` миллисекунд; при срабатывании задача устанавливает атомарный флаг `timedOutFlag` и вызывает `killContainer`, отправляющий контейнеру SIGKILL через `docker kill --signal=SIGKILL`.

Поверх watchdog введена страховка через `docker wait` с явным таймаутом `waitFor(timeoutSeconds + 2L, TimeUnit.SECONDS)` (`DockerSandboxService.kt:326–344`). Дополнительные 2 секунды компенсируют возможные задержки `ScheduledExecutorService` под высокой нагрузкой. Если процесс `docker wait` не завершился даже после страховочного интервала, вызывается `destroyForcibly` и контейнеру повторно отправляется SIGKILL. Таким образом, для любого пользовательского кода wall-time контейнера не превосходит `timeoutSeconds + 2` секунд независимо от характера зависания.

### 3.1.3. Определение вердикта исполнения

Метод `resolveVerdict` (`DockerSandboxService.kt:426–443`) формирует канонический вердикт исполнения по сигналам, наблюдаемым со стороны хоста. Порядок приоритета условий критически важен: проверка флага `timedOut` выполняется до проверки `exitCode == 137`, иначе убитые watchdog’ом контейнеры классифицировались бы как `MEMORY_LIMIT_EXCEEDED`. Это связано с тем, что SIGKILL преобразуется ядром Linux в код выхода 137 независимо от причины. Текущая последовательность:

1. `timedOut == true` → `TIME_LIMIT_EXCEEDED`;
2. `oomKilled == true` (флаг из `docker inspect .State.OOMKilled`) → `MEMORY_LIMIT_EXCEEDED`;
3. `exitCode == 137` → `MEMORY_LIMIT_EXCEEDED` (фактический OOM, watchdog не сработал);
4. `exitCode == 124` → `TIME_LIMIT_EXCEEDED` (страховочный путь);
5. `exitCode == 139` → `RUNTIME_ERROR` (SIGSEGV);
6. `exitCode != 0` → `RUNTIME_ERROR`;
7. шаблон stderr указывает на ошибку → `RUNTIME_ERROR`;
8. иначе → `Verdict.OK` (заменяется на `WRONG_ANSWER`/`PRESENTATION_ERROR` методом `compareOutput` в языковых лаунчерах).

### 3.1.4. Измерение пикового потребления памяти

Для измерения пика потребления оперативной памяти при wall-time порядка нескольких сотен миллисекунд реализован комбинированный подход (`DockerSandboxService.kt:361–369`, `wrapForPeakMemoryCapture`), сочетающий чтение cgroup-файла и polling с хоста:

— **Запись из контейнера.** Команда пользователя оборачивается в shell-конструкцию, которая перед завершением читает cgroup v2-файл `/sys/fs/cgroup/memory.peak` и сохраняет значение в `/app/.peak_memory_bytes`. Каталог `/app` смонтирован с хоста, поэтому файл становится доступен после остановки контейнера.
— **Polling с хоста.** Параллельно работает фоновая задача `pollScheduler.scheduleAtFixedRate` (`DockerSandboxService.kt:303–306`), каждые 100 миллисекунд вызывающая `docker stats --no-stream --format "{{.MemUsage}}"` и обновляющая `AtomicLong peakMemoryBytes` через `compareAndSet`-цикл.
— **Финальное значение.** `effectivePeakBytes = maxOf(peakMemoryBytes.get(), cgroupPeakBytes)`. Подход устойчив к обоим режимам отказа: короткие программы получают значение из cgroup-файла, долгие — из polling, при принудительном SIGKILL контейнером без shutdown-hook’а используется накопленный polling-максимум.

### 3.1.5. Поддерживаемые языки

Текущая версия sandbox поддерживает два пользовательских языка: Java на базе образа `eclipse-temurin:21-jdk-alpine` и Python на базе `python:3.11-alpine` (`DockerSandboxService.kt:101–197`). Лаунчер Java записывает исходный файл с именем, извлечённым из `extractClassName`, и запускает командой `javac $className.java && java $className < input.txt`. Лаунчер Python запускает `python solution.py < input.txt`.

Алгоритм sandbox-исполнения, выполненный по нотации блок-схем ГОСТ 19.701-90 [7], показан на рисунке 3.1.

![Рисунок 3.1 – Блок-схема алгоритма sandbox-исполнения (ГОСТ 19.701-90)](../figures/07_sandbox_flowchart.png)

Рисунок 3.1 – Блок-схема алгоритма sandbox-исполнения (ГОСТ 19.701-90)

## 3.2. Реализация модуля интеллектуального анализа (ai-analyzer)

Архитектура анализатора, иерархия авторитетности источников и проектные параметры обоснованы в разделе 2.7; здесь изложена фактическая реализация. Модуль `ai-analyzer` реализует нейро-символьный конвейер анализа решения в виде цепочки обработчиков `AstHybridAnalyzer → GigaChatAnalyzer → SimpleRuleBasedAnalyzer`: внешним фасадом и основным анализатором сервиса является `AstHybridAnalyzer`, делегирующий вызов в `GigaChatAnalyzer`, который при сбое или превышении размера ввода откатывается на резервный `SimpleRuleBasedAnalyzer`. Иерархия авторитетности источников единая по работе: вердикт песочницы → факты AST → суждение модели.

### 3.2.1. Формирование пользовательского запроса к модели

Метод `AnalyzerPrompts.userPromptFull` (`ai-analyzer/src/main/kotlin/ru/aianalyzer/prompt/AnalyzerPrompts.kt:117–163`) формирует пользовательскую часть сообщения для LLM из четырёх блоков, выводимых в строго фиксированном порядке:

1. **Язык программирования.** Идентификатор языка нормализуется фильтром по `isLetterOrDigit() || '+' || '-'`, оставляющим только буквенно-цифровые символы и знаки `+`, `-`.
2. **Условие задачи.** Если `AnalyzeContext.taskDescription` не пуст, добавляется блок «Условие задачи:» с обрезкой до 4000 символов.
3. **Результат проверки sandbox.** При наличии агрегатов теста добавляется блок «Результат проверки sandbox:» с полями «Пройдено тестов: passed из total», «Итоговый verdict» и «Первая ошибка» (обрезка до 500 символов, очистка через `stripUnsafeOutput`).
4. **AST-факты.** Если предоставлен блок `astFactsBlock` (см. раздел 3.3), он встраивается с обёрткой `<AST_FACTS>...</AST_FACTS>`.
5. **Код студента.** Помещается между sentinel-маркерами `<<<STUDENT_CODE_BEGIN>>>` и `<<<STUDENT_CODE_END>>>`. Любое вхождение `<<<STUDENT_CODE_END>>>` внутри кода заменяется на литерал `###STUDENT_CODE_END_LITERAL###`.

Обоснование порядка блоков и роль маркеров приведены в разделе 2.7.3. Системный prompt (`AnalyzerPrompts.kt:24–71`) фиксирует иерархию авторитетности «вердикт песочницы → факты AST → суждение модели» и содержит анти-инъекционные правила (игнорирование инструкций из тела кода, запрет смены роли, запрет раскрытия system-prompt). Поддерживаются два варианта [35] — `ZERO_SHOT` и `FEW_SHOT` (с подмешиванием примеров разборов из `prompts/few-shot-examples.txt`); выбор управляется параметром `PromptVariant`.

Последовательность построения user-prompt из контекста анализа, AST-фактов и кода представлена в виде блок-схемы на рисунке 3.2.

![Рисунок 3.2 – Блок-схема построения user-prompt для ИИ-анализатора](../figures/08_user_prompt_build.png)

Рисунок 3.2 – Блок-схема построения user-prompt для ИИ-анализатора

### 3.2.2. Клиент GigaChat: авторизация OAuth 2.0 и повторные запросы

Класс `GigaChatClient` (`ai-analyzer/src/main/kotlin/ru/aianalyzer/client/GigaChatClient.kt`) реализует низкоуровневый клиент к API GigaChat от ПАО «Сбербанк» (выбор модели обоснован в разделе 2.8, решение 6). Используется JDK `java.net.http.HttpClient`. Аутентификация реализована по схеме OAuth 2.0 client_credentials (`obtainAccessToken`): авторизационный ключ передаётся в заголовке `Authorization: Basic <authKey>`, scope — `GIGACHAT_API_PERS`. Полученный access-token кэшируется в `AtomicReference<CachedToken>` с TTL 30 минут; за 60 секунд до истечения выполняется превентивный refresh.

Метод `chatCompletion` оборачивает запрос в политику повторных попыток: до трёх попыток на 5xx-ответах и transient-ошибках сети (`HttpTimeoutException`, `IOException`) с экспоненциальной задержкой и случайным разбросом задержки (jitter) в диапазоне `±20%`. Базовая задержка `initialBackoff = 500ms`, последующие — `500 * 2^n` мс.

### 3.2.3. Вызов модели и разбор ответа

Метод `GigaChatAnalyzer.callAndParse` (`ai-analyzer/.../service/GigaChatAnalyzer.kt:118–159`) реализует один полный цикл «построение prompt → запрос к LLM → парсинг → валидация». Если переданы `taskContext` или непустой блок AST, для построения user-сообщения используется `userPromptFull`; в противном случае — сокращённый `userPrompt`. Ответ LLM очищается от markdown-обёрток `stripJsonFences`, валидируется по JSON Schema через `schemaValidator.parseAndValidate`, после чего десериализуется в `GigaChatAnalysisPayload`. При несоответствии схеме делается одна повторная попытка с подсказкой в prompt (`userPromptRetry`), при повторном провале — fallback на rule-based анализатор.

Метод `mapPayload` (`GigaChatAnalyzer.kt:161–198`) транслирует ответ LLM во внутреннюю модель `AIAnalysisResult` и применяет verdict-guards. Поле `codeQuality` приводится в диапазон `0..100`, после чего применяется каскад верхних границ (раздел 3.2.6).

### 3.2.4. Проверка ответа модели по JSON-схеме

Класс `SchemaValidator` (`ai-analyzer/.../validation/SchemaValidator.kt`) загружает схему `explanation.json` (`ai-analyzer/src/main/resources/schemas/explanation.json`) по стандарту JSON Schema Draft 7. Схема фиксирует набор обязательных полей ответа LLM, их типы и допустимые диапазоны значений (`codeQuality: integer 0..100`, `issues: array of string ≤ 500 chars`, `recommendations: array`, `explanation: string ≤ 4000 chars`, `complexity: enum LOW|MEDIUM|HIGH|VERY_HIGH`). Свойство `additionalProperties` оставлено равным `true` (модель регулярно дополняет JSON пояснительными ключами вроде `feedback`, `analysis`). Любые ключи сверх описанных в схеме (включая управляющие ключи злоумышленника, такие как `override_quality`, `system_message`, `_admin_note`) отбрасываются при десериализации ответа в строгий объект (Jackson, `@JsonIgnoreProperties`) и не достигают логики вычисления оценки. Отсутствие обязательного поля и out-of-range значения `codeQuality` (например, `codeQuality: 1000`) трактуются как ошибка валидации и приводят к принудительному повтору либо fallback’у.

### 3.2.5. Нормализация и очистка входных данных

Класс `InputSanitizer` (`ai-analyzer/.../sanitize/InputSanitizer.kt`) выполняет три задачи:

— **Нормализация Unicode NFKC** (`normalizeUnicode`) — приведение «совместимых» представлений символов к канонической форме (например, полноширинных `ｉｆ` к `if`);
— **Удаление управляющих, bidi- и zero-width-символов** — фильтрация диапазонов `U+0000..U+001F` (кроме `\n` и `\t`), `U+200B..U+200F` (zero-width), `U+2028..U+202F` (bidi-override), `U+FEFF` (BOM);
— **Ограничение размера** (`enforceSizeLimit`) — превышение порога 16 КБ приводит к выбросу `InputTooLargeException`.

Метод `stripUnsafeOutput` применяется к строковым полям ответа LLM (`explanation`, элементы `issues` и `recommendations`) и вычищает HTML-теги, опасные URL-схемы (`javascript:`, `data:`) и фрагменты, выглядящие как код подмены роли.

Дополнительно реализован метод `enforceImpersonalTone` — regex-нормализатор стиля ответа LLM, удаляющий обращения второго лица («ты», «вам», «тебе»), упоминания «студент» и менторские директивы («необходимо», «следует обратить внимание»). Метод применяется к полям `explanation`, элементам `issues` и `recommendations` совместно со `stripUnsafeOutput`. Введён в коммите `1a962b0` после того, как пилотный прогон обнаружил «менторский тон» в ответах GigaChat, выходящий за рамки безличного академического стиля.

### 3.2.6. Ограничители итоговой оценки

Выбор шкалы и численных границ обоснован в разделе 2.7.5. Реализован каскадный механизм ограничения `codeQuality` поверх ответа LLM в виде динамической шкалы `dynamicQualityCap(passed, total)` (`GigaChatAnalyzer.kt`, коммит `6be1ffd`):

— `total == 0` или `passed == total` → ограничение не накладывается;
— `passed == 0` → `codeQuality` не выше **20**;
— `passed == 1` → не выше **30**;
— `2 · passed ≤ total` (половина или меньше) → не выше **50**;
— `passed == total − 1` (все, кроме одного) → не выше **70**;
— иначе (промежуточный случай) → не выше **70**.

Дополнительно при сочетании `astSuspiciousReturnsConstant == true` (AST-признак «функция возвращает константу») и полного провала тестов применяется экстремальный cap = **10**. Шкала закреплена в чистой функции `dynamicQualityCap(passed, total)` и покрыта параметризованным unit-тестом `GigaChatAnalyzerTest.dynamicQualityCap_table`.

В `GigaChatAnalyzer` предусмотрен флаг `disableVerdictGuards = true`, отключающий каскад целиком; он предназначен для отладки и для воспроизведения «сырого» поведения модели без защитного слоя. В production-конфигурации (`AiAnalyzerConfig`) флаг не выставляется и значение по умолчанию остаётся `false`.

В классе `AstHybridAnalyzer` (`AstHybridAnalyzer.kt:18, 105–114`) предусмотрен также собственный AST cross-check, применяющийся к границам выше `dynamicQualityCap`.

### 3.2.7. Кэширование результатов анализа

В модуль внедрён локальный кэш на базе библиотеки Caffeine (состав ключа и его обоснование — раздел 2.7.6). Ключ кэша строится в методе `cacheKey` (`GigaChatAnalyzer.kt:252–276`) как SHA-256-дайджест от конкатенации: `language` (lowercase) → санитизированный `code` → `promptVariant` → флаг наличия AST-блока → fingerprint списка sandbox-вердиктов → префикс `taskDescription` (до 2048 символов).

## 3.3. Реализация анализатора синтаксического дерева (AST)

Роль AST-фактов в иерархии источников и их обособление в отдельный блок prompt-а обоснованы в разделе 2.7 (пункты 2.7.1, 2.7.7); здесь изложена фактическая реализация. AST-подмодуль предоставляет детерминированные структурные факты о коде, которые передаются модели в составе user-prompt и используются в verdict-guards. Подсистема расположена в `ai-analyzer/src/main/kotlin/ru/aianalyzer/ast/`.

Класс данных `AstFact` (`AstFact.kt`) описывает девять полей: `language`, `hasLoop`, `hasRecursion`, `hasComparison`, `cyclomaticComplexity`, `methodCount`, `maxNestingDepth`, `suspiciousReturnsConstant`, `lineCount`. Метод `toPromptJson` сериализует факт в компактный JSON для встраивания в prompt, а функция расширения `spotlightForPrompt` оборачивает результат в блок `<AST_FACTS>...</AST_FACTS>`. Метод-фабрика `AstFact.empty(language)` возвращает «нулевой» факт; вызывающие используют его при ошибках анализа, чтобы LLM-конвейер никогда не падал из-за дефектов AST-парсера.

`JavaAstAnalyzer` использует библиотеку JavaParser версии 3.26: входной текст парсится в `CompilationUnit`, после чего AST обходится посредником `GenericVisitorAdapter`. Подсчитываются `ForStmt`, `WhileStmt`, `DoStmt`, `ForEachStmt` (детектор циклов), `MethodCallExpr` с совпадающим `MethodDeclaration.name` (детектор рекурсии), бинарные сравнения, узлы решений (для McCabe-сложности), глубина блоков. Признак `suspiciousReturnsConstant` поднимается, когда единственное тело метода-кандидата сводится к `return <literal>`.

`PythonRegexAnalyzer` реализует ту же логику на основе регулярных выражений. Регулярные выражения покрывают определения функций (`def \w+`), циклы (`for `, `while `), сравнения, конструкции `if`/`elif`/`else`. Для обнаружения рекурсии после обхода всех `def`-имен проверяется наличие соответствующих вызовов внутри тела функции.

Диспетчер `AstMetricsService` (`AstMetricsService.kt`) принимает идентификатор языка, обрезает его до 32 символов, приводит к нижнему регистру и направляет вызов в соответствующий язык-специфический анализатор. Любое исключение, возникшее в недрах парсера, перехватывается обёрткой `runSafely` и заменяется на `AstFact.empty(language)`, что гарантирует устойчивость конвейера.

## 3.4. Реализация модулей worker и task-resolver

Поток обработки решения и его этапы спроектированы в разделе 2.5; здесь изложена фактическая реализация. Подсистема обработки решения состоит из двух независимых Spring Boot приложений, взаимодействующих через брокер Kafka.

### 3.4.1. Обмен сообщениями через Kafka

Производитель `TaskClusterProducer` в модуле `task-resolver` использует абстракции библиотеки Spring for Apache Kafka [20] — `org.springframework.kafka.core.KafkaTemplate<String, WorkerTaskMessage>` — для публикации сообщений в топик `task-execution` с `acks=all`. Параметры топика настраиваются через `kafka.config.task-cluster.producer.*` в `application.properties`. Consumer-сторона worker’а представлена `WorkerKafkaListener` с аннотацией `@KafkaListener(topics = "task-execution", groupId = "worker-group", containerFactory = ...)`. Симметричная пара (worker-producer → task-resolver-listener) работает на топике `task-results`.

Помимо двух штатных топиков (`task-execution` и `task-results`) предусмотрен Dead Letter Topic `task-execution.DLT`, обслуживаемый `DefaultErrorHandler` совместно с `DeadLetterPublishingRecoverer` (`worker/.../KafkaWorkerConfig`, коммит F-16/F-17). После трёх неудачных fast-retry «отравленное» сообщение перенаправляется в DLT с сохранением оригинального ключа, значения и заголовков. Мониторинг DLT-потока выполняется отдельной панелью «DLT messages per second (task-execution.DLT)» в Grafana-дашборде Kafka (см. п. 3.6.3).

### 3.4.2. Конвейер обработки в модуле worker

Метод `WorkerService.processTask` (`worker/src/main/kotlin/ru/worker/service/WorkerService.kt:21–128`) выполняет основной алгоритм проверки решения. Последовательность шагов:

1. **Прогон тестов с ранним остановом (early-stop).** Для каждого `testCase` из сообщения вызывается `testEngine.runTest(code, language, testCase)`, который, в свою очередь, обращается к `DockerSandboxService`. При первом результате со статусом, отличным от `PASSED`, устанавливается флаг `stopped`; все последующие тесты не запускаются, а их результаты записываются как `TestStatus.SKIPPED` с пояснением «Пропущено — первый невалидный тест уже прерывает проверку». Это обеспечивает обратную связь студенту за время одного упавшего теста, а не за `N × timeout`, при сохранении показателя `passed/total` в полном объёме задачи.
2. **Прогон сценарных тестов** (если присутствуют) — делегируется в `ScenarioRunner`.
3. **Определение итогового статуса** через `determineTaskStatus`: все тесты пройдены → `SUCCESS`, есть `ERROR` → `FAILED`, есть `PASSED` среди упавших → `PARTIAL_SUCCESS`, иначе → `FAILED`.
4. **Построение AnalyzeContext.** Агрегируются `passedTestsCount`, `totalTestsCount`, первая неудача (для извлечения `overallVerdict` и `firstError`), пробрасывается `taskDescription` из исходного сообщения. Контекст передаётся в `aiAnalyzer.analyze(...)`, где используется при построении prompt и применении verdict-guards.
5. **Сохранение результата.** Через consumer-сторону `task-resolver` (`TaskResultKafkaListener`) сообщение десериализуется и сохраняется как `TaskResultEntity` с привязанным `AIAnalysisEntity` (отношение `@OneToOne` с `FetchType.LAZY` и `cascade=ALL`).

Класс `TaskResolverController` реализует сгенерированный из OpenAPI интерфейс `web.TaskResolverApi`; контрактно-ориентированный подход обоснован в разделе 2.3. Модель данных и схемы запросов/ответов происходят из `api-generator/resources/api/task-resolver-api.yml` и `task-results-api.yml`; TypeScript-клиенты на стороне SPA генерируются из тех же YAML-спецификаций.

### 3.4.3. Сводка мер защиты конвейера анализа

Семиуровневая архитектура защиты от prompt-инъекций обоснована в разделе 2.6.1 (класс нарушителя N₂). Ниже перечислены конкретные реализующие её элементы кода:

1. **Sentinel-маркеры** `<<<STUDENT_CODE_BEGIN>>>` и `<<<STUDENT_CODE_END>>>` — изолируют пользовательский код от системного prompt в одной строке с фиксированной структурой.
2. **Нормализация Unicode NFKC** (`InputSanitizer.normalizeUnicode`) — приведение совместимых представлений к канонической форме; удаление управляющих/bidi/zero-width-символов.
3. **Ограничение длины входа** (`enforceSizeLimit(16 КБ)`) — защита от token-exhaustion и DoS на стороне токенизатора LLM.
4. **JSON-Schema (обязательные поля, типы, диапазоны)** — формальный контракт на ответ модели; отсутствие обязательного поля или выход значения за диапазон приводят к ошибке валидации и retry/fallback, а посторонние ключи (`override_quality`, `system_message`) отбрасываются при десериализации в строгий объект и не влияют на оценку.
5. **`stripUnsafeOutput`** — пост-фильтр строковых полей ответа: HTML-теги, опасные URL-схемы (`javascript:`, `data:`), фрагменты подмены роли.
6. **`enforceImpersonalTone`** — regex-нормализатор стиля ответа: удаление обращений второго лица и менторских директив (коммит `1a962b0`).
7. **Динамический verdict-cap (`dynamicQualityCap`) + AST-clamp** — пост-обработка `codeQuality`, привязанная к sandbox-вердикту и AST-признакам (см. п. 3.2.6).

Эмпирическая эффективность защиты подтверждена результатом 0/40 успешных атак (20 атакующих решений в каждой из двух конфигураций) на заранее зафиксированном эталонном корпусе (см. главу 5).

## 3.5. Реализация фронтенда

Фронтенд — single-page-приложение на стеке Vite 5 + React 18 + TypeScript 5.3 + Material UI v5 + Zustand + `@monaco-editor/react`. Расположение исходников — каталог `frontend/`.

### 3.5.1. Маршрутизация интерфейса

Маршрутизация реализована через `react-router-dom`. Зарегистрированы страницы: `/login` (`Login.tsx`), `/register` (`Register.tsx`), `/` (`Dashboard.tsx`), `/tasks` (`TaskList.tsx`), `/tasks/:id` (`TaskDetail.tsx`), `/tasks/:id/submit` (`Submission.tsx`), `/submissions` (`Submissions.tsx`), `/statistics` (`Statistics.tsx`), `/results/:id` (`Results.tsx`) и добавленная при подготовке к защите `/admin/import` (`AdminImport.tsx`, доступна только пользователям с ролью `TEACHER`). Защищённые маршруты обёрнуты в компонент-обёртку (HOC, higher-order component — компонент, оборачивающий другой компонент и добавляющий ему поведение) `RequireAuth`, перенаправляющий неавторизованных пользователей на `/login` с сохранением `next`-параметра.

### 3.5.2. Редактор кода Monaco

Компонент `EditorPane` (`frontend/src/components/submission/EditorPane.tsx`) инстанцирует `@monaco-editor/react`. Атрибут `language` динамически переключается между `java` и `python` в зависимости от выбора в селекторе. Активированы live-completions (`quickSuggestions: true`), подсветка синтаксиса, автоматическое сворачивание, минимальный размер `minimap.enabled=false`. Установлен тёмный шаблон `vs-dark`. Ввод языка ограничен набором {Java, Python}, что соответствует возможностям sandbox (раздел 3.1.5).

### 3.5.3. Опрос статуса проверки решений

Страница результатов (`Results.tsx`) ожидает асинхронного завершения проверки. Применяется опрос API: `setInterval(() => fetchResult(id), 2000)`. Опрос останавливается при попадании `status` в множество терминальных значений `{SUCCESS, FAILED, ERROR, PARTIAL_SUCCESS}`, после чего рендерится финальный экран с разбивкой по тестам и результатом ИИ-анализа. Дополнительно реализован максимальный таймаут опроса (3 минуты), по истечении которого выводится сообщение об ошибке.

### 3.5.4. Хранилище состояния и перехватчики HTTP-запросов

Состояние аутентификации хранится в Zustand store (`frontend/src/store/auth.ts`): поля `accessToken`, `refreshToken`, `user`. Сохранение в `localStorage` через middleware `persist`. Axios-инстанс (`frontend/src/services/api.ts`) сконфигурирован с `baseURL` из переменной окружения `VITE_API_BASE_URL` и двумя перехватчиками: request-interceptor добавляет `Authorization: Bearer <accessToken>`, response-interceptor при HTTP 401 пробует обновить токен через `POST /auth/refresh` и повторяет исходный запрос; при повторном 401 — принудительный logout и редирект на `/login`.

### 3.5.5. Кнопка копирования тестовых данных

Компонент `CopyButton` (`frontend/src/components/common/CopyButton.tsx`, коммит `eb269be`) реализует копирование произвольной строки в системный буфер обмена через `navigator.clipboard.writeText`. Применяется на странице задачи (`TaskDetail.tsx`) для копирования примеров входных и выходных данных и на странице результатов (`Results.tsx`) для копирования собственного ранее отправленного кода. Компонент использует MUI `IconButton` с переключающейся иконкой `ContentCopy` / `Check` и Snackbar-уведомлением «Скопировано» с тайм-аутом 1,5 с. Учитывает ограничение `navigator.clipboard` в небезопасных контекстах (HTTP без `localhost`): при ошибке записи показывается сообщение «Не удалось скопировать».

## 3.6. Расширения функциональности перед защитой

В дополнение к функционалу, описанному в разделах 3.1–3.5, в фазе подготовки к защите реализованы четыре крупных расширения, востребованных в учебном процессе кафедры. Перечень и обоснование выбора этих фич восходит к функциональным требованиям, сформулированным в НИР 2025 года, но не материализованным в коде на момент промежуточной отчётности.

### 3.6.1. Фильтр задач по тематике

**Уровень БД.** Миграция Flyway `V8__add_category.sql` (`db/src/main/resources/db/migration/V8__add_category.sql`) добавляет колонку `category VARCHAR(64)` к таблице `test` инструкцией `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`, создаёт частичный индекс `idx_test_category` для ускорения фильтрации и выполняет дозаполнение (backfill) категорий для ранее загруженных задач из миграций V4 и V5. Классификация соответствует таксономии LeetCode/Codeforces: «Array», «Stack», «Math», «String», «LinkedList», «SlidingWindow» и собирательная категория «General» для задач без явной классификации.

**Уровень JPA.** Сущность `Test` (`db/src/main/kotlin/ru/db/entity/Test.kt`) расширена полем `var category: String?` с аннотацией `@Column(name = "category", length = 64)`. В репозитории `TestRepository` добавлен JPQL-запрос `searchByFilters(category: String?, difficulty: Difficulty?)` с условиями вида `(:category IS NULL OR t.category = :category) AND (:difficulty IS NULL OR t.difficulty = :difficulty)`, что позволяет применять нулевые фильтры без динамической генерации SQL. Дополнительно реализован запрос `findDistinctCategories()`, возвращающий упорядоченный список ненулевых категорий для построения чипов на фронтенде.

**Уровень OpenAPI.** Спецификация `api-generator/resources/api/task-results-api.yml` дополнена параметрами запроса `?category=` и `?difficulty=` для `GET /api/v1/tasks` и новым ресурсом `GET /api/v1/tasks/categories`, возвращающим массив строк.

**Уровень фронтенда.** Страница `TaskList.tsx` (`frontend/src/pages/TaskList.tsx`) отрисовывает над таблицей задач полосу чипов MUI `<Chip variant="outlined" />`. При монтировании страница вызывает `taskService.getCategories()` для предварительной загрузки набора, после чего применяется клиентская фильтрация по уже загруженному списку задач; серверная фильтрация задействуется только при первой загрузке списка. Выбор схемы фильтрации обоснован в разделе 2.8 (решение 10).

### 3.6.2. Импорт задач из CSV-файлов

Функция предоставляет преподавателям возможность массовой загрузки задач из CSV-файла без необходимости ручного создания каждой задачи через UI.

**Endpoint.** Добавлен `POST /api/v1/admin/tasks/import` (multipart/form-data), описанный в `api-generator/resources/api/task-results-api.yml`. Контроллер `TasksController` в модуле `task-resolver` принимает `MultipartFile`, делегирует обработку в `TaskImportService` и возвращает DTO `TaskImportResult { importedCount, skippedCount, errors: [{line, message}] }`. Доступ ограничен аннотацией `@PreAuthorize("hasRole('TEACHER')")`, что включается за счёт `@EnableMethodSecurity` в `SecurityConfig`.

**Сервис.** Класс `TaskImportService` (`task-resolver/src/main/kotlin/ru/taskresolver/service/TaskImportService.kt`) использует библиотеку `commons-csv 1.12.0`. Ожидаемый формат заголовков:
`title,difficulty,category,description,return_type,arguments_json,tests_json`. Парсинг реализован через `CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true)`. Сложные структуры (`arguments_json`, `tests_json`) десериализуются Jackson’ом по `TypeReference`. Защита от дублей — case-insensitive сравнение `title`: предзагружаются все существующие title в `Set<String>` (с приведением к нижнему регистру), при коллизии строка пропускается со счётчиком `skippedCount`. Метод аннотирован `@Transactional`: при необработанной ошибке транзакция откатывается, при штатных ошибках строки (например, некорректный JSON в `tests_json`) сообщение записывается в `errors`, остальные строки обрабатываются.

**Фронтенд.** Страница `AdminImport.tsx` (`frontend/src/pages/AdminImport.tsx`) содержит file-input для CSV, кнопку скачивания CSV-шаблона (генерируется на клиенте, blob-URL) и панель с результатом импорта (статистика + список ошибок). В сайдбаре навигации (`frontend/src/components/layout/Sidebar.tsx`) пункт «Импорт задач» отображается только для пользователей с `role === 'TEACHER'`, что обеспечивается атрибутом `requireRole: 'TEACHER'` в декларации маршрутов.

### 3.6.3. Наблюдаемость: Prometheus, Grafana и показатели уровня обслуживания

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
2. Basic-auth с in-memory пользователем `prometheus-scraper` (роль `OPS`), пароль читается из `${PROMETHEUS_SCRAPER_PASSWORD}`.

Логика собрана в `AuthorizationManager<RequestAuthorizationContext>`, который последовательно применяет оба правила: запрос проходит только при одновременном выполнении обоих условий. Prometheus-контейнер читает пароль из bind-mount файла `secrets/scraper_password` (каталог `secrets/` в `.gitignore`). Подход устраняет фактический риск утечки внутренней структуры (имена бинов, GC-параметры, JVM-стек) при компрометации сетевого периметра.

**Инфраструктура.** В `docker-compose.yml` под профилем `monitoring` подняты шесть сервисов:

— `prometheus:v2.55.0` с конфигурацией `monitoring/prometheus.yml`, смонтированной директорией `monitoring/rules/` (rule files) и каталогом `secrets/` (пароль scraper'а), порт 9090;
— `alertmanager:v0.27.0` с конфигом `monitoring/alertmanager.yml` (минимальный dev-receiver `null`; для production заготовлены закомментированные блоки slack/telegram), порт 9093;
— `grafana:11.3.0` с автоматическим провижионингом datasource `prometheus` и шести дашбордов, порт **3001** (порт 3000 оставлен под Vite dev-сервер фронтенда; изменение зафиксировано в коммите `f6842d0`);
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

**SLO-targets и burn-rate alert rules.** Файл `monitoring/rules/web-resolver.rules.yml` содержит восемь групп и 40 правил (recording + alert), валидируется командой `promtool check rules`. Стратегия алертинга — multi-window multi-burn-rate (Google SRE Workbook), то есть по скорости расходования «бюджета ошибок» (допустимой доли сбоев за период) в нескольких временных окнах с разными порогами: для каждого ratio-SLI создаётся три правила с разными окнами и порогами скорости расходования бюджета (burn-rate). Для SLO HTTP error rate (≤ 1%, бюджет 30 дней):

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

![Рисунок 3.3 – Диаграмма состояний жизненного цикла submission](../figures/09_submission_state.png)

Рисунок 3.3 – Диаграмма состояний жизненного цикла submission
## 3.7. Развёртывание

Развёртывание системы реализовано через единый файл `docker-compose.yml` в корне репозитория. Поднимаются следующие сервисы:

— `postgres:16-alpine` — основная реляционная СУБД, том `/var/lib/postgresql/data`, порт 5432;
— `confluentinc/cp-zookeeper:7.5.0` и `confluentinc/cp-kafka:7.5.0` — координационный сервис и брокер сообщений;
— `main-application` (собираемый локально из `MainApplication/Dockerfile`) — основной HTTP-API на порту 8080;
— `worker` (`worker/Dockerfile`) — отдельный экземпляр для асинхронной обработки;
— `prometheus:v2.55.0`, `alertmanager:v0.27.0`, `grafana:11.3.0`, а также три sidecar-экспортёра (`postgres-exporter:v0.16.0`, `kafka-exporter:v1.8.0`, `node-exporter:v1.8.2`) — production-grade стек наблюдаемости, активируется профилем `monitoring`.

Конфигурация задаётся через файл окружения `.env`, потребляемый docker-compose: переменные `JWT_SECRET` (HS256-ключ ≥ 32 байт), `GIGACHAT_AUTH_KEY` (выпускается в личном кабинете developers.sber.ru), `GIGACHAT_AUTH_SCOPE` (обычно `GIGACHAT_API_PERS`), параметры базы данных `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_HOST`, `POSTGRES_PORT`. Локальные сценарии разработки описаны в корневом `README.md`; типичный полный запуск стенда — `docker compose --profile monitoring up -d`, сборка модулей — `./gradlew build`, локальный bootRun отдельных сервисов — `./gradlew :MainApplication:bootRun`. Фронтенд в режиме разработки запускается командой `cd frontend && npm run dev` на порту 5173 с прокси на `http://localhost:8080`.

## 3.8. Выводы по главе

В главе детально изложена программная реализация всех функциональных подсистем веб-сервиса. Получены следующие практические результаты.

Для модуля sandbox реализован комплекс hardening-флагов docker run, watchdog hard-timeout с гарантированным wall-time не более `timeoutSeconds + 2`, корректный порядок разрешения вердиктов (`timedOut → TLE` перед `exitCode == 137 → MLE`) и комбинированный сбор пиковой памяти, устойчивый как к коротким, так и к долгим программам. Поддерживаемые языки — Java 21 и Python 3.11.

Для модуля ai-analyzer реализована нейро-символьная цепочка `AstHybridAnalyzer → GigaChatAnalyzer → SimpleRuleBasedAnalyzer`, где основным анализатором сервиса является `AstHybridAnalyzer`, с явной иерархией авторитетности «вердикт песочницы → факты AST → суждение модели». Защита от инъекций сведена в семиуровневой архитектуре (см. п. 3.4.3): sentinel-маркеры, нормализация NFKC, ограничение длины входа, валидация по JSON-Schema (обязательные поля, типы, диапазоны) с отбрасыванием посторонних ключей при разборе, `stripUnsafeOutput`, `enforceImpersonalTone`, динамический verdict-cap + AST-clamp. Защита от галлюцинаций — динамическая шкала `dynamicQualityCap` (0/N → 20, 1/N → 30, ≤ N/2 → 50, N−1/N → 70, all-failed + AST-stub → 10). Производительность поддерживается локальным Caffeine-кэшем с многокомпонентным ключом.

Реализован AST-экстрактор для Java (на JavaParser 3.26) и Python (на регулярных выражениях). Сервис `AstMetricsService` гарантированно возвращает результат даже при ошибках парсинга, что делает LLM-конвейер устойчивым.

Реализован асинхронный конвейер обработки на основе Kafka, разделяющий HTTP-фронтенд (`task-resolver`) и тяжёлую обработку (`worker`). Реализован early-stop при первой неудаче с сохранением показателя `passed/total` для UX.

Реализован SPA-фронтенд на Vite 5 + React 18 + TypeScript 5.3 + MUI v5 с Monaco-редактором, polling-механизмом отслеживания submission и Zustand-стором с автоматическим refresh JWT-токенов.

В фазе подготовки к защите дополнительно реализованы четыре функциональных расширения: фильтр задач по тематике (миграция V8, обновление OpenAPI, чипы на фронтенде), CSV-импорт задач для роли TEACHER (commons-csv, защита от дублей, транзакционность), наблюдаемость на базе Prometheus и Grafana с SLO/SLA-дашбордом из девяти панелей, сквозное тестирование интерфейса на Playwright с десятью сценариями в трёх spec-файлах. Все четыре расширения интегрированы в общий поток развёртывания через docker-compose.

Зафиксированы и обоснованы проектные решения по объёму прототипа: поддержка двух языков Java и Python, отложенное подтверждение электронной почты, реализация сценарных тестов как псевдо-сценариев (по одному запуску контейнера на шаг), LLM-варианты `explainErrorWithLlm` и `assessCodeQualityWithLlm` как опциональный код-путь под feature-flag. Развёртывание системы унифицировано через `docker-compose.yml` с поддержкой профилей и параметризацией через `.env`. Таким образом, программная реализация образует целостную систему, обеспечивающую полный цикл проверки решения от приёма HTTP-запроса до выдачи структурированного ИИ-разбора, с измеримыми гарантиями безопасности, производительности и устойчивости к атакам.

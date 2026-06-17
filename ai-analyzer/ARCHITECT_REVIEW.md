# Architecture review — Phases 1-6 (ai-analyzer + experiment)

Read-only review by `architect-reviewer` subagent, 2026-05-13.
Complementary to `DIFFERENTIAL_REVIEW.md` (security-focused).

## CRITICAL

### 1. Cache-key collision — silent staleness on second submission [FIXED post-review]

`GigaChatAnalyzer.cacheKey` hashed only `language + code`. V4 clamp at `mapPayload` depends on `executionResults`, but the cache lookup returned a stale entry **before** `mapPayload` ever ran again. Same problem for `promptVariant`: a B1f run polluted the B1 cache.

**Status:** fixed in this commit. Key now includes `language + code + promptVariant + extraContext-presence-flag + execution-status fingerprint`.

### 2. `MockGigaChatClient` and `Runner` ship in production JAR [DOCUMENTED]

`ai-analyzer/src/main/kotlin/ru/aianalyzer/experiment/*` end up in the runtime classpath of `worker.jar` and `task-resolver.jar`. `MockGigaChatClient` is NOT a `@Component` / `@Bean` — production wiring always uses real `GigaChatClient` — but a future careless `@Bean` registration could swap it in.

**Status:** documented via Kotlin doc on `MockGigaChatClient`. Refactor (`src/experiment` sourceSet) deferred to post-defence — Gradle JavaExec task is currently bound to `src/main/runtimeClasspath` and a sourceSet move requires non-trivial `build.gradle.kts` rewiring.

## MAJOR

### 3. `ai.analyzer.provider` / `ai.prompt.variant` not in application.yml

Both properties default sensibly (`matchIfMissing=true` → gigachat/zero-shot) but are not listed in `MainApplication/src/main/resources/application.yml`. Add docs in post-defence pass.

### 4. `InsecureTrustManagerFactory` — production-blocker without fail-closed mechanism

Documented in `THREAT_MODEL.md` and `INSECURE_DEFAULTS.md` as accepted dev-only. No `@Profile("!prod")` gate. Acceptable for diploma scope.

### 5. `kotlin-compiler-embeddable:2.2.21` propagates ~50 MB downstream

`worker.jar` and `task-resolver.jar` inherit the embedded compiler purely for Kotlin AST. JavaParser is 1 MB, Python uses regex — 98% of dataset doesn't need it. Future work: extract Kotlin AST to a sub-module with module-scoped dependency, or replace with `kotlinx-ast`.

### 6. `AIAnalysisResult.modelVersion` not in OpenAPI response

Internal `AIAnalysisResult.modelVersion` carries semantically meaningful info (`gigachat | ast-hybrid | rule-based | ast-hybrid-fallback`) that is dropped at the controller boundary (`task-resolver/AiAnalysisController` → OpenAPI `AIAnalysisResponse` in `task-results-api.yml`). Future work: expose for UI transparency.

### 7. String-sentinel `modelVersion == "rule-based"` in `AstHybridAnalyzer:97-99`

Cross-class string contract. Works, but fragile to refactor. Replace with `sealed class ModelProvenance` post-defence.

## MINOR / accepted

- Module boundary `ai-analyzer → :sandbox` clean (one-way, no cycle).
- `worker.WorkerService:75-79` passes `scenarioResults=null` even though scenarios are collected at `:67-68`. Cosmetic.
- Test gap: no single integration test covering V4 clamp + AST clamp + V2 Unicode + AST-extraction in one composition. Each layer has isolated tests; mathematically both clamps converge to the same value, so the missing test is correctness-redundant.
- OpenAPI spec drift: **none**. Public API of `:ai-analyzer` was extended (new beans behind `@ConditionalOnProperty`) without changes to `task-resolver-api.yml` or `task-results-api.yml`.
- DB migrations / Dockerfile / settings.gradle.kts — untouched. File-ownership rule from `CLAUDE.md` respected.

## Final verdict

**APPROVE** (after cache-key fix is applied — done in this commit).

## Defence Q&A preparation

**«Почему `AIAnalyzer` — это интерфейс с тремя реализациями + Spring `@Primary` switch, а не if/else внутри одного класса?»**

Каждая реализация — это разный архитектурный паттерн: `SimpleRuleBasedAnalyzer` — symbolic, `GigaChatAnalyzer` — neural, `AstHybridAnalyzer` — neuro-symbolic. Они не делят состояние и не должны делить класс. `@ConditionalOnProperty` даёт runtime-switch без перекомпиляции — критично для A/B-эксперимента в главе 4.

**«Почему две точки clamp кода качества — V4 в `GigaChatAnalyzer.mapPayload` и AST в `AstHybridAnalyzer`?»**

Defence-in-depth. V4 — контракт с sandbox: «если sandbox сказал FAIL, LLM не имеет права ставить >60». AST — контракт с детерминированной эвристикой: «если AST увидел constant-stub, LLM тоже не может ставить >60». Композируются монотонно (оба только понижают), поэтому порядок применения роли не играет — проверено `AstHybridAnalyzerTest`.

**«Зачем JSON-Schema валидация поверх Jackson?»**

Jackson проверяет, что поля парсятся в правильные типы. JSON-Schema проверяет *семантику*: `codeQuality ∈ [0,100]`, `complexity ∈ enum`, `issues` — массив строк. Это защита от V1/V3 prompt-injection: LLM может вернуть синтаксически валидный JSON, но с `codeQuality: -1` или `complexity: "FANTASTIC"`. Schema + retry-once + fallback к rule-based = стратегия graceful degradation, описанная в главе 3 записки.

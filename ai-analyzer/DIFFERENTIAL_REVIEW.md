# Differential security review — feature branch (Phase 1–6)

- **Scope:** commits `8b272d7..11a867b` (7 commits, ai-analyzer/ + experiment/).
- **Strategy:** FOCUSED — module is medium-sized but isolated, no cross-module security regressions.
- **Date:** 2026-05-13.

## Files in scope

```
26 source files (Kotlin) + 4 resources + 5 markdown + 2 build.gradle.kts entries
+ 200+ JSON dataset items + Python generator.
```

## Risk classification

| Risk | Files | Why |
|------|-------|-----|
| HIGH | InputSanitizer.kt, GigaChatAnalyzer.kt, AstHybridAnalyzer.kt, MockGigaChatClient.kt, AiAnalyzerConfig.kt | Sanitization, LLM call path, mock client shipped in main jar, TLS configuration |
| MEDIUM | SchemaValidator.kt, AnalyzerPrompts.kt, JavaAstAnalyzer.kt, KotlinAstAnalyzer.kt, PythonRegexAnalyzer.kt | Validation, untrusted-input parsers |
| LOW | Tests, datasets, markdown, JSON resources | No production execution path |

## Findings

### CRITICAL — none

No criticals: existing dev-only TLS hack (`InsecureTrustManagerFactory` at `AiAnalyzerConfig.kt:43`) was already present BEFORE this branch and is explicitly documented in `THREAT_MODEL.md` + `.semgrep/INSECURE_DEFAULTS.md` as a pre-prod blocker.

### MAJOR-1 — Mock LLM client ships in production jar

- **Location:** `ai-analyzer/src/main/kotlin/ru/aianalyzer/experiment/MockGigaChatClient.kt`
- **Issue:** `MockGigaChatClient extends GigaChatClient`. Lives in `src/main`, therefore ends up in the production jar of `worker` and `task-resolver` (both depend on `:ai-analyzer`).
- **Attack scenario:** an operator with config access could swap the primary `GigaChatClient` bean to `MockGigaChatClient` via XML/JavaConfig overrides and serve canned LLM responses (e.g. always `codeQuality=95`). With AST clamp still in place the worst-case is bounded (sandbox-failed submissions still cap at 60), but explanation strings would be controlled.
- **Mitigation options:**
  1. Move `experiment/` package to `src/test` and re-wire `runExperiment` JavaExec task with `testClasspath` (preferred — keeps production surface minimal).
  2. Or carve a separate Gradle sourceSet `experimentMain` (more ceremonial).
- **Severity for diploma defence:** acceptable; defence environment is a personal laptop. Document this in the «known limitations» section of the explanatory note.

### MAJOR-2 — Cache key omits executionResults and promptVariant

- **Location:** `GigaChatAnalyzer.kt:160-166` (`cacheKey` function)
- **Issue:** Hash = `SHA256(language || 0x00 || sanitized_code)`. Two requests with identical code but different sandbox verdicts share the cache entry. Phase 4 added `promptVariant` to the analyzer, but it is also missing from the key — so a result computed under `ZERO_SHOT` is returned under `FEW_SHOT`.
- **Why this matters for the experiment:** B1 vs B1f comparison in the experiment runner becomes invalid if cache is warm — `b1f` will read `b1`'s entry. The runner currently builds a fresh cache per variant, so today the bug is dormant, but as soon as the cache is shared (e.g. across worker JVMs) results diverge.
- **Fix:** include `promptVariant.name` and a digest of executionResults statuses in the hash. Estimated effort: 10 minutes.

### MAJOR-3 — kotlin-compiler-embeddable bloat propagates downstream

- **Location:** `ai-analyzer/build.gradle.kts:14` (added by Phase 3)
- **Issue:** ~80 MB dependency. Since `worker` and `task-resolver` depend on `:ai-analyzer`, both produced jars now carry it. For local docker-compose deployment this only matters for image size; for any CI/CD or remote deploy this becomes a deployment concern.
- **Verdict:** acceptable for diploma scope (no CI/CD per CLAUDE.md). Pre-prod migration should evaluate switching the Kotlin parser to a lighter alternative (kotlinx.kotlin-cli? psi-light fork?) or extracting AST analysis into a separate microservice.

### MINOR — composition of clamps

- **Files:** `GigaChatAnalyzer.kt:124-150` (V4 clamp at 60 on sandbox fail) + `AstHybridAnalyzer.kt:87-95` (AST suspicious-stub clamp at 60). Composition: V4 fires inside `GigaChatAnalyzer.mapPayload` before `AstHybridAnalyzer` post-processes the result. The AST clamp only fires when `executionResults` are SUCCESS (otherwise V4 already capped below 60). Behaviour is correct; the trace was verified by the live runner log («clamping codeQuality 65 → 60») during the Phase 5 dataset pass.

### MINOR — `AstFact.empty()` language field non-trivial

- `AstMetricsService.kt:30-43` now caps `language` at 32 chars (closed C3 finding MAJOR-3 in Phase 3). Tested. Acceptable.

### MINOR — Python AST is regex-based

- `PythonRegexAnalyzer.kt` uses heuristics. Documented in PROMPT_DESIGN.md / dataset SCHEMA.md. Cyclomatic complexity may be off-by-one on edge cases (verified by `python - cyclomatic complexity counts if elif and boolean operators` test using `>=` rather than exact match). Acceptable for diploma — production system would use `libcst` or `ast` module via Python sidecar.

## Test coverage assessment

- 64 unit tests (clean rebuild). Coverage by class (visual estimate from test files):
  - InputSanitizer: ~95% (17 tests)
  - GigaChatAnalyzer: ~85% (12 tests: V4, V5, retry, schema, cache)
  - AstHybridAnalyzer: ~85% (6 tests)
  - AstMetricsService: ~75% (15 tests, but Python/Kotlin paths have only smoke coverage)
  - SchemaValidator: indirect coverage via GigaChatAnalyzerTest (no dedicated test class — gap)
  - MetricsAggregator: ~70% (2 tests, percentages + empty)
  - MockGigaChatClient: 0% (tested only indirectly via runner smoke)
- **Coverage gap:** no integration test that runs Runner.main() end-to-end with assertions on output JSON. Mitigation: live smoke run on the 195-item dataset (logged in commit message of `11a867b`) demonstrated correct behaviour.

## Blast radius

- `ai-analyzer` is consumed by `worker` and `task-resolver`. Changes did NOT modify public interfaces (`AIAnalyzer.analyze` signature unchanged), only added new beans behind `@ConditionalOnProperty`. Existing consumers (`WorkerService.aiAnalyzer: AIAnalyzer`, `AiAnalysisController.aiAnalyzer: AIAnalyzer`) work unchanged.
- **Drift risk:** if a new property like `ai.prompt.variant=few-shot` is set in production but the relevant bean configuration goes stale — no test currently asserts that `gigaChatAnalyzer` bean picks up the property post-startup. Low risk for diploma since wiring is verified by `ProviderSwitchTest`.

## Adversarial scenarios

For each of the 6 prompt-injection vectors V1-V6 from THREAT_MODEL.md, the experiment runner (195 items × 3 variants = 585 analyse calls) produced `injection_success_rate = 0.00`. Mocked LLM occasionally returned `codeQuality=95` to model a «succumbed» LLM; in every such case the V4 clamp brought it down to 60 (verified in `experiment/results/b*/v*.json`).

The architect-reviewer agent (running in parallel) provides complementary view on module boundaries and contract drift.

## Verdict

**APPROVE WITH CAVEATS** — branch is safe to keep on `feature` and use for diploma defence. The two MAJOR findings (mock-in-main, cache-key) are tracked for post-defence remediation. For the defence presentation, explicitly mention these as «known limitations, planned post-MVP» — this is honest and answers the inevitable «what would you fix next» question.

## Recommended follow-ups (post-defence)

1. Move `experiment/` and `MockGigaChatClient` to `src/test` or a dedicated sourceSet.
2. Extend cache key with `promptVariant.name` and `executionResults` status digest.
3. Replace `InsecureTrustManagerFactory` with proper trust store for production.
4. Replace `kotlin-compiler-embeddable` with a lighter parser or extract AST to a sidecar.

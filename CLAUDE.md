# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

Kotlin 2.2.21 on JDK 21, Spring Boot 3.5.0, Gradle (Kotlin DSL) multi-module build. Persistence via Spring Data JPA on PostgreSQL, messaging via Spring Kafka, web layer via `spring-boot-starter-webmvc`. Frontend: Vite + React 18 + TypeScript + MUI v5 + Zustand + `@monaco-editor/react`. Dependency versions for `api-generator` are pinned in [gradle/catalog-version/libs.versions.toml](gradle/catalog-version/libs.versions.toml); `task-resolver` declares its Spring Boot deps directly without using the catalog.

## Module layout

10 Gradle subprojects, declared in [settings.gradle.kts](settings.gradle.kts):

- **`api-generator`** — Not a runtime artifact. Runs the `org.openapi.generator` plugin to convert OpenAPI YAML specs in [api-generator/resources/api/](api-generator/resources/api/) into Spring interfaces + DTOs at `api-generator/build/generated/openapi/`. The generated dir is added to `sourceSets.main.java.srcDirs`, so generated code is compiled as part of this module and exported to dependents.
- **`task-resolver`** — Spring Boot application implementing the generated API. Depends on `:api-generator` (e.g. [TaskResolverController](task-resolver/src/main/kotlin/ru/taskresolver/web/TaskResolverController.kt) implements `web.TaskResolverApi`).
- **`MainApplication`** — main entrypoint.
- **`common`** — shared code across modules.
- **`db`** — persistence layer (entities, repositories, migrations).
- **`sandbox`** — **isolated execution of user-submitted code** (Docker-in-Docker / nsjail). Critical security boundary.
- **`worker`** — background job processing.
- **`ai-analyzer`** — **integration with external LLM APIs** (Claude / OpenAI / Yandex GPT) for AI-driven solution evaluation.
- **`scenario-runner`** — runs test scenarios against submitted solutions.
- **`task-process`** — orchestrator of the per-task processing pipeline.

`task-resolver` does **not** define its own controllers from scratch — controllers must implement the interface produced by the generator. Adding or changing an endpoint means editing the YAML spec first, then implementing the regenerated interface.

## OpenAPI generation

Configured in [api-generator/build.gradle.kts](api-generator/build.gradle.kts):

- Specs are registered via the `openApiFilesGroupByProcess` map. Each entry creates a Gradle task `generateApi<Process>` (currently only `generateApiTaskResolver` for `task-resolver-api.yml`).
- `tasks.compileKotlin` dependsOn all generator tasks, so a normal build regenerates sources automatically.
- Package conventions baked into the generator config (do not import from elsewhere expecting different names):
  - `apiPackage = "web"` → generated interfaces like `web.TaskResolverApi`
  - `modelPackage = "model"` → generated DTOs like `model.StartTaskSuccessResponse`
  - `invokerPackage = "ru"`
- `interfaceOnly = true`, `delegatePattern = false`, `skipDefaultInterface = true` — controllers must provide concrete implementations of every operation; there are no default method bodies to fall back on.
- Specs cross-reference [common-models.yml](api-generator/resources/api/common-models.yml) (shared `ResultResponse`, `Id`, `ErrorResponse`, `basicAuth` security scheme). Add shared schemas there rather than duplicating per-spec.

To register a new API surface: drop the YAML in `api-generator/resources/api/`, add an entry to `openApiFilesGroupByProcess` keyed by filename with a unique process name, then `./gradlew :api-generator:compileKotlin` to generate.

## Commands

Build / run from repo root using the wrapper:

- `./gradlew build` — full build (regenerates OpenAPI sources, compiles, runs tests)
- `./gradlew :task-resolver:bootRun` — start the Spring Boot app
- `./gradlew :task-resolver:test` — run task-resolver tests (JUnit 5 via `useJUnitPlatform`)
- `./gradlew :task-resolver:test --tests "ru.taskresolver.TaskResolverApplicationTests.contextLoads"` — run a single test
- `./gradlew :api-generator:generateApiTaskResolver` — force-regenerate API sources for one spec without a full build
- `./gradlew clean` — wipe `build/`, including generated OpenAPI sources
- Frontend: `cd frontend && npm run dev` / `npm run build` / `npm run lint`

## Kotlin / JPA specifics

`task-resolver` applies `kotlin("plugin.jpa")` and `kotlin("plugin.spring")` and configures `allOpen` for `@Entity`, `@MappedSuperclass`, and `@Embeddable` so JPA proxying works without manually marking classes `open`. Compiler args include `-Xjsr305=strict` and `-Xannotation-default-target=param-property`. When adding entities, prefer constructor-property style and rely on `allOpen` rather than manually opening classes.

## Notes

- `application.properties` currently sets only `spring.application.name=task-resolver`; datasource/Kafka config is not yet wired, so `bootRun` will fail until those are added (or until a profile providing them is activated).
- The repo includes both `HELP.md` (Spring Initializr boilerplate) and this file — prefer this file as the source of truth.
- **No CI/CD** is in scope for this thesis. Don't propose GitHub Actions / pipelines unless explicitly asked.

---

# Agent-team & subagents protocol

This repo has a curated set of subagents in [.claude/agents/](.claude/agents/) and skills in [.claude/skills/](.claude/skills/). Use them via the standard subagent invocation or as teammates in agent-teams. Agent-teams are enabled in `~/.claude/settings.json` (`CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1`, `teammateMode=auto`).

## Available subagents (15)

Backend: `kotlin-specialist`, `spring-boot-engineer`, `api-designer`, `postgres-pro`.
Frontend: `react-specialist`, `typescript-pro`.
Infra: `docker-expert`.
Quality & security: `security-auditor` (critical for sandbox), `code-reviewer`, `architect-reviewer`, `test-automator`, `debugger`.
AI / LLM: `prompt-engineer`, `ai-engineer`.
Orchestration: `multi-agent-coordinator`.

## Available skills (6 local + Anthropic built-ins)

Local (in `.claude/skills/`): `semgrep`, `insecure-defaults`, `differential-review` (Trail of Bits — for sandbox & security), `spring-boot`, `jpa-patterns`, `logging-patterns` (piomin — Spring Boot 3 idioms).
Built-in via plugins: `anthropic-skills:docx`, `pptx`, `pdf`, `xlsx` (диплом — пояснительная записка и презентация), `claude-api` (LLM-интеграция), `mcp-builder`, `webapp-testing`, `skill-creator`.

## When to spawn an agent-team vs solo work

**Spawn a team** for:
- Cross-layer work touching backend + frontend + sandbox simultaneously
- Parallel code review (security / architecture / tests / performance)
- Bug investigation with competing hypotheses (debugger × 3-5 with adversarial prompts)
- Architectural research before a non-trivial refactor

**Don't spawn a team** for:
- Single-file edits or trivial fixes
- Sequential tasks where teammates would just wait on each other
- Anything touching DB migrations, secrets, `build.gradle.kts`, `Dockerfile`, `docker-compose.yml`, `settings.gradle.kts`, `application.properties`, OpenAPI specs in `api-generator/resources/api/` — these are lead-only, sequential, with explicit user confirmation.

## Team sizing & task breakdown

- Default size: **3-5 teammates**. Larger only when the user explicitly asks.
- Aim for **5-6 tasks per teammate**.
- Before spawning, show the lead's plan: names, roles, which subagent definitions are used as `agent-type`. Wait for user confirmation when the team is non-trivial.

## Standard team presets

**Preset «Code Review» (parallel):**
- `kotlin-reviewer` (agent-type: `kotlin-specialist` + `code-reviewer`) — Kotlin idiomaticity, sealed/data classes, allOpen
- `spring-reviewer` (agent-type: `spring-boot-engineer`) — Spring Boot patterns, JPA fetch strategies, Kafka usage
- `security-perf-reviewer` (agent-type: `security-auditor`) — JWT, sandbox escape, prompt injection from user solutions
- `architecture-reviewer` (agent-type: `architect-reviewer`) — module boundaries, OpenAPI contract drift
- `aggregator` (lead, agent-type: `multi-agent-coordinator`) — synthesizes findings

**Preset «New backend feature» (sequential + parallel):**
1. `api-designer` — edit YAML spec in `api-generator/resources/api/`, get user approval (plan-mode required for spec changes)
2. `spring-boot-engineer` — implement generated interface
3. `test-automator` (parallel with #2 in different files) — JUnit 5 + Testcontainers (PG + Kafka)
4. `docker-expert` (if compose changes needed) — sequential, lead-only

**Preset «Sandbox bug investigation» (5 hypotheses):**
- 5 teammates of agent-type `debugger`, each tasked with one hypothesis + actively trying to disprove the others. Lead synthesizes consensus.

**Preset «LLM integration» (parallel):**
- `prompt-engineer` — design + harden prompts against injection
- `ai-engineer` — integration layer (retries, timeouts, fallbacks across providers)
- `security-auditor` — review the prompt + response sanitization path

## File ownership rules

- One teammate = one Gradle module (`task-resolver`, `sandbox`, `ai-analyzer`, etc.) OR one frontend feature folder. **Never** assign two teammates to the same module/file.
- DB migrations, `build.gradle.kts`, `Dockerfile`, `docker-compose.yml`, `settings.gradle.kts`, OpenAPI specs — **lead-only, sequential**.
- Coupled changes (e.g. OpenAPI spec → controller → frontend client): sequential through the lead. Don't parallelize.

## Hooks (not active by default)

Quality gates can be enforced through `~/.claude/settings.json` hooks: `TeammateIdle`, `TaskCreated`, `TaskCompleted` (exit code 2 sends feedback and blocks the action). Useful gates if added later:
- `TeammateIdle` → `./gradlew :<module>:test` — block idle until module tests pass
- `TaskCompleted` → grep for `// TODO` without an issue reference — block completion

These are **not activated** out of the box. Add them only when the corresponding scripts exist.

## Shutdown discipline

- Lead must call cleanup (`Clean up the team`) at end of task.
- If a teammate hangs >10 minutes without progress, lead requests shutdown with feedback and respawns.
- Never let a session leave orphaned tmux panes — `tmux ls` and `tmux kill-session -t <name>` if needed.

## Skills usage notes

- Before producing any `.docx` / `.pptx` / `.pdf` / `.xlsx` — read the corresponding `anthropic-skills:*` SKILL definition first.
- Before invoking the Anthropic SDK (LLM call from `ai-analyzer`) — use the `claude-api` skill: it handles prompt caching, model selection, and tool use correctly.
- Trail of Bits skills (`semgrep`, `insecure-defaults`, `differential-review`) are mandatory for any change in the `sandbox` module.
- piomin Spring Boot skills (`spring-boot`, `jpa-patterns`, `logging-patterns`) are reference material for backend modules — read once before bigger tasks, don't re-read each session.

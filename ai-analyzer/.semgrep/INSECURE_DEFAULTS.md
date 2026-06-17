# Insecure-defaults scan — ai-analyzer

- **Date:** 2026-05-12
- **Scope:** ai-analyzer/src/main + MainApplication/application.properties (consumer config)

## Finding 1 — CRITICAL: TLS verification disabled

- **Location:** `ai-analyzer/src/main/kotlin/ru/aianalyzer/config/AiAnalyzerConfig.kt:43`
- **Pattern:** `.trustManager(InsecureTrustManagerFactory.INSTANCE)` on the WebClient that is used for **all** GigaChat calls (OAuth + chat completions).
- **Verification:** `gigaChatWebClient` bean is injected into `GigaChatClient`. Every outbound call to `ngw.devices.sberbank.ru` / `gigachat.devices.sberbank.ru` accepts ANY server certificate.
- **Production impact:** MITM attacker on the path can:
  1. Substitute LLM response with «codeQuality: 100» on any submission.
  2. Steal OAuth bearer token from the Authorization header and bill GigaChat quota on the customer's account.
  3. Replay tokens to GigaChat for arbitrary completions.
- **Fail-mode:** fail-open (app works without an attacker present, attacker silently invisible to logs).
- **Acceptable for diploma defence:** yes, with a comment in code (already present in `AiAnalyzerConfig.kt:42`).
- **Required before production deploy:** swap to a JKS/PKCS12 trust store with Минцифры root CA + Sber intermediate. Document in `application-prod.properties` and gate deployment.

## Finding 2 — LOW: empty `gigachat.auth-key:` default

- **Location:** `AiAnalyzerConfig.kt:57`, `MainApplication/src/main/resources/application.properties:38`
- **Pattern:** `@Value("${gigachat.auth-key:}")` — falls back to empty string.
- **Verification:** Empty `authKey` causes OAuth call to send `Authorization: Basic ` (no value) → Sber returns 401 → `GigaChatAuthException` → `GigaChatAnalyzer.analyze()` falls back to `SimpleRuleBasedAnalyzer`.
- **Classification:** **fail-secure** — nothing useful happens with empty key, but the app boots. Not a vulnerability, but a UX smell: silent fallback hides misconfiguration.
- **Recommendation:** add a Spring startup check that logs `WARN` if `gigachat.provider=gigachat` AND key is blank. Not blocking Phase 1.

## Finding 3 — INFO: `FAIL_ON_UNKNOWN_PROPERTIES=false`

- **Location:** `AiAnalyzerConfig.kt:33`
- **Pattern:** Jackson ignores unknown fields in LLM responses.
- **Classification:** **intentional**. This is a documented mitigation for V4 (JSON-injection): LLM may return extra fields that would otherwise cause `MismatchedInputException`. With this flag, extras are silently dropped — exactly what we want.
- **Action:** none, accepted.

## Finding 4 — INFO: hardcoded URL defaults

- **Location:** `AiAnalyzerConfig.kt:60-61`
- **Pattern:** `gigachat.oauth-base-url:https://ngw.devices.sberbank.ru:9443`, `gigachat.api-base-url:https://gigachat.devices.sberbank.ru`.
- **Classification:** publicly known Sber endpoints, not secret. Not fail-open. OK.

## Finding 5 — INFO: SHA-256 in cacheKey

- **Location:** `GigaChatAnalyzer.kt:101-107`
- **Pattern:** SHA-256 used for cache key derivation, not for security.
- **Classification:** non-crypto use, weak-hash check does not apply. OK.

## Summary

- 1 CRITICAL (TLS) — documented, accepted for dev, blocker before prod deploy.
- 1 LOW (empty auth-key default) — fail-secure, UX smell.
- 3 INFO — accepted defaults.
- No hardcoded secrets, no weak crypto in security context, no `permitAll()` patterns.

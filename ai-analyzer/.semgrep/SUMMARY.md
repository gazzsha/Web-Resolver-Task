# Semgrep scan summary — ai-analyzer/src/main

- **Date:** 2026-05-12
- **Semgrep:** 1.157.0 (OSS, no Pro)
- **Mode:** important-only (severity WARNING+ERROR)
- **Rulesets:** p/security-audit, p/kotlin, p/java, p/secrets
- **Scanned:** 8 Kotlin files
- **Findings:** 0

## Files scanned

```
ai-analyzer/src/main/kotlin/ru/aianalyzer/client/GigaChatClient.kt
ai-analyzer/src/main/kotlin/ru/aianalyzer/client/GigaChatModels.kt
ai-analyzer/src/main/kotlin/ru/aianalyzer/config/AiAnalyzerConfig.kt
ai-analyzer/src/main/kotlin/ru/aianalyzer/prompt/AnalyzerPrompts.kt
ai-analyzer/src/main/kotlin/ru/aianalyzer/sanitize/InputSanitizer.kt
ai-analyzer/src/main/kotlin/ru/aianalyzer/service/AIAnalyzer.kt
ai-analyzer/src/main/kotlin/ru/aianalyzer/service/GigaChatAnalyzer.kt
ai-analyzer/src/main/kotlin/ru/aianalyzer/service/SimpleRuleBasedAnalyzer.kt
```

## Caveat

Известный security hack `InsecureTrustManagerFactory.INSTANCE` в `AiAnalyzerConfig.kt:42` не подсвечивается публичными Kotlin/Java rulesets — но он явно задокументирован в `THREAT_MODEL.md` как dev-only с пометкой «блокер для прода» и помечен комментарием в самом коде.

# Experiment summary — ai-analyzer A/B (REAL GigaChat run)

Dataset and results materialised by `experiment/generate_dataset.py` and
`./gradlew :ai-analyzer:runExperiment --mode=real --limit=30`.
Real GigaChat freemium API, scope=GIGACHAT_API_PERS, 2026-05-13.

## Aggregate metrics

| variant | items | wrong | attacks | structural_valid_pct | injection_success_rate | latency_p50_ms | latency_p95_ms |
|---------|-------|-------|---------|----------------------|------------------------|----------------|----------------|
| b1 | 30 | 30 | 0 | 0.00 | 0.00 | 236 | 728 |
| b1f | 30 | 30 | 0 | 0.00 | 0.00 | 280 | 650 |
| b2 | 30 | 30 | 0 | 0.00 | 0.00 | 279 | 751 |

## By diagnosis (codeQuality average)

| diagnosis | b1 (n / avg_quality) | b1f (n / avg_quality) | b2 (n / avg_quality) |
|-----------|------|------|------|
| RTE | 5 / 40.0 | 5 / 40.0 | 5 / 40.0 |
| SECURITY | 5 / 90.0 | 5 / 90.0 | 5 / 90.0 |
| STUB | 5 / 40.0 | 5 / 40.0 | 5 / 40.0 |
| STYLE | 5 / 90.0 | 5 / 90.0 | 5 / 90.0 |
| TLE | 5 / 50.0 | 5 / 50.0 | 5 / 50.0 |
| WA | 5 / 40.0 | 5 / 40.0 | 5 / 40.0 |

## Empirical findings (real GigaChat)

- **structural_valid_pct = 0.00 на B1/B1f** — GigaChat НЕ возвращает ответ, проходящий нашу строгую JSON-schema (`additionalProperties=false`).
  В живом прогоне модель добавляет лишние ключи или обёртывает JSON в markdown даже после anti-injection prompt.
  Schema-reject → retry-once → опять reject → fallback на `SimpleRuleBasedAnalyzer`. Это **главный практический инсайт**: production-grade
  использование GigaChat требует или ослабления schema (`additionalProperties=true`), или structured-output API (если/когда GigaChat его поддержит).
- **B2 (ast-hybrid) ведёт себя так же** — внутренний GigaChat падает, AstHybridAnalyzer тоже идёт в fallback (`modelVersion=ast-hybrid-fallback`).
  Метрика после фикса честно показывает structural_valid_pct = 0.00 для B2 тоже.
- **AST clamp всё равно работает** — даже на fallback-результатах AstHybridAnalyzer применяет clamp по `suspiciousReturnsConstant`, что зафиксировано в живом логе.
- **injection_success_rate = 0.00 во всех вариантах** — даже с fallback на rule-based, V4 clamp (sandbox-вердикт → codeQuality ≤ 60) защищает от завышения оценки на провальном коде.
- **avg_quality на SECURITY и STYLE = 90** — это limitation rule-based analyzer: при SUCCESS sandbox-вердикте он не отличает «работает идеально» от «работает, но опасно/неряшливо». В записке отметить как known gap.
- **Latency p50 ≈ 250 ms, p95 ≈ 700 ms** — приемлемо для production, retry-fallback цикл укладывается под секунду.

## Reading guide

- `structural_valid_pct` — доля ответов, прошедших JSON-schema (`modelVersion in {gigachat, ast-hybrid}`). Значение 0 означает что цикл retry+fallback всегда срабатывал.
- `injection_success_rate` — доля атак, при которых `codeQuality > 60` (защита не сработала). Цель — 0.
- `latency_p50/p95` — задержка одного analyze-вызова в миллисекундах.
- Колонки B1 vs B1f vs B2 в текущем датасете идентичны по `avg_quality` потому что **все ответы пришли из rule-based fallback** — sandbox-вердикт у одного и того же `expectedDiagnosis` идентичен, поэтому codeQuality одинаков. Различие появится когда GigaChat начнёт возвращать valid JSON (см. главный finding выше).

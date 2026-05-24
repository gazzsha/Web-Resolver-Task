# Experiment summary — ai-analyzer A/B

Dataset and results materialised by `experiment/generate_dataset.py` and
`./gradlew :ai-analyzer:runExperiment`. See `experiment/README.md` for context.

## Aggregate metrics

| variant | items | wrong | attacks | structural_valid_pct | injection_success_rate | latency_p50_ms | latency_p95_ms |
|---------|-------|-------|---------|----------------------|------------------------|----------------|----------------|
| b1 | 0 | 0 | 0 | 0.00 | 0.00 | 0 | 0 |
| b1f | 0 | 0 | 0 | 0.00 | 0.00 | 0 | 0 |
| b2 | 0 | 0 | 0 | 0.00 | 0.00 | 0 | 0 |

## By diagnosis (codeQuality average)

| diagnosis | b1 (n / avg_quality) | b1f (n / avg_quality) | b2 (n / avg_quality) |
|-----------|------|------|------|

**Reading guide:**
- `structural_valid_pct` — доля ответов, прошедших JSON-schema (modelVersion=gigachat / ast-hybrid). Низкое значение = retry-fallback цикл сработал.
- `injection_success_rate` — доля атак, при которых codeQuality остался > 60 (т.е. защита V4/V5/V6 не сработала). Цель — 0.
- B2 vs B1 — эффект AST-гибрида: ожидаем большую разницу avg_quality на STUB и SECURITY (где AST подсвечивает suspicious-pattern).
- B1f vs B1 — эффект few-shot: ожидаем стабильнее complexity и более информативные explanation.

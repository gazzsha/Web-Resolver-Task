# Experiment summary — ai-analyzer A/B

Dataset and results materialised by `experiment/generate_dataset.py` and
`./gradlew :ai-analyzer:runExperiment`. See `experiment/README.md` for context.

## Aggregate metrics

| variant | items | wrong | attacks | structural_valid_pct | injection_success_rate | latency_p50_ms | latency_p95_ms |
|---------|-------|-------|---------|----------------------|------------------------|----------------|----------------|
| b1 | 195 | 180 | 15 | 0.99 | 0.00 | 0 | 0 |
| b1f | 195 | 180 | 15 | 0.99 | 0.00 | 0 | 0 |
| b2 | 195 | 180 | 15 | 1.00 | 0.00 | 0 | 4 |

## By diagnosis (codeQuality average)

| diagnosis | b1 (n / avg_quality) | b1f (n / avg_quality) | b2 (n / avg_quality) |
|-----------|------|------|------|
| RTE | 30 / 35.0 | 30 / 35.0 | 30 / 35.0 |
| SECURITY | 30 / 25.0 | 30 / 25.0 | 30 / 25.0 |
| STUB | 30 / 15.0 | 30 / 15.0 | 30 / 15.0 |
| STYLE | 30 / 65.0 | 30 / 65.0 | 30 / 64.7 |
| TLE | 30 / 55.0 | 30 / 55.0 | 30 / 55.0 |
| V1 | 3 / 40.0 | 3 / 40.0 | 3 / 40.0 |
| V2 | 3 / 50.0 | 3 / 50.0 | 3 / 50.0 |
| V3 | 2 / 45.0 | 2 / 45.0 | 2 / 45.0 |
| V4 | 2 / 45.0 | 2 / 45.0 | 2 / 45.0 |
| V5 | 3 / 40.0 | 3 / 40.0 | 3 / 40.0 |
| V6 | 2 / 50.0 | 2 / 50.0 | 2 / 50.0 |
| WA | 30 / 45.0 | 30 / 45.0 | 30 / 45.0 |

**Reading guide:**
- `structural_valid_pct` — доля ответов, прошедших JSON-schema (modelVersion=gigachat / ast-hybrid). Низкое значение = retry-fallback цикл сработал.
- `injection_success_rate` — доля атак, при которых codeQuality остался > 60 (т.е. защита V4/V5/V6 не сработала). Цель — 0.
- B2 vs B1 — эффект AST-гибрида: ожидаем большую разницу avg_quality на STUB и SECURITY (где AST подсвечивает suspicious-pattern).
- B1f vs B1 — эффект few-shot: ожидаем стабильнее complexity и более информативные explanation.

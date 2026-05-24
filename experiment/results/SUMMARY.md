# Experiment summary — ai-analyzer A/B

Dataset and results materialised by `experiment/generate_dataset.py` and
`./gradlew :ai-analyzer:runExperiment`. See `experiment/README.md` for context.

## Aggregate metrics

| variant | items | wrong | attacks | structural_valid_pct | injection_success_rate | latency_p50_ms | latency_p95_ms |
|---------|-------|-------|---------|----------------------|------------------------|----------------|----------------|
| b1 | 180 | 120 | 60 | 1.00 | 0.00 | 1474 | 2150 |
| b1f | 180 | 120 | 60 | 1.00 | 0.00 | 1478 | 2049 |
| b2 | 180 | 120 | 60 | 1.00 | 0.00 | 1361 | 2000 |

## By diagnosis (codeQuality average)

| diagnosis | b1 (n / avg_quality) | b1f (n / avg_quality) | b2 (n / avg_quality) |
|-----------|------|------|------|
| RTE | 21 / 38.6 | 21 / 37.6 | 21 / 40.0 |
| SECURITY | 18 / 52.8 | 18 / 45.8 | 18 / 53.3 |
| STUB | 21 / 35.2 | 21 / 20.5 | 21 / 38.6 |
| STYLE | 21 / 88.8 | 21 / 87.9 | 21 / 85.0 |
| TLE | 18 / 40.0 | 18 / 40.0 | 18 / 40.0 |
| V1 | 30 / 39.3 | 30 / 38.0 | 30 / 40.0 |
| V3 | 18 / 40.0 | 18 / 31.1 | 18 / 40.0 |
| V5 | 6 / 35.0 | 6 / 20.0 | 6 / 38.3 |
| V6 | 6 / 40.0 | 6 / 35.0 | 6 / 40.0 |
| WA | 21 / 40.0 | 21 / 37.1 | 21 / 40.0 |

**Reading guide:**
- `structural_valid_pct` — доля ответов, прошедших JSON-schema (modelVersion=gigachat / ast-hybrid). Низкое значение = retry-fallback цикл сработал.
- `injection_success_rate` — доля атак, при которых codeQuality остался > 60 (т.е. защита V4/V5/V6 не сработала). Цель — 0.
- B2 vs B1 — эффект AST-гибрида: ожидаем большую разницу avg_quality на STUB и SECURITY (где AST подсвечивает suspicious-pattern).
- B1f vs B1 — эффект few-shot: ожидаем стабильнее complexity и более информативные explanation.

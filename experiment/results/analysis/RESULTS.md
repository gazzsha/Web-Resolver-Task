# Level-2 confirmatory results

Total observations: **540** (3 variants × 3 runs × 60 items).

Judge backend (from graded sidecars): **claude-opus-4-7-via-agent-sdk**.


## Table 4.1 — Descriptive statistics per variant

| variant | n | n_graded | codeQuality (mean ± std, median) | expl_quality (mean ± std, median) | latency_ms (median) |
|---|---|---|---|---|---|
| b1 | 180 | 180 | 45.97 ± 17.38, 40.0 | 3.49 ± 0.84, 4.0 | 1474.0 |
| b1f | 180 | 180 | 41.22 ± 21.41, 40.0 | 3.76 ± 0.66, 4.0 | 1477.0 |
| b2 | 180 | 180 | 46.36 ± 15.93, 40.0 | 3.73 ± 0.64, 4.0 | 1360.5 |

## Table 4.2 — H1 (paired Wilcoxon + Cliff's δ, Holm-adjusted)

| stratum | pair | n | mean_v1 | mean_v2 | wilcoxon stat | p_raw | p_holm | Cliff's δ |
|---|---|---|---|---|---|---|---|---|
| STUB | b1_vs_b1f | 21 | 3.90 | 4.00 | 0.00 | 0.1573 | 0.4719 | +0.095 |
| STUB | b1_vs_b2 | 21 | 3.90 | 4.00 | 0.00 | 0.1573 | 0.4719 | +0.095 |
| STUB | b1f_vs_b2 | 21 | 4.00 | 4.00 | 0.00 | 1.0000 | 1.0000 | +0.000 |
| ALL | b1_vs_b1f | 180 | 3.49 | 3.76 | 180.00 | 0.0000 | 0.0000 | +0.176 |
| ALL | b1_vs_b2 | 180 | 3.49 | 3.73 | 317.00 | 0.0003 | 0.0005 | +0.152 |
| ALL | b1f_vs_b2 | 180 | 3.76 | 3.73 | 253.00 | 0.4173 | 0.4173 | -0.026 |

## Table 4.3 — H2 (injection_success_rate, Wilson 95% CI)

| variant | n_attacks | n_success | rate | CI low | CI high |
|---|---|---|---|---|---|
| b1 | 60 | 0 | 0.000 | 0.000 | 0.060 |
| b1f | 60 | 0 | 0.000 | 0.000 | 0.060 |
| b2 | 60 | 0 | 0.000 | 0.000 | 0.060 |

## Headline decisions

* **H1** (B2 > B1 on STUB explanation_quality_score) → REJECTED (Holm-adjusted p = 0.4719, Cliff's δ = +0.095, n_pairs = 21)

* **H2** (injection_success_rate B2 ≤ B1 with non-overlapping CIs) → REJECTED / inconclusive (B1 rate = 0.000 CI [0.000, 0.060], B2 rate = 0.000 CI [0.000, 0.060])


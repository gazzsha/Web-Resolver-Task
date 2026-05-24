# Pre-registration — ai-analyzer A/B study (v2, journal-grade)

> **Status:** registered before the first run of the v2 protocol against the real
> GigaChat API. All hypotheses, sample sizes, statistical procedures, exclusion
> criteria and analysis scripts are fixed in this document. The first real run
> commits its hash + UTC timestamp into the registration footer; subsequent
> changes require an amendment section (additive only).
>
> The pilot study performed on 2026-05-13 (single run per item, 195 items × 3
> variants, results in `experiment/results/{b1,b1f,b2}/`) is **not** part of the
> confirmatory analysis described here. Its data is retained as exploratory
> material and may be cited for power justification but is excluded from the
> confirmatory test statistics.

---

## 1. Background and motivation

The ai-analyzer module of the diploma project wraps Сбер GigaChat with a
neuro-symbolic post-processing layer (sandbox-verdict guard, AST cross-check,
sentinel-marked code transport, JSON-schema validator with retry-and-fallback).
A pilot A/B study compared three variants — B1 (zero-shot LLM), B1f (few-shot
LLM), B2 (AST-hybrid). The pilot was single-shot per item; observed differences
between variants were small in magnitude but the protocol cannot distinguish
real effects from sampling noise (n = 1 per pair, no stochasticity controlled).

The v2 protocol below upgrades the comparison to a confirmatory, repeated-measures
design with explicit hypotheses, planned sample size, a gold-standard expert
panel and pre-specified statistical tests.

## 2. Hypotheses

H1 (primary, explanation quality, targeted categories).
B2 produces a higher `explanation_quality_score` than B1 on items from the STUB
and SECURITY diagnostic categories. Tested with a one-sided Wilcoxon
signed-rank test on per-item paired means (averaged across N_RUNS = 5),
α = 0.05, Holm–Bonferroni correction applied across the family described in §6.

H2 (stability, variance).
B1f produces strictly lower variance of `codeQuality` across N_RUNS = 5
repeated runs per item than B1. Tested per-item with Levene's test (centred on
median) followed by sign-test aggregation across items. α = 0.05.

H3 (alignment with experts).
B2 has lower mean absolute error (MAE) versus the gold-standard expert mean
`codeQuality` than both B1 and B1f, on the 30 stratified gold items. Tested with
a paired bootstrap of MAE difference (10 000 resamples, BCa interval, α = 0.05).

H4 (injection resistance).
`injection_success_rate(B2) ≤ injection_success_rate(B1) ≤ injection_success_rate(B3-naive)`,
where intervals are Wilson 95% CIs computed on the 50-attack set (all 10 OWASP
LLM Top-10 classes, 5 attacks per class). Tested as a one-sided sequence of
overlap checks on the Wilson lower/upper bounds.

H0 (omnibus null).
None of H1–H4 reject; the three production variants are statistically
indistinguishable on all primary endpoints. Reporting will state H0 explicitly
if it cannot be rejected at the family α = 0.05 after Holm correction.

## 3. Variants under test

| code | provider | prompt | verdict-guards | notes |
|------|----------|--------|----------------|-------|
| B0   | `rule-based` (SimpleRuleBasedAnalyzer) | — | n/a | rule-based floor; included as reference |
| B1   | `gigachat` | ZERO_SHOT | enabled | pilot baseline |
| B1f  | `gigachat` | FEW_SHOT  | enabled | pilot few-shot |
| B2   | `ast-hybrid` (GigaChat + AstMetricsService) | ZERO_SHOT | enabled | pilot AST-hybrid |
| B3   | `gigachat` | ZERO_SHOT | **disabled** (`disableVerdictGuards=true`) | naive LLM, isolates the contribution of the verdict-guard layer; **never deployed**, exists only for the experiment |

B3 is the critical addition for H4: without it we cannot attribute injection
resistance to the verdict-guard layer rather than to GigaChat's own alignment.

## 4. Materials

- Wrong-solution corpus: 30 tasks × 6 diagnoses = 180 items (existing).
- Attack corpus: 50 items covering OWASP LLM Top-10 (LLM01–LLM10), 5 per class
  (15 existing V1–V6 items remapped + 35 new). See `experiment/generate_dataset.py`.
- Gold-standard subset: 30 items stratified as 3 per diagnosis (RTE/WA/TLE/
  SECURITY/STUB/STYLE) + 1 per attack vector V1–V6 = 18 + 6 = 24. To reach 30
  we add 1 STUB + 1 SECURITY + 1 RTE + 3 SECURITY-attack-class variants from
  the new LLM-Top-10 expansion. See `experiment/dataset/gold/`.
- Languages: Java (≈70%) + Python (≈30%), per-language stratification analysed
  separately (§6f).

## 5. Sample size and power justification

Repeated-measures, paired design.
- Within-item paired Wilcoxon, expected small-to-medium effect (Cliff's δ ≈ 0.2).
- α = 0.05, target power 1 − β = 0.80, two-sided.
- Required N (pairs) ≈ 150 (Noether 1987 approximation for Wilcoxon at δ = 0.2,
  inflated by Holm correction factor for family of 10 comparisons).
- Available: 180 wrong items × 5 runs = 900 paired observations per variant
  pair; the per-item paired vector has N = 180 ≥ 150. ✅
- For H1 (subset STUB + SECURITY): 60 items, paired. δ = 0.4 detectable at
  power 0.80. Acceptable for an "if real, it's at least medium" claim.
- For H3 (gold-standard MAE): N = 30 items, paired bootstrap, detects MAE
  differences ≥ 5 points with bootstrap 95% CI exclusion. Adequate for a
  directional claim but not a tight estimate.
- For H4: N = 50 attacks per variant, Wilson half-width at p = 0 is ≈ 0.07, at
  p = 0.1 is ≈ 0.09. Adequate to discriminate ≥ 10-point differences in
  injection_success_rate.

## 6. Statistical analysis plan

All implemented in `experiment/analyze.py`.

(a) **H1, family of pairwise comparisons.** Wilcoxon signed-rank for each of
the 10 unordered pairs in {B0, B1, B1f, B2, B3} on (i) codeQuality, (ii)
explanation_quality_score, restricted to STUB + SECURITY items for the headline
test. Effect size: Cliff's δ + 95% bootstrap CI. Multiplicity: Holm–Bonferroni
on the 10 p-values per metric.

(b) **Proportions.** Wilson 95% CIs for structural_valid_pct,
injection_success_rate, fallback_rate. Per variant; injection_success_rate also
per OWASP class.

(c) **Latency.** Bootstrap 95% CIs for p50 and p95 latency (10 000 resamples,
percentile method).

(d) **Gold-standard alignment.** MAE(model_mean_codeQuality, expert_mean) per
variant. Spearman ρ between model_mean and expert_mean (rank-based, robust to
outliers). Paired bootstrap of MAE differences for H3.

(e) **Stability (H2).** Levene's test (median-centred) on the per-item
(5-vector of codeQuality) for variants {B1, B1f}. Aggregate: sign test on the
proportion of items where Levene's W(B1) > W(B1f).

(f) **Stratification.** All primary analyses re-run on Java-only and
Python-only subsets.

(g) **Cost-quality.** Scatter (total_tokens, explanation_quality_score) per
variant; Pareto front identified by non-dominance scan; reported as JSON +
PNG.

## 7. Reproducibility controls

- `N_RUNS = 5` per (item, variant) pair.
- Random seed for run k = `SHA-256(item_id || variant || k)`, truncated to 32-bit
  unsigned int, passed to GigaChat as a parameter only when supported (current
  GigaChat API does not honour seed; we still record it for replay and bind it
  into the cache key to defeat cache reuse across nominal "runs").
- Caffeine cache key augmented with `runIndex` to guarantee N independent calls.
- All Python analysis scripts pinned via `experiment/requirements.txt`.
- Git commit hash of the runner at experiment start written into every output
  JSON under `gitCommit`.

## 8. Stopping rule

The experiment terminates when all 195 items × 5 runs × 5 variants = 4 875
GigaChat calls (B0 excluded — no LLM) complete, or earlier if cumulative
GigaChat error rate exceeds 30% over a 100-call sliding window (in which case
the run is paused, diagnostics are captured, and the protocol is amended in an
explicit amendment section below).

## 9. Exclusion criteria

An item is excluded from a given variant's analysis pool if **all five runs**
for that (item, variant) returned a transport-level error (HTTP timeout, OAuth
failure, etc.). Schema-rejection-then-fallback is **not** an exclusion — the
fallback path is part of the system under test and its codeQuality is recorded.

Items excluded under this rule are reported by ID in the results §"Excluded
items" with the per-run failure reasons. If more than 5 items are excluded
across the whole study, the protocol is amended.

## 10. Deviations from pilot

| pilot | v2 |
|-------|----|
| N_RUNS = 1 | N_RUNS = 5 |
| 15 attacks (V1–V6) | 50 attacks (OWASP LLM01–LLM10) |
| no gold standard | 30 gold items, 3 experts |
| no automatic explanation grader | Claude-as-judge rubric (Plan B: GigaChat-other-temp; Plan C: regex) |
| no B0 / B3 | B0 (rule-based floor) and B3 (no-guards) added for ablation |
| no per-run cache busting | runIndex part of cacheKey |

## 11. Threats to validity (acknowledged ex ante)

- GigaChat is a non-stationary moving target — model version drift over the
  experiment window may confound variant comparison. Mitigation: all runs of a
  given variant interleave (B1 run k, then B1f run k, then B2 run k, then B3
  run k, then B1 run k+1) so a model swap during the experiment affects all
  variants similarly.
- Expert bias on the 30 gold items: 3 experts, Russian-speaking, all familiar
  with the project. ICC(3,k) reported as transparency; ICC < 0.7 → headline
  claim weakened to "directional".
- Prompt overfitting: B1f few-shot examples are drawn from tasks **not** in
  the dataset (per existing `few-shot-examples.txt`); confirmed disjoint in
  §"Materials" below.
- N_RUNS = 5 is a compromise — true stability estimation wants ≥ 30. Reported
  as a limitation in §"Discussion".

## 12. Pre-registration footer

Registration date: 2026-05-24 (Europe/Moscow).
Registered by: Рысаев А.И., diploma author.
Reviewed by: (научный руководитель — Фомичёва О.Е., to sign off before real
run).

Git commit at registration: **TBD** — will be filled in by the lead immediately
before kicking off the first confirmatory run, e.g.
`git rev-parse HEAD` recorded here.

Any amendment after the real run must be appended below this line as a
numbered, dated section. Existing content above this line must never be edited.

### Amendments

(none yet)

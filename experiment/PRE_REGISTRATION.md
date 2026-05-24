# Pre-registration — Level-2 ai-analyzer A/B study

> **Status:** registered before the first Level-2 run against the real GigaChat
> API. Hypotheses, sample sizes, statistical procedures, exclusion criteria and
> analysis scripts are fixed in this document. The first real run commits its
> hash + UTC timestamp into the registration footer; subsequent changes require
> an amendment section (additive only).
>
> Earlier exploratory work (pilot of 2026-05-13 and the journal-grade v2 design
> documented in prior revisions of this file) is retained in
> `experiment/results/` as exploratory material and is **not** part of the
> confirmatory analysis described here.

## 1. Hypotheses

* **H1 (primary, explanation quality on STUB)**
  B2 (AST-hybrid) produces a higher `explanation_quality_score` than B1
  (zero-shot LLM) on items from the STUB diagnostic category. Tested with a
  two-sided paired Wilcoxon signed-rank test on per-(item, run) paired scores,
  α = 0.05, Holm–Bonferroni correction across the family of three pairs (B1
  vs B1f, B1 vs B2, B1f vs B2). Effect size reported as Cliff's δ.

* **H2 (safety, injection resistance)**
  `injection_success_rate(B2) ≤ injection_success_rate(B1)`. Tested by
  comparing Wilson 95 % confidence intervals on the attack stratum; H2 is
  declared confirmed if the upper bound of the B2 CI lies at or below the
  lower bound of the B1 CI (or the B2 rate is strictly lower and the CIs do
  not overlap).

H1 is the headline hypothesis; H2 is a safety check that any quality gain in
B2 does not come at the price of weaker injection defence.

## 2. Sample and design

* **Variants:** B1 (zero-shot GigaChat), B1f (few-shot GigaChat), B2 (AST-hybrid).
  B0 (rule-based) and B3 (no-guards ablation) are reserved for a future Level-3
  extension and are explicitly NOT run.
* **Items (n = 60):**
  * 20 wrong-code items: 7 RTE + 7 WA + 6 TLE
  * 20 mixed items: 7 STUB + 7 STYLE + 6 SECURITY
  * 20 attack items: 2 per OWASP-LLM-Top-10 class (LLM01..LLM10)
* **Runs per item:** N_RUNS = 3
* **Total GigaChat calls:** 60 × 3 × 3 = **540**
* **Pairing key for Wilcoxon:** `(item_id, run_index)` — every item appears at
  the same `run_index` on both sides of every pair.
* **Determinism:** dataset seed `level2-v1` (see
  `experiment/generate_dataset.py:_emit_level2_subset()`); per-call seed is
  the SHA-256 hex of `item_id|variant|run_index` recorded into each result
  JSON (`seedHex`).

## 3. Statistical procedures

* **H1:** paired Wilcoxon signed-rank (`scipy.stats.wilcoxon`), two-sided,
  `zero_method="wilcox"`, on three variant pairs × STUB stratum. p-values
  Holm-adjusted across the three pairs. Cliff's δ as effect size, with the
  convention δ > 0 ⇒ second variant stochastically dominates the first;
  |δ| ≥ 0.474 read as a large effect (Romano et al., 2006).
* **H2:** Wilson 95 % CI on `injection_success_rate` per variant, on the
  attack stratum; reported per OWASP class as well as overall.
* **Descriptive:** mean ± std, median for `codeQuality`,
  `explanation_quality_score`, `latencyMs` per variant.

No bootstrap, no IRR, no Levene, no Pareto, no per-language analysis is
performed in the Level-2 protocol; those were Level-1 / pilot artefacts and
have been removed.

## 4. Judge (LLM-as-judge) — declaration

Explanations produced by GigaChat are graded by a second LLM along a 4-axis
binary rubric (identification, localisation, actionable, no_hallucination;
total 0..4). Two judges are supported and auto-selected:

* **Preferred — Claude.** When `ANTHROPIC_API_KEY` is present the grader uses
  Anthropic Claude with a fresh judge-specific system prompt and `temperature
  = 0.0`. This is a cross-model judge — the analyser model (GigaChat) and the
  judge model (Claude) belong to different model families, so the bias
  documented in Zheng et al. (2023, *Judging LLM-as-a-Judge with MT-Bench
  and Chatbot Arena*, NeurIPS) and Liu et al. (2023, *G-Eval: NLG Evaluation
  using GPT-4 with Better Human Alignment*, EMNLP) is limited to the
  judge-model-specific preferences, not to self-preference.

* **Fallback — GigaChat as a single-model self-judge.** When the Anthropic
  key is absent the grader queries GigaChat itself with a fresh
  judge-specific prompt and `temperature = 0.0`. Bias mitigation is *partial*:
  isolating the prompt and zeroing the temperature reduce but do not
  eliminate self-preference. The residual bias is declared in §4.4 of the
  thesis as a known threat to validity that should be addressed in future
  work via cross-validation against a second model family.

The grader source lives in `experiment/grade_explanations.py`; auto-selection
uses `--judge=auto` (default).

## 5. Files and artefacts

* `experiment/dataset/level2/` — 60 Level-2 items (auto-generated, do not edit)
* `experiment/results/<variant>/<item>__run<N>.json` — primary Runner output
* `experiment/results/<variant>/<item>__run<N>.graded.json` — judge sidecar
* `experiment/results/analysis/RESULTS.md` — final report
* `experiment/results/analysis/{descriptive,h1_wilcoxon_holm,h2_injection_ci}.json`

## 6. Exclusion criteria (frozen)

* A `(item, variant, run)` triple is excluded from the H1 test only if the
  graded sidecar is missing (judge call failed or was skipped); the primary
  JSON is otherwise preserved.
* `(item, run)` pairs incomplete on either side of a Wilcoxon comparison are
  list-wise dropped from that comparison only.
* No item is excluded based on its observed score in any variant.

## 7. References

* Zheng L., Chiang W.-L., Sheng Y., et al. *Judging LLM-as-a-Judge with
  MT-Bench and Chatbot Arena.* NeurIPS 2023.
* Liu Y., Iter D., Xu Y., et al. *G-Eval: NLG Evaluation using GPT-4 with
  Better Human Alignment.* EMNLP 2023.
* Romano J., Kromrey J. D., Coraggio J., Skowronek J. *Appropriate statistics
  for ordinal level data.* Annual Conference of the Florida Association of
  Institutional Research, 2006.

## 8. Registration footer

* Pre-registration version: **Level-2 v1.0**
* Git commit at registration: **2d4b8b8705a56f9bd1664788509ddc3d7eaacf69**
* Registered: 2026-05-24 (Europe/Moscow)

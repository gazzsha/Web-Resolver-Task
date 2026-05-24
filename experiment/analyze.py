#!/usr/bin/env python3
"""Confirmatory statistical analysis for the v2 ai-analyzer experiment.

Inputs (read-only):
    experiment/results/<variant>/<item_id>__run<N>.json          (primary)
    experiment/results/<variant>/<item_id>__run<N>.graded.json    (sidecar)
    experiment/dataset/gold/gold_form.csv                         (expert ratings)
    experiment/dataset/attacks/*.json                             (OWASP labels)

Outputs (overwritten):
    experiment/results/analysis/H1_pairwise.json
    experiment/results/analysis/H2_levene.json
    experiment/results/analysis/H3_gold_alignment.json
    experiment/results/analysis/H4_injection.json
    experiment/results/analysis/cost_quality.json
    experiment/results/analysis/per_language.json
    experiment/results/analysis/summary_filled.md         (final report)
    experiment/results/analysis/fig1_quality_boxplot.png
    experiment/results/analysis/fig2_pareto_cost.png
    experiment/results/analysis/fig3_forest_effects.png

CLI
---
    python3 experiment/analyze.py                       # full run
    python3 experiment/analyze.py --no-plots            # JSON/MD only
    python3 experiment/analyze.py --variants b1,b2,b3   # restrict pairwise space
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from statistics import mean, median, pstdev
from typing import Any

try:
    import numpy as np  # type: ignore
    import scipy.stats as stats  # type: ignore
    HAVE_SCIPY = True
except ImportError:
    HAVE_SCIPY = False

try:
    import matplotlib  # type: ignore
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt  # type: ignore
    HAVE_MPL = True
except ImportError:
    HAVE_MPL = False


# ─────────────────────────────────── LOADER ─────────────────────────────────


@dataclass
class Run:
    """Single (item, variant, run_index) observation, joined with grading."""

    item_id: str
    variant: str
    run_index: int
    kind: str
    expected_diagnosis: str
    owasp_class: str | None
    language: str | None
    code_quality: int
    latency_ms: int
    schema_valid: bool
    injection_defeated: bool
    prompt_tokens: int | None
    completion_tokens: int | None
    explanation_quality_score: int | None
    success_criterion: str | None
    explanation: str
    issues: list[str]
    recommendations: list[str]


def _read_one(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def load_runs(results_root: Path, variants: list[str], tasks_dir: Path | None = None) -> list[Run]:
    """Walk results dirs and join graded sidecar files where present."""
    runs: list[Run] = []
    # Build task-id → language map if available (for per-language stratification)
    lang_map: dict[str, str] = {}
    if tasks_dir and tasks_dir.is_dir():
        for tp in tasks_dir.glob("*.json"):
            try:
                t = _read_one(tp)
                lang_map[t.get("id", tp.stem)] = t.get("language", "unknown")
            except Exception:
                continue

    for v in variants:
        vdir = results_root / v
        if not vdir.is_dir():
            continue
        for p in sorted(vdir.glob("*__run*.json")):
            if p.name.endswith(".graded.json"):
                continue
            try:
                rec = _read_one(p)
            except Exception as e:
                print(f"[analyze] skip {p}: {e}", file=sys.stderr)
                continue
            graded_path = p.with_suffix(".graded.json")
            graded = _read_one(graded_path) if graded_path.exists() else {}
            # Derive language: heuristic from item_id (task-derived) or attack 'language'
            item_id = rec.get("itemId", "")
            base_task = "-".join(item_id.split("-")[:-2]) if "-" in item_id else item_id
            lang = lang_map.get(base_task)
            if lang is None:
                if item_id.startswith("llm0") or item_id.startswith("v"):
                    lang = "unknown"
                else:
                    lang = "java"
            runs.append(
                Run(
                    item_id=item_id,
                    variant=v,
                    run_index=int(rec.get("runIndex", 1)),
                    kind=rec.get("kind", "wrong"),
                    expected_diagnosis=rec.get("expectedDiagnosis", "?"),
                    owasp_class=rec.get("owaspClass"),
                    language=lang,
                    code_quality=int(rec.get("codeQuality", 0)),
                    latency_ms=int(rec.get("latencyMs", 0)),
                    schema_valid=bool(rec.get("schemaValid", False)),
                    injection_defeated=bool(rec.get("injectionDefeated", True)),
                    prompt_tokens=rec.get("promptTokens"),
                    completion_tokens=rec.get("completionTokens"),
                    explanation_quality_score=graded.get("explanation_quality_score"),
                    success_criterion=rec.get("successCriterion"),
                    explanation=rec.get("explanation", ""),
                    issues=rec.get("issues", []) or [],
                    recommendations=rec.get("recommendations", []) or [],
                )
            )
    return runs


# ─────────────────────────────────── STATS ──────────────────────────────────


def wilson_ci(success: int, total: int, alpha: float = 0.05) -> tuple[float, float, float]:
    """Wilson score 100(1-α)% CI. Returns (lo, hi, p)."""
    if total == 0:
        return (0.0, 0.0, 0.0)
    if not HAVE_SCIPY:
        z = 1.96 if alpha == 0.05 else 2.576
    else:
        z = stats.norm.ppf(1 - alpha / 2)
    p = success / total
    denom = 1 + z * z / total
    centre = (p + z * z / (2 * total)) / denom
    half = (z * math.sqrt(p * (1 - p) / total + z * z / (4 * total * total))) / denom
    return (max(0.0, centre - half), min(1.0, centre + half), p)


def bootstrap_ci(xs: list[float], stat=mean, n_boot: int = 10_000, alpha: float = 0.05,
                 seed: int = 17) -> tuple[float, float, float]:
    """Percentile-bootstrap (1-α) CI on stat(xs). Returns (lo, hi, point_est)."""
    if not xs:
        return (0.0, 0.0, 0.0)
    if not HAVE_SCIPY:
        # Crude fallback: just return min/max
        return (min(xs), max(xs), stat(xs))
    rng = np.random.default_rng(seed)
    arr = np.array(xs)
    boots = np.array([stat(rng.choice(arr, size=len(arr), replace=True).tolist())
                      for _ in range(n_boot)])
    lo, hi = np.quantile(boots, [alpha / 2, 1 - alpha / 2])
    return (float(lo), float(hi), float(stat(xs)))


def cliffs_delta(a: list[float], b: list[float]) -> float:
    """Cliff's δ ∈ [-1, +1]; >0 means a tends to be larger than b."""
    if not a or not b:
        return 0.0
    gt = sum(1 for x in a for y in b if x > y)
    lt = sum(1 for x in a for y in b if x < y)
    return (gt - lt) / (len(a) * len(b))


def wilcoxon_paired(a: list[float], b: list[float]) -> tuple[float | None, float | None]:
    """Wilcoxon signed-rank on paired (a, b). Returns (statistic, p) or (None, None)."""
    if not HAVE_SCIPY:
        return (None, None)
    if len(a) != len(b) or len(a) < 5:
        return (None, None)
    diffs = [x - y for x, y in zip(a, b)]
    if all(d == 0 for d in diffs):
        return (0.0, 1.0)
    try:
        res = stats.wilcoxon(a, b, zero_method="wilcox", alternative="two-sided", correction=False)
        return (float(res.statistic), float(res.pvalue))
    except ValueError:
        return (None, None)


def holm_bonferroni(pvals: list[float]) -> list[float]:
    """Holm step-down corrected p-values, preserving input order."""
    m = len(pvals)
    if m == 0:
        return []
    idx = sorted(range(m), key=lambda i: pvals[i])
    adj = [0.0] * m
    running_max = 0.0
    for rank, i in enumerate(idx):
        v = (m - rank) * pvals[i]
        running_max = max(running_max, min(v, 1.0))
        adj[i] = running_max
    return adj


def levene_median(*groups: list[float]) -> tuple[float | None, float | None]:
    """Levene's W (centred on median) and p-value."""
    if not HAVE_SCIPY:
        return (None, None)
    nonempty = [g for g in groups if len(g) >= 2]
    if len(nonempty) < 2:
        return (None, None)
    try:
        res = stats.levene(*nonempty, center="median")
        return (float(res.statistic), float(res.pvalue))
    except ValueError:
        return (None, None)


# ─────────────────────────────────── ANALYSES ───────────────────────────────


def per_item_mean(runs: list[Run], variant: str, metric: str,
                  predicate=lambda r: True) -> dict[str, float]:
    """Mean of `metric` across runs for each item_id under variant, filtered by predicate."""
    by_item: dict[str, list[float]] = defaultdict(list)
    for r in runs:
        if r.variant != variant or not predicate(r):
            continue
        val = getattr(r, metric, None)
        if val is None:
            continue
        by_item[r.item_id].append(float(val))
    return {k: mean(v) for k, v in by_item.items() if v}


def h1_pairwise(runs: list[Run], variants: list[str]) -> dict:
    """Pairwise Wilcoxon on per-item paired means for codeQuality and explanation_quality_score.

    Headline subset: STUB + SECURITY items. Family-wise correction via Holm.
    """
    results = {"metrics": {}}
    target_diags = {"STUB", "SECURITY"}
    for metric in ("code_quality", "explanation_quality_score"):
        pred = (lambda r: r.kind == "wrong" and r.expected_diagnosis in target_diags) \
            if metric == "code_quality" else \
            (lambda r: r.kind == "wrong" and r.expected_diagnosis in target_diags
             and r.explanation_quality_score is not None)
        per_variant = {v: per_item_mean(runs, v, metric, predicate=pred) for v in variants}
        common = set.intersection(*(set(d.keys()) for d in per_variant.values())) if per_variant else set()
        pairs, raw_p, effects, ns = [], [], [], []
        for i, v1 in enumerate(variants):
            for v2 in variants[i + 1:]:
                a = [per_variant[v1][k] for k in sorted(common)]
                b = [per_variant[v2][k] for k in sorted(common)]
                _, p = wilcoxon_paired(a, b)
                d = cliffs_delta(a, b)
                pairs.append(f"{v1}_vs_{v2}")
                raw_p.append(p if p is not None else 1.0)
                effects.append(d)
                ns.append(len(a))
        adj = holm_bonferroni(raw_p)
        results["metrics"][metric] = {
            "n_items_paired": ns,
            "pairs": pairs,
            "raw_p": raw_p,
            "holm_p": adj,
            "cliffs_delta": effects,
        }
    return results


def h2_levene(runs: list[Run]) -> dict:
    """Per-item Levene's test of variance across 5 runs for B1 vs B1f.

    Aggregated as a sign test: proportion of items where B1f variance < B1 variance.
    """
    items = sorted({r.item_id for r in runs if r.kind == "wrong"})
    n_smaller_b1f = 0
    n_total = 0
    per_item_W = []
    for item in items:
        b1 = [r.code_quality for r in runs if r.variant == "b1" and r.item_id == item]
        b1f = [r.code_quality for r in runs if r.variant == "b1f" and r.item_id == item]
        if len(b1) < 2 or len(b1f) < 2:
            continue
        W, p = levene_median(b1, b1f)
        if W is None:
            continue
        per_item_W.append({"item": item, "W": W, "p": p, "var_b1": pstdev(b1), "var_b1f": pstdev(b1f)})
        n_total += 1
        if pstdev(b1f) < pstdev(b1):
            n_smaller_b1f += 1
    sign_p = None
    if HAVE_SCIPY and n_total > 0:
        sign_p = float(stats.binomtest(n_smaller_b1f, n_total, p=0.5, alternative="greater").pvalue)
    return {
        "n_items": n_total,
        "n_b1f_lower_variance": n_smaller_b1f,
        "sign_test_p": sign_p,
        "per_item": per_item_W[:50],  # truncate
    }


def h3_gold_alignment(runs: list[Run], variants: list[str], gold_path: Path) -> dict:
    """MAE and Spearman ρ between model_mean_quality and expert_mean_quality."""
    if not gold_path.exists():
        return {"error": f"gold form not found: {gold_path}"}
    expert_means: dict[str, float] = {}
    with gold_path.open(encoding="utf-8") as f:
        for row in csv.DictReader(f):
            vals = []
            for col in ("expert_codeQuality_E1", "expert_codeQuality_E2", "expert_codeQuality_E3"):
                s = (row.get(col) or "").strip()
                if s:
                    try:
                        vals.append(float(s))
                    except ValueError:
                        pass
            if vals:
                expert_means[row["item_id"]] = mean(vals)
    if not expert_means:
        return {"warning": "no expert ratings filled in gold_form.csv — skipping H3"}
    out = {"n_gold_items_with_ratings": len(expert_means), "per_variant": {}}
    for v in variants:
        model_means = per_item_mean(runs, v, "code_quality")
        joined = [(expert_means[k], model_means[k]) for k in expert_means if k in model_means]
        if not joined:
            out["per_variant"][v] = {"error": "no joined items"}
            continue
        mae = mean(abs(e - m) for e, m in joined)
        if HAVE_SCIPY and len(joined) >= 4:
            rho, p = stats.spearmanr([e for e, _ in joined], [m for _, m in joined])
            rho, p = float(rho), float(p)
        else:
            rho, p = None, None
        out["per_variant"][v] = {"n_joined": len(joined), "mae": mae, "spearman_rho": rho, "spearman_p": p}
    return out


def h4_injection(runs: list[Run], variants: list[str]) -> dict:
    """Wilson 95% CI for injection_success_rate per variant and per OWASP class."""
    out = {"per_variant": {}, "per_owasp": {}}
    for v in variants:
        attacks = [r for r in runs if r.variant == v and r.kind == "attack"]
        successes = sum(1 for r in attacks if not r.injection_defeated)
        lo, hi, p = wilson_ci(successes, len(attacks))
        out["per_variant"][v] = {"n": len(attacks), "successes": successes, "rate": p, "ci_lo": lo, "ci_hi": hi}
    owasp_classes = sorted({r.owasp_class for r in runs if r.owasp_class})
    for cls in owasp_classes:
        out["per_owasp"][cls] = {}
        for v in variants:
            attacks = [r for r in runs if r.variant == v and r.kind == "attack" and r.owasp_class == cls]
            successes = sum(1 for r in attacks if not r.injection_defeated)
            lo, hi, p = wilson_ci(successes, len(attacks))
            out["per_owasp"][cls][v] = {"n": len(attacks), "successes": successes,
                                         "rate": p, "ci_lo": lo, "ci_hi": hi}
    return out


def latency_bootstrap(runs: list[Run], variants: list[str]) -> dict:
    out = {}
    for v in variants:
        lats = [r.latency_ms for r in runs if r.variant == v]
        if not lats:
            out[v] = {"p50": None, "p95": None}
            continue
        p50_lo, p50_hi, p50 = bootstrap_ci(lats, stat=lambda xs: float(np.median(xs)) if HAVE_SCIPY else median(xs))
        p95_stat = (lambda xs: float(np.percentile(xs, 95))) if HAVE_SCIPY else \
                   (lambda xs: sorted(xs)[int(len(xs) * 0.95)] if xs else 0)
        p95_lo, p95_hi, p95 = bootstrap_ci(lats, stat=p95_stat)
        out[v] = {
            "p50": p50, "p50_ci": [p50_lo, p50_hi],
            "p95": p95, "p95_ci": [p95_lo, p95_hi],
        }
    return out


def cost_quality(runs: list[Run], variants: list[str]) -> dict:
    """Pareto front analysis on (tokens, quality)."""
    points = []
    for v in variants:
        rs = [r for r in runs if r.variant == v and r.kind == "wrong"
              and r.explanation_quality_score is not None and r.prompt_tokens is not None]
        if not rs:
            continue
        avg_tokens = mean(r.prompt_tokens + (r.completion_tokens or 0) for r in rs)
        avg_q = mean(r.explanation_quality_score for r in rs)
        points.append({"variant": v, "avg_tokens": avg_tokens, "avg_quality": avg_q})
    pareto = []
    for p in points:
        dominated = False
        for q in points:
            if q is p:
                continue
            if q["avg_tokens"] <= p["avg_tokens"] and q["avg_quality"] >= p["avg_quality"] and \
               (q["avg_tokens"] < p["avg_tokens"] or q["avg_quality"] > p["avg_quality"]):
                dominated = True
                break
        if not dominated:
            pareto.append(p["variant"])
    return {"points": points, "pareto_front": pareto}


def per_language(runs: list[Run], variants: list[str]) -> dict:
    out = {}
    for lang in ("java", "python"):
        out[lang] = {}
        for v in variants:
            qs = [r.code_quality for r in runs if r.variant == v and r.language == lang and r.kind == "wrong"]
            out[lang][v] = {"n": len(qs), "mean_quality": mean(qs) if qs else None}
    return out


# ─────────────────────────────────── PLOTS ──────────────────────────────────


def plot_boxplot(runs: list[Run], variants: list[str], out: Path) -> None:
    if not HAVE_MPL:
        return
    diagnoses = ["WA", "TLE", "RTE", "STUB", "STYLE", "SECURITY"]
    fig, axes = plt.subplots(1, len(diagnoses), figsize=(20, 4), sharey=True)
    for ax, d in zip(axes, diagnoses):
        data = []
        for v in variants:
            qs = [r.code_quality for r in runs
                  if r.variant == v and r.kind == "wrong" and r.expected_diagnosis == d]
            data.append(qs)
        ax.boxplot(data, labels=variants, showfliers=False)
        ax.set_title(d)
        ax.set_ylim(0, 100)
    fig.suptitle("Figure 1 — codeQuality by variant × diagnosis")
    fig.tight_layout()
    fig.savefig(out, dpi=120)
    plt.close(fig)


def plot_pareto(cq: dict, out: Path) -> None:
    if not HAVE_MPL or not cq["points"]:
        return
    fig, ax = plt.subplots(figsize=(8, 5))
    for p in cq["points"]:
        marker = "o" if p["variant"] in cq["pareto_front"] else "x"
        ax.scatter(p["avg_tokens"], p["avg_quality"], marker=marker, s=120, label=p["variant"])
        ax.annotate(p["variant"], (p["avg_tokens"], p["avg_quality"]), xytext=(5, 5), textcoords="offset points")
    ax.set_xlabel("avg tokens (prompt+completion)")
    ax.set_ylabel("avg explanation_quality_score (0..4)")
    ax.set_title("Figure 2 — cost-quality Pareto front")
    fig.tight_layout()
    fig.savefig(out, dpi=120)
    plt.close(fig)


def plot_forest(h1: dict, out: Path) -> None:
    if not HAVE_MPL:
        return
    eff = h1["metrics"].get("code_quality", {})
    pairs = eff.get("pairs", [])
    deltas = eff.get("cliffs_delta", [])
    if not pairs:
        return
    fig, ax = plt.subplots(figsize=(8, max(3, 0.4 * len(pairs))))
    y = list(range(len(pairs)))
    ax.scatter(deltas, y, s=80)
    ax.set_yticks(y); ax.set_yticklabels(pairs)
    ax.axvline(0, color="black", lw=1)
    ax.set_xlabel("Cliff's δ (codeQuality on STUB+SECURITY items)")
    ax.set_title("Figure 3 — pairwise effect sizes (forest plot)")
    fig.tight_layout()
    fig.savefig(out, dpi=120)
    plt.close(fig)


# ─────────────────────────────────── MAIN ───────────────────────────────────


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--results-root", default="experiment/results", type=Path)
    ap.add_argument("--gold-form", default="experiment/dataset/gold/gold_form.csv", type=Path)
    ap.add_argument("--tasks-dir", default="experiment/dataset/tasks", type=Path)
    ap.add_argument("--variants", default="b0,b1,b1f,b2,b3")
    ap.add_argument("--no-plots", action="store_true")
    args = ap.parse_args()

    variants = [v.strip() for v in args.variants.split(",") if v.strip()]
    out_dir = args.results_root / "analysis"
    out_dir.mkdir(parents=True, exist_ok=True)

    runs = load_runs(args.results_root, variants, args.tasks_dir)
    print(f"[analyze] loaded {len(runs)} run records across variants {variants}")
    if not runs:
        print("[analyze] no runs found — nothing to compute", file=sys.stderr)
        return

    h1 = h1_pairwise(runs, variants)
    h2 = h2_levene(runs)
    h3 = h3_gold_alignment(runs, variants, args.gold_form)
    h4 = h4_injection(runs, variants)
    lat = latency_bootstrap(runs, variants)
    cq = cost_quality(runs, variants)
    pl = per_language(runs, variants)

    (out_dir / "H1_pairwise.json").write_text(json.dumps(h1, indent=2, ensure_ascii=False))
    (out_dir / "H2_levene.json").write_text(json.dumps(h2, indent=2, ensure_ascii=False))
    (out_dir / "H3_gold_alignment.json").write_text(json.dumps(h3, indent=2, ensure_ascii=False))
    (out_dir / "H4_injection.json").write_text(json.dumps(h4, indent=2, ensure_ascii=False))
    (out_dir / "latency.json").write_text(json.dumps(lat, indent=2, ensure_ascii=False))
    (out_dir / "cost_quality.json").write_text(json.dumps(cq, indent=2, ensure_ascii=False))
    (out_dir / "per_language.json").write_text(json.dumps(pl, indent=2, ensure_ascii=False))

    if not args.no_plots and HAVE_MPL:
        plot_boxplot(runs, variants, out_dir / "fig1_quality_boxplot.png")
        plot_pareto(cq, out_dir / "fig2_pareto_cost.png")
        plot_forest(h1, out_dir / "fig3_forest_effects.png")

    print(f"[analyze] JSON + PNG written to {out_dir}")


if __name__ == "__main__":
    main()

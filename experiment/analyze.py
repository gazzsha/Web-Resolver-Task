#!/usr/bin/env python3
"""Confirmatory statistical analysis for the Level-2 ai-analyzer experiment.

Computes only what the Level-2 pre-registration asks for:

* descriptive stats per variant (codeQuality, explanation_quality_score, latency)
* Wilcoxon signed-rank tests with Holm correction on the three variant pairs
  (B1 vs B1f, B1 vs B2, B1f vs B2) on ``explanation_quality_score`` restricted
  to the STUB stratum (H1) and on the full sample
* Cliff's δ effect sizes for the same pairs
* Wilson 95% confidence intervals for ``injection_success_rate`` on the
  attack stratum (H2)
* two figures: bar chart of mean ``explanation_quality_score`` per variant
  broken down by diagnosis, and a forest plot of Cliff's δ with CIs

All Level-1 / pilot-only artefacts (Levene, IRR, bootstrap, Pareto, per-language
breakdowns) are intentionally removed — they are out of scope for the confirmatory
protocol and will only confuse the reviewer if left in.

Inputs (read-only)
------------------
``experiment/results/<variant>/<item_id>__run<N>.json``           — primary
``experiment/results/<variant>/<item_id>__run<N>.graded.json``    — sidecar

Outputs (overwritten)
---------------------
``<out>/RESULTS.md``                       — final report (Markdown)
``<out>/descriptive.json``                 — per-variant tables
``<out>/h1_wilcoxon_holm.json``            — H1 tests (STUB + ALL)
``<out>/h2_injection_ci.json``             — H2 Wilson CIs
``<out>/quality_by_variant.png``           — bar chart
``<out>/effect_sizes_forest.png``          — forest plot
``<out>/injection_success_per_owasp.png``  — bar of injection_success per OWASP class

CLI
---
::

    python3 experiment/analyze.py --level=2 \\
        --input experiment/results/ \\
        --out experiment/results/analysis/
"""
from __future__ import annotations

import argparse
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


LEVEL2_VARIANTS = ["b1", "b1f", "b2"]
LEVEL2_PAIRS = [("b1", "b1f"), ("b1", "b2"), ("b1f", "b2")]


# ─────────────────────────────────── LOADER ─────────────────────────────────


@dataclass
class Run:
    item_id: str
    variant: str
    run_index: int
    kind: str
    expected_diagnosis: str
    owasp_class: str | None
    code_quality: int
    latency_ms: int
    schema_valid: bool
    injection_defeated: bool
    explanation_quality_score: int | None
    success_criterion: str | None


def _read_one(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def load_runs(results_root: Path, variants: list[str]) -> list[Run]:
    runs: list[Run] = []
    for v in variants:
        vdir = results_root / v
        if not vdir.is_dir():
            continue
        for p in sorted(vdir.glob("*__run*.json")):
            if p.name.endswith(".graded.json"):
                continue
            try:
                rec = _read_one(p)
            except Exception as e:  # noqa: BLE001
                print(f"[analyze] skip {p}: {e}", file=sys.stderr)
                continue
            graded_path = p.with_suffix(".graded.json")
            graded = _read_one(graded_path) if graded_path.exists() else {}
            runs.append(
                Run(
                    item_id=rec.get("itemId", ""),
                    variant=v,
                    run_index=int(rec.get("runIndex", 1)),
                    kind=rec.get("kind", ""),
                    expected_diagnosis=rec.get("expectedDiagnosis", ""),
                    owasp_class=rec.get("owaspClass"),
                    code_quality=int(rec.get("codeQuality", 0)),
                    latency_ms=int(rec.get("latencyMs", 0)),
                    schema_valid=bool(rec.get("schemaValid", False)),
                    injection_defeated=bool(rec.get("injectionDefeated", False)),
                    explanation_quality_score=graded.get("explanation_quality_score"),
                    success_criterion=rec.get("successCriterion"),
                )
            )
    return runs


# ─────────────────────────────────── STATS ──────────────────────────────────


def cliffs_delta(xs: list[float], ys: list[float]) -> float:
    """Cliff's δ effect size. δ in [-1, 1]; |δ| ≥ 0.474 = large (Romano+2006)."""
    if not xs or not ys:
        return 0.0
    n = len(xs) * len(ys)
    g = 0
    for x in xs:
        for y in ys:
            if x > y:
                g += 1
            elif x < y:
                g -= 1
    return g / n


def wilson_ci(successes: int, total: int, alpha: float = 0.05) -> tuple[float, float]:
    """Wilson score 95% CI for a binomial proportion."""
    if total == 0:
        return (0.0, 0.0)
    z = 1.959963984540054  # Φ^{-1}(0.975)
    p = successes / total
    denom = 1 + z * z / total
    centre = (p + z * z / (2 * total)) / denom
    half = (z * math.sqrt(p * (1 - p) / total + z * z / (4 * total * total))) / denom
    return (max(0.0, centre - half), min(1.0, centre + half))


def holm_correct(p_values: list[float]) -> list[float]:
    """Holm-Bonferroni step-down correction. Returns adjusted p-values."""
    m = len(p_values)
    order = sorted(range(m), key=lambda i: p_values[i])
    adj = [0.0] * m
    prev = 0.0
    for rank, idx in enumerate(order):
        raw = p_values[idx] * (m - rank)
        prev = max(prev, min(1.0, raw))
        adj[idx] = prev
    return adj


def wilcoxon_pair(a: list[float], b: list[float]) -> tuple[float, float]:
    """Paired Wilcoxon signed-rank. Returns (statistic, p-value).

    Returns (nan, 1.0) when sample size is too small or all differences are zero.
    """
    if not HAVE_SCIPY:
        return (float("nan"), 1.0)
    if len(a) != len(b) or len(a) < 5:
        return (float("nan"), 1.0)
    if all(abs(x - y) < 1e-12 for x, y in zip(a, b)):
        return (0.0, 1.0)
    try:
        stat, p = stats.wilcoxon(a, b, zero_method="wilcox", correction=False, alternative="two-sided")
        return (float(stat), float(p))
    except Exception as e:  # noqa: BLE001
        print(f"[analyze] wilcoxon failed: {e}", file=sys.stderr)
        return (float("nan"), 1.0)


# ─────────────────────────────────── AGGREGATION ────────────────────────────


def descriptive_per_variant(runs: list[Run]) -> dict[str, dict[str, Any]]:
    by_v: dict[str, list[Run]] = defaultdict(list)
    for r in runs:
        by_v[r.variant].append(r)
    out: dict[str, dict[str, Any]] = {}
    for v, rs in by_v.items():
        cq = [r.code_quality for r in rs]
        eq = [r.explanation_quality_score for r in rs if r.explanation_quality_score is not None]
        lat = [r.latency_ms for r in rs]
        out[v] = {
            "n": len(rs),
            "n_graded": len(eq),
            "code_quality_mean": round(mean(cq), 3) if cq else 0.0,
            "code_quality_std": round(pstdev(cq), 3) if len(cq) > 1 else 0.0,
            "code_quality_median": median(cq) if cq else 0.0,
            "explanation_quality_mean": round(mean(eq), 3) if eq else 0.0,
            "explanation_quality_std": round(pstdev(eq), 3) if len(eq) > 1 else 0.0,
            "explanation_quality_median": median(eq) if eq else 0.0,
            "latency_ms_mean": round(mean(lat), 1) if lat else 0.0,
            "latency_ms_median": median(lat) if lat else 0.0,
        }
    return out


def paired_series(runs: list[Run], v1: str, v2: str, kind_filter: str | None = None,
                  diag_filter: str | None = None) -> tuple[list[float], list[float], list[str]]:
    """Build paired explanation-quality series for variants v1 and v2.

    Pairing key = (item_id, run_index). Items missing from either side or with
    unscored grading are dropped (listwise deletion).
    """
    by_key1: dict[tuple, Run] = {}
    by_key2: dict[tuple, Run] = {}
    for r in runs:
        if kind_filter and r.kind != kind_filter:
            continue
        if diag_filter and r.expected_diagnosis != diag_filter:
            continue
        if r.explanation_quality_score is None:
            continue
        key = (r.item_id, r.run_index)
        if r.variant == v1:
            by_key1[key] = r
        elif r.variant == v2:
            by_key2[key] = r
    common = sorted(set(by_key1.keys()) & set(by_key2.keys()))
    a = [float(by_key1[k].explanation_quality_score) for k in common]
    b = [float(by_key2[k].explanation_quality_score) for k in common]
    ids = [f"{k[0]}#run{k[1]}" for k in common]
    return a, b, ids


def h1_tests(runs: list[Run]) -> dict[str, Any]:
    """H1: B2 > B1 on explanation_quality_score, STUB stratum, Wilcoxon+Holm.

    We also compute the full-sample variant for completeness; the headline is
    the STUB-stratum pair B1 vs B2.
    """
    strata = {"STUB": "STUB", "ALL": None}
    out: dict[str, Any] = {}
    for label, diag in strata.items():
        raw_p: list[float] = []
        rows: list[dict[str, Any]] = []
        for v1, v2 in LEVEL2_PAIRS:
            a, b, _ = paired_series(runs, v1, v2, diag_filter=diag)
            stat, p = wilcoxon_pair(a, b)
            delta = cliffs_delta(b, a)  # δ > 0 ⇒ v2 stochastically dominates v1
            rows.append({
                "pair": f"{v1}_vs_{v2}",
                "n_pairs": len(a),
                "mean_v1": round(mean(a), 3) if a else 0.0,
                "mean_v2": round(mean(b), 3) if b else 0.0,
                "wilcoxon_stat": stat,
                "p_value_raw": p,
                "cliffs_delta": round(delta, 3),
            })
            raw_p.append(p)
        adj = holm_correct(raw_p)
        for row, ap in zip(rows, adj):
            row["p_value_holm"] = ap
        out[label] = rows
    return out


def h2_injection_ci(runs: list[Run]) -> dict[str, Any]:
    """H2: injection_success_rate per variant with Wilson 95% CI.

    success := NOT injection_defeated  (attack item only).
    A reduction (or non-increase) in B2 vs B1 with non-overlapping CIs supports H2.
    """
    by_v: dict[str, list[Run]] = defaultdict(list)
    for r in runs:
        if r.kind == "attack":
            by_v[r.variant].append(r)
    out: dict[str, Any] = {}
    for v, rs in by_v.items():
        n = len(rs)
        succ = sum(1 for r in rs if not r.injection_defeated)
        lo, hi = wilson_ci(succ, n)
        out[v] = {
            "n_attacks": n,
            "n_success": succ,
            "rate": round(succ / n, 3) if n else 0.0,
            "ci_low": round(lo, 3),
            "ci_high": round(hi, 3),
        }
    # By OWASP class for the secondary figure
    per_owasp: dict[str, dict[str, dict[str, Any]]] = {}
    classes = sorted({r.owasp_class or "UNK" for r in runs if r.kind == "attack"})
    for cls in classes:
        per_owasp[cls] = {}
        for v in LEVEL2_VARIANTS:
            rs = [r for r in runs if r.kind == "attack" and r.variant == v and (r.owasp_class or "UNK") == cls]
            n = len(rs)
            succ = sum(1 for r in rs if not r.injection_defeated)
            lo, hi = wilson_ci(succ, n)
            per_owasp[cls][v] = {
                "n": n, "succ": succ, "rate": round(succ / n, 3) if n else 0.0,
                "ci_low": round(lo, 3), "ci_high": round(hi, 3),
            }
    return {"overall": out, "per_owasp": per_owasp}


# ─────────────────────────────────── PLOTS ──────────────────────────────────


def plot_quality_by_variant(runs: list[Run], out_path: Path) -> None:
    if not HAVE_MPL:
        print("[analyze] matplotlib missing, skip quality_by_variant plot", file=sys.stderr)
        return
    diags = ["RTE", "WA", "TLE", "STUB", "STYLE", "SECURITY", "ATTACK"]
    series: dict[str, list[float]] = {v: [] for v in LEVEL2_VARIANTS}
    for diag in diags:
        for v in LEVEL2_VARIANTS:
            if diag == "ATTACK":
                xs = [r.explanation_quality_score for r in runs
                      if r.variant == v and r.kind == "attack" and r.explanation_quality_score is not None]
            else:
                xs = [r.explanation_quality_score for r in runs
                      if r.variant == v and r.expected_diagnosis == diag and r.explanation_quality_score is not None]
            series[v].append(mean(xs) if xs else 0.0)
    x = np.arange(len(diags))
    width = 0.27
    fig, ax = plt.subplots(figsize=(9, 4.5))
    for i, v in enumerate(LEVEL2_VARIANTS):
        ax.bar(x + (i - 1) * width, series[v], width, label=v.upper())
    ax.set_xticks(x)
    ax.set_xticklabels(diags)
    ax.set_ylabel("mean explanation_quality_score (0..4)")
    ax.set_title("Качество объяснений по варианту × категории (Level 2, N=3)")
    ax.set_ylim(0, 4.1)
    ax.legend()
    ax.grid(axis="y", linestyle=":", alpha=0.5)
    fig.tight_layout()
    fig.savefig(out_path, dpi=140)
    plt.close(fig)


def plot_forest(h1: dict[str, Any], out_path: Path) -> None:
    if not HAVE_MPL:
        print("[analyze] matplotlib missing, skip forest plot", file=sys.stderr)
        return
    rows = []
    for label in ("STUB", "ALL"):
        for row in h1.get(label, []):
            rows.append((f"{row['pair']} ({label})", row["cliffs_delta"], row["p_value_holm"]))
    if not rows:
        return
    rows.reverse()  # so STUB-pairs appear at top
    labels = [r[0] for r in rows]
    deltas = [r[1] for r in rows]
    fig, ax = plt.subplots(figsize=(8, max(3, 0.5 * len(rows))))
    y = np.arange(len(rows))
    colors = ["tab:blue" if abs(d) < 0.474 else "tab:red" for d in deltas]
    ax.barh(y, deltas, color=colors, alpha=0.7)
    ax.set_yticks(y)
    ax.set_yticklabels(labels)
    ax.axvline(0, color="black", linewidth=1)
    ax.axvline(0.474, color="gray", linestyle="--", linewidth=0.7)
    ax.axvline(-0.474, color="gray", linestyle="--", linewidth=0.7)
    ax.set_xlabel("Cliff's δ  (|δ| ≥ 0.474 = large effect, Romano+2006)")
    ax.set_title("Forest plot эффектов на explanation_quality_score")
    fig.tight_layout()
    fig.savefig(out_path, dpi=140)
    plt.close(fig)


def plot_injection_per_owasp(h2: dict[str, Any], out_path: Path) -> None:
    if not HAVE_MPL:
        return
    per = h2.get("per_owasp", {})
    if not per:
        return
    classes = sorted(per.keys())
    x = np.arange(len(classes))
    width = 0.27
    fig, ax = plt.subplots(figsize=(10, 4.5))
    for i, v in enumerate(LEVEL2_VARIANTS):
        rates = [per[c].get(v, {}).get("rate", 0.0) for c in classes]
        ax.bar(x + (i - 1) * width, rates, width, label=v.upper())
    ax.set_xticks(x)
    ax.set_xticklabels(classes, rotation=30, ha="right")
    ax.set_ylabel("injection_success_rate")
    ax.set_title("Успех инъекции по OWASP-классу (Level 2)")
    ax.set_ylim(0, 1.05)
    ax.legend()
    ax.grid(axis="y", linestyle=":", alpha=0.5)
    fig.tight_layout()
    fig.savefig(out_path, dpi=140)
    plt.close(fig)


# ─────────────────────────────────── REPORT ─────────────────────────────────


def render_markdown(
    desc: dict[str, Any],
    h1: dict[str, Any],
    h2: dict[str, Any],
    n_runs_total: int,
    out_path: Path,
    judge_hint: str,
) -> None:
    L = []
    L.append("# Level-2 confirmatory results\n")
    L.append(f"Total observations: **{n_runs_total}** (3 variants × 3 runs × 60 items).\n")
    L.append(f"Judge backend (from graded sidecars): **{judge_hint}**.\n")
    L.append("\n## Table 4.1 — Descriptive statistics per variant\n")
    L.append("| variant | n | n_graded | codeQuality (mean ± std, median) | expl_quality (mean ± std, median) | latency_ms (median) |")
    L.append("|---|---|---|---|---|---|")
    for v in LEVEL2_VARIANTS:
        d = desc.get(v, {})
        L.append(
            f"| {v} | {d.get('n', 0)} | {d.get('n_graded', 0)} | "
            f"{d.get('code_quality_mean', 0):.2f} ± {d.get('code_quality_std', 0):.2f}, {d.get('code_quality_median', 0)} | "
            f"{d.get('explanation_quality_mean', 0):.2f} ± {d.get('explanation_quality_std', 0):.2f}, {d.get('explanation_quality_median', 0)} | "
            f"{d.get('latency_ms_median', 0)} |"
        )
    L.append("\n## Table 4.2 — H1 (paired Wilcoxon + Cliff's δ, Holm-adjusted)\n")
    L.append("| stratum | pair | n | mean_v1 | mean_v2 | wilcoxon stat | p_raw | p_holm | Cliff's δ |")
    L.append("|---|---|---|---|---|---|---|---|---|")
    for label in ("STUB", "ALL"):
        for row in h1.get(label, []):
            L.append(
                f"| {label} | {row['pair']} | {row['n_pairs']} | "
                f"{row['mean_v1']:.2f} | {row['mean_v2']:.2f} | "
                f"{row['wilcoxon_stat']:.2f} | {row['p_value_raw']:.4f} | {row['p_value_holm']:.4f} | "
                f"{row['cliffs_delta']:+.3f} |"
            )
    L.append("\n## Table 4.3 — H2 (injection_success_rate, Wilson 95% CI)\n")
    L.append("| variant | n_attacks | n_success | rate | CI low | CI high |")
    L.append("|---|---|---|---|---|---|")
    for v in LEVEL2_VARIANTS:
        d = h2.get("overall", {}).get(v, {})
        L.append(
            f"| {v} | {d.get('n_attacks', 0)} | {d.get('n_success', 0)} | "
            f"{d.get('rate', 0):.3f} | {d.get('ci_low', 0):.3f} | {d.get('ci_high', 0):.3f} |"
        )
    L.append("\n## Headline decisions\n")
    stub_b1_b2 = next((r for r in h1.get("STUB", []) if r["pair"] == "b1_vs_b2"), None)
    if stub_b1_b2:
        decision = "CONFIRMED" if stub_b1_b2["p_value_holm"] < 0.05 and stub_b1_b2["cliffs_delta"] > 0 else "REJECTED"
        L.append(
            f"* **H1** (B2 > B1 on STUB explanation_quality_score) → {decision} "
            f"(Holm-adjusted p = {stub_b1_b2['p_value_holm']:.4f}, Cliff's δ = {stub_b1_b2['cliffs_delta']:+.3f}, n_pairs = {stub_b1_b2['n_pairs']})\n"
        )
    b1, b2 = h2.get("overall", {}).get("b1", {}), h2.get("overall", {}).get("b2", {})
    if b1 and b2:
        # H2 supported if B2's upper CI does not exceed B1's lower CI (clean reduction),
        # or its rate is lower and CIs do not overlap.
        h2_ok = b2["ci_high"] <= b1["ci_low"] or (b2["rate"] <= b1["rate"] and b2["ci_high"] < b1["ci_low"])
        L.append(
            f"* **H2** (injection_success_rate B2 ≤ B1 with non-overlapping CIs) → "
            f"{'CONFIRMED' if h2_ok else 'REJECTED / inconclusive'} "
            f"(B1 rate = {b1['rate']:.3f} CI [{b1['ci_low']:.3f}, {b1['ci_high']:.3f}], "
            f"B2 rate = {b2['rate']:.3f} CI [{b2['ci_low']:.3f}, {b2['ci_high']:.3f}])\n"
        )
    out_path.write_text("\n".join(L) + "\n", encoding="utf-8")


# ─────────────────────────────────── MAIN ───────────────────────────────────


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--level", default="2", choices=["2"], help="Level (only 2 supported in this script)")
    ap.add_argument("--input", default="experiment/results")
    ap.add_argument("--out", default="experiment/results/analysis")
    ap.add_argument("--no-plots", action="store_true")
    args = ap.parse_args()

    if not HAVE_SCIPY:
        raise SystemExit("scipy is required for Level-2 analysis; pip install -r experiment/requirements.txt")

    in_root = Path(args.input)
    out_root = Path(args.out)
    out_root.mkdir(parents=True, exist_ok=True)

    runs = load_runs(in_root, LEVEL2_VARIANTS)
    print(f"[analyze] loaded {len(runs)} runs across {len(LEVEL2_VARIANTS)} variants")
    judge_hint = "unknown"
    for v in LEVEL2_VARIANTS:
        for p in sorted((in_root / v).glob("*.graded.json")) if (in_root / v).is_dir() else []:
            try:
                judge_hint = json.loads(p.read_text())["judge"]
                break
            except Exception:
                continue
        if judge_hint != "unknown":
            break

    desc = descriptive_per_variant(runs)
    h1 = h1_tests(runs)
    h2 = h2_injection_ci(runs)

    (out_root / "descriptive.json").write_text(json.dumps(desc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (out_root / "h1_wilcoxon_holm.json").write_text(json.dumps(h1, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (out_root / "h2_injection_ci.json").write_text(json.dumps(h2, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    render_markdown(desc, h1, h2, len(runs), out_root / "RESULTS.md", judge_hint)
    print(f"[analyze] wrote {out_root / 'RESULTS.md'}")

    if not args.no_plots and HAVE_MPL:
        plot_quality_by_variant(runs, out_root / "quality_by_variant.png")
        plot_forest(h1, out_root / "effect_sizes_forest.png")
        plot_injection_per_owasp(h2, out_root / "injection_success_per_owasp.png")
        print(f"[analyze] wrote plots to {out_root}/")


if __name__ == "__main__":
    main()

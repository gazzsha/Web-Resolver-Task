#!/usr/bin/env python3
"""Inter-rater reliability (IRR) statistics for the expert gold-standard panel.

Reads ``experiment/dataset/gold/gold_form.csv`` filled in by experts E1, E2, E3
and computes:

* **Cohen's kappa** for every pair of experts on the discretised codeQuality
  bins (rounded to academic 5-bucket scale).
* **ICC(3,k)** — two-way mixed, average measures — on raw codeQuality 0..100.
  Acceptance threshold per ``PRE_REGISTRATION.md`` is ICC ≥ 0.70.
* **Fleiss' kappa** on the integer 0..4 rubric.
* Simple descriptive stats per expert (mean / median / sd / missing count).

Output: JSON to stdout + Markdown table to ``--out``.

Usage
-----
::

    python3 experiment/compute_irr.py \\
        --form experiment/dataset/gold/gold_form.csv \\
        --out experiment/dataset/gold/IRR_REPORT.md
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from statistics import mean, median, pstdev
from typing import Iterable


# ─────────────────────────────────── DATA ───────────────────────────────────


@dataclass
class ExpertRow:
    """One row of expert ratings for one item.

    Attributes
    ----------
    item_id : str
    codeQuality : list[int | None]
        Ordered [E1, E2, E3]; None for missing.
    rubric : list[int | None]
        Ordered [E1, E2, E3]; None for missing.
    """

    item_id: str
    codeQuality: list[int | None]
    rubric: list[int | None]


def _parse_int(s: str) -> int | None:
    s = (s or "").strip()
    if not s:
        return None
    try:
        return int(float(s))
    except ValueError:
        return None


def load_form(path: Path) -> list[ExpertRow]:
    """Read the CSV form, returning one ExpertRow per item."""
    rows: list[ExpertRow] = []
    with path.open(encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for r in reader:
            rows.append(
                ExpertRow(
                    item_id=r["item_id"],
                    codeQuality=[
                        _parse_int(r.get("expert_codeQuality_E1", "")),
                        _parse_int(r.get("expert_codeQuality_E2", "")),
                        _parse_int(r.get("expert_codeQuality_E3", "")),
                    ],
                    rubric=[
                        _parse_int(r.get("expert_rubric_score_E1", "")),
                        _parse_int(r.get("expert_rubric_score_E2", "")),
                        _parse_int(r.get("expert_rubric_score_E3", "")),
                    ],
                )
            )
    return rows


# ─────────────────────────────────── METRICS ────────────────────────────────


def discretise_quality(q: int | None) -> int | None:
    """Map raw codeQuality 0..100 → 5-bucket academic scale {0,1,2,3,4}.

    0..39 → 0 (неудовлетворительно без попытки)
    40..59 → 1 (неудовлетворительно с попыткой)
    60..74 → 2 (удовлетворительно)
    75..89 → 3 (хорошо)
    90..100 → 4 (отлично)
    """
    if q is None:
        return None
    if q < 40:
        return 0
    if q < 60:
        return 1
    if q < 75:
        return 2
    if q < 90:
        return 3
    return 4


def cohens_kappa(a: list[int | None], b: list[int | None]) -> float | None:
    """Cohen's kappa on aligned discrete labels (None values dropped pairwise)."""
    pairs = [(x, y) for x, y in zip(a, b) if x is not None and y is not None]
    if not pairs:
        return None
    labels = sorted({x for p in pairs for x in p})
    n = len(pairs)
    obs = sum(1 for x, y in pairs if x == y) / n
    marg_a = {lab: sum(1 for x, _ in pairs if x == lab) / n for lab in labels}
    marg_b = {lab: sum(1 for _, y in pairs if y == lab) / n for lab in labels}
    exp = sum(marg_a[l] * marg_b[l] for l in labels)
    if exp >= 1.0:
        return 1.0
    return (obs - exp) / (1 - exp)


def icc3k(matrix: list[list[float]]) -> float | None:
    """ICC(3,k) — two-way mixed, average measures, absolute agreement.

    matrix : list of lists, shape (n_items, n_raters), no missing values.

    Formula (Shrout & Fleiss 1979, McGraw & Wong 1996):
        ICC(3,k) = (MSR - MSE) / MSR
    where MSR = between-targets MS, MSE = residual MS from two-way ANOVA.

    Returns
    -------
    float or None
        None if not enough data.
    """
    n = len(matrix)
    if n < 2:
        return None
    k = len(matrix[0])
    if k < 2 or any(len(row) != k for row in matrix):
        return None
    grand = mean(x for row in matrix for x in row)
    row_means = [mean(row) for row in matrix]
    col_means = [mean(matrix[i][j] for i in range(n)) for j in range(k)]
    ss_total = sum((matrix[i][j] - grand) ** 2 for i in range(n) for j in range(k))
    ss_rows = k * sum((rm - grand) ** 2 for rm in row_means)
    ss_cols = n * sum((cm - grand) ** 2 for cm in col_means)
    ss_err = ss_total - ss_rows - ss_cols
    df_rows = n - 1
    df_err = (n - 1) * (k - 1)
    if df_rows == 0 or df_err == 0:
        return None
    msr = ss_rows / df_rows
    mse = ss_err / df_err
    if msr <= 0:
        return None
    return (msr - mse) / msr


def fleiss_kappa(matrix_counts: list[list[int]]) -> float | None:
    """Fleiss' kappa for k raters and m categories.

    Parameters
    ----------
    matrix_counts : list of lists
        Shape (n_items, m_categories). matrix_counts[i][j] = number of raters
        who assigned category j to item i. Row sums must equal the constant
        number-of-raters n.

    Returns
    -------
    float or None
    """
    if not matrix_counts:
        return None
    n_raters = sum(matrix_counts[0])
    if n_raters < 2:
        return None
    if any(sum(row) != n_raters for row in matrix_counts):
        return None
    N = len(matrix_counts)
    m = len(matrix_counts[0])
    p_j = [sum(matrix_counts[i][j] for i in range(N)) / (N * n_raters) for j in range(m)]
    P_i = [
        (sum(matrix_counts[i][j] ** 2 for j in range(m)) - n_raters) / (n_raters * (n_raters - 1))
        for i in range(N)
    ]
    P_bar = mean(P_i)
    P_e = sum(pj ** 2 for pj in p_j)
    if P_e >= 1.0:
        return 1.0
    return (P_bar - P_e) / (1 - P_e)


# ─────────────────────────────────── DRIVER ─────────────────────────────────


def compute_report(rows: list[ExpertRow]) -> dict:
    """Build the full IRR report as a JSON-serialisable dict."""
    n_experts = 3
    expert_names = ["E1", "E2", "E3"]

    # Descriptive stats per expert (drop missing)
    descriptives = {}
    for e_idx, name in enumerate(expert_names):
        vals = [r.codeQuality[e_idx] for r in rows if r.codeQuality[e_idx] is not None]
        descriptives[name] = {
            "n_filled": len(vals),
            "n_missing": len(rows) - len(vals),
            "mean": round(mean(vals), 2) if vals else None,
            "median": median(vals) if vals else None,
            "sd": round(pstdev(vals), 2) if len(vals) > 1 else None,
        }

    # Cohen's kappa on discretised codeQuality, pairwise
    cohens = {}
    for i in range(n_experts):
        for j in range(i + 1, n_experts):
            a = [discretise_quality(r.codeQuality[i]) for r in rows]
            b = [discretise_quality(r.codeQuality[j]) for r in rows]
            cohens[f"{expert_names[i]}-{expert_names[j]}"] = cohens_kappa(a, b)

    # ICC(3,k) — only on items with all three experts present
    full_quality = [
        [float(x) for x in r.codeQuality if x is not None]
        for r in rows
        if all(x is not None for x in r.codeQuality)
    ]
    icc_val = icc3k(full_quality) if full_quality else None

    # Fleiss' kappa on rubric (categories 0..4)
    full_rubric_items = [r.rubric for r in rows if all(x is not None for x in r.rubric)]
    fleiss = None
    if full_rubric_items:
        counts = []
        for row in full_rubric_items:
            cat_counts = [0] * 5
            for v in row:
                if v is not None and 0 <= v <= 4:
                    cat_counts[v] += 1
            counts.append(cat_counts)
        fleiss = fleiss_kappa(counts)

    icc_pass = (icc_val is not None and icc_val >= 0.70)
    return {
        "n_items": len(rows),
        "n_complete_quality": len(full_quality),
        "n_complete_rubric": len(full_rubric_items),
        "descriptives": descriptives,
        "cohens_kappa_codeQuality_bucketed": cohens,
        "icc3k_codeQuality": icc_val,
        "fleiss_kappa_rubric": fleiss,
        "passes_threshold_icc70": icc_pass,
    }


def format_markdown(report: dict) -> str:
    """Render the JSON report as a Markdown audit document."""
    lines = ["# Inter-rater reliability report", ""]
    lines.append(f"- n_items: {report['n_items']}")
    lines.append(f"- complete rows (codeQuality, all 3 experts): {report['n_complete_quality']}")
    lines.append(f"- complete rows (rubric,         all 3 experts): {report['n_complete_rubric']}")
    icc = report["icc3k_codeQuality"]
    lines.append(f"- **ICC(3,k) codeQuality** = {('%.3f' % icc) if icc is not None else 'n/a'} "
                 f"({'PASS ≥0.70' if report['passes_threshold_icc70'] else 'FAIL (<0.70)'})")
    fl = report["fleiss_kappa_rubric"]
    lines.append(f"- Fleiss κ rubric (0..4) = {('%.3f' % fl) if fl is not None else 'n/a'}")
    lines.append("")
    lines.append("## Cohen's κ on bucketed codeQuality (academic scale 0..4)")
    lines.append("")
    lines.append("| pair | κ |")
    lines.append("|------|----|")
    for pair, k in report["cohens_kappa_codeQuality_bucketed"].items():
        lines.append(f"| {pair} | {('%.3f' % k) if k is not None else 'n/a'} |")
    lines.append("")
    lines.append("## Per-expert descriptives")
    lines.append("")
    lines.append("| expert | n_filled | n_missing | mean | median | sd |")
    lines.append("|--------|----------|-----------|------|--------|----|")
    for name, d in report["descriptives"].items():
        lines.append(
            f"| {name} | {d['n_filled']} | {d['n_missing']} | "
            f"{d['mean']} | {d['median']} | {d['sd']} |"
        )
    return "\n".join(lines) + "\n"


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--form", required=True, type=Path)
    ap.add_argument("--out", type=Path, default=None)
    args = ap.parse_args()

    rows = load_form(args.form)
    report = compute_report(rows)
    print(json.dumps(report, indent=2, ensure_ascii=False))
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(format_markdown(report), encoding="utf-8")
        print(f"\n[irr] Markdown report → {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()

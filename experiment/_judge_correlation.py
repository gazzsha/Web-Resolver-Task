#!/usr/bin/env python3
"""Spearman correlation between Claude judge and GigaChat self-judge across 540 items."""
from __future__ import annotations
import json
from pathlib import Path
from scipy.stats import spearmanr, pearsonr

ROOT = Path(__file__).resolve().parent
RES = ROOT / "results"

claude_scores: list[int] = []
giga_scores: list[int] = []
for v in ("b1", "b1f", "b2"):
    for cp in sorted((RES / v).glob("*.graded.claude.json")):
        gp = cp.with_name(cp.name.replace(".graded.claude.json", ".graded.gigachat-self.json"))
        if not gp.exists():
            continue
        c = json.loads(cp.read_text())
        g = json.loads(gp.read_text())
        c_s = int(c.get("quality_score", -1))
        g_s = int(g.get("explanation_quality_score", -1))
        if c_s < 0 or g_s < 0:
            continue
        claude_scores.append(c_s)
        giga_scores.append(g_s)

print(f"n_paired = {len(claude_scores)}")
if len(claude_scores) >= 5:
    rho, p = spearmanr(claude_scores, giga_scores)
    pr, pp = pearsonr(claude_scores, giga_scores)
    # Confusion-like agreement
    from collections import Counter
    diffs = Counter()
    for c, g in zip(claude_scores, giga_scores):
        diffs[c - g] += 1
    print(f"spearman_rho = {rho:.3f}  (p={p:.3g})")
    print(f"pearson_r    = {pr:.3f}  (p={pp:.3g})")
    print(f"score_diff_distribution (claude - gigachat): {dict(sorted(diffs.items()))}")
    print(f"claude_mean = {sum(claude_scores)/len(claude_scores):.3f}")
    print(f"giga_mean   = {sum(giga_scores)/len(giga_scores):.3f}")
    agreement = sum(1 for c, g in zip(claude_scores, giga_scores) if c == g) / len(claude_scores)
    print(f"exact_agreement = {agreement:.3f}")

out = {
    "n_paired": len(claude_scores),
    "spearman_rho": rho,
    "pearson_r": pr,
    "claude_mean": sum(claude_scores) / len(claude_scores),
    "gigachat_self_mean": sum(giga_scores) / len(giga_scores),
    "exact_agreement": agreement,
}
(RES / "level2_judge_correlation.json").write_text(json.dumps(out, indent=2, ensure_ascii=False), encoding="utf-8")

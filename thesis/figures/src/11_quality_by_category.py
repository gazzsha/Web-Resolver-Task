#!/usr/bin/env python3
"""Render fig 4.2 — avg codeQuality by category x variant."""
import json
import pathlib
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[3]
RESULTS = ROOT / "experiment/results"
OUT = ROOT / "thesis/figures/11_quality_by_category.png"

VARIANTS = ["b1", "b1f", "b2"]
LABELS = {"b1": "B1 (zero-shot)", "b1f": "B1f (few-shot)", "b2": "B2 (AST + guard)"}
CATEGORIES = ["WA", "TLE", "RTE", "STUB", "STYLE", "SECURITY"]


def load(v):
    p = RESULTS / v / "_summary.json"
    if not p.exists():
        return {}
    data = json.loads(p.read_text(encoding="utf-8"))
    return data.get("byDiagnosis", {})


fig, ax = plt.subplots(figsize=(9.5, 5.0), dpi=200)

x = np.arange(len(CATEGORIES))
width = 0.27
colors = ["#777777", "#bbbbbb", "#222222"]

for i, v in enumerate(VARIANTS):
    diag = load(v)
    values = [float(diag.get(c, {}).get("avgCodeQuality", 0.0)) for c in CATEGORIES]
    bars = ax.bar(x + (i - 1) * width, values, width, label=LABELS[v], color=colors[i], edgecolor="black", linewidth=0.5)
    for bar, val in zip(bars, values):
        if val > 0:
            ax.text(bar.get_x() + bar.get_width() / 2, val + 1.0, f"{val:.0f}", ha="center", va="bottom", fontsize=8)

ax.set_xticks(x)
ax.set_xticklabels(CATEGORIES)
ax.set_ylabel("Средняя codeQuality")
ax.set_xlabel("Категория диагноза")
ax.set_title("Средняя оценка качества кода (codeQuality) по категориям и вариантам анализатора")
ax.set_ylim(0, 105)
ax.grid(axis="y", linestyle="--", linewidth=0.4, alpha=0.6)
ax.legend(loc="upper right", framealpha=0.9)

fig.tight_layout()
fig.savefig(OUT, dpi=300, bbox_inches="tight", facecolor="white")
print(f"Saved {OUT}")

#!/usr/bin/env python3
"""Render fig — П2: распределение 540 оценок качества объяснений (0..4),
выставленных независимым судьёй (Claude).

Источник: experiment/results/level2_judge_summary.json (поле score_distribution).
"""
import json
import pathlib
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[3]
SRC = ROOT / "experiment/results/level2_judge_summary.json"
OUT = ROOT / "thesis/figures/14_explanation_quality.png"

data = json.loads(SRC.read_text(encoding="utf-8"))
dist = data["score_distribution"]
total = data["total_judged"]

scores = [0, 1, 2, 3, 4]
counts = [dist.get(str(s), 0) for s in scores]
assert sum(counts) == total, f"{sum(counts)} != {total}"

fig, ax = plt.subplots(figsize=(8.5, 5.0), dpi=200)

x = np.arange(len(scores))
colors = ["#c0392b", "#e08e0b", "#d4c020", "#9bbb59", "#2c7a2c"]
bars = ax.bar(x, counts, width=0.65, color=colors, edgecolor="black", linewidth=0.5)
for bar, c in zip(bars, counts):
    pct = 100 * c / total
    ax.text(bar.get_x() + bar.get_width() / 2, c + 5,
            f"{c}\n({pct:.0f} %)", ha="center", va="bottom", fontsize=9)

ax.set_xticks(x)
ax.set_xticklabels([str(s) for s in scores])
ax.set_ylabel("Число оценок")
ax.set_xlabel("Балл качества объяснения (0–4: точность, локализация, действенность, отсутствие галлюцинаций)")
ax.set_title("Распределение оценок качества объяснений GigaChat\nпо суждению независимого эксперта (Claude), N = %d" % total)
ax.set_ylim(0, max(counts) * 1.18)
ax.grid(axis="y", linestyle="--", linewidth=0.4, alpha=0.6)

fig.tight_layout()
fig.savefig(OUT, dpi=300, bbox_inches="tight", facecolor="white")
print(f"Saved {OUT} (total={total}, counts={counts})")

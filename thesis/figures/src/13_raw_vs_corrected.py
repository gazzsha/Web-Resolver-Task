#!/usr/bin/env python3
"""Render fig — П1: распределение «сырых» оценок одиночной модели на нерабочем
коде и потолок после коррекции детерминированным слоем (clamp = 40).

Источник: experiment/results/level2_run.log (события
"GigaChat output contradicts sandbox: passed=A/B quality=X → clamp Y").
"""
import re
import pathlib
from collections import Counter
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[3]
LOG = ROOT / "experiment/results/level2_run.log"
OUT = ROOT / "thesis/figures/13_raw_vs_corrected.png"

PAT = re.compile(r"passed=(\d+)/(\d+)\s+quality=(\d+)\s+→\s+clamp\s+(\d+)")

qualities = []
clamp_target = None
for line in LOG.read_text(encoding="utf-8").splitlines():
    m = PAT.search(line)
    if m:
        qualities.append(int(m.group(3)))
        clamp_target = int(m.group(4))

n = len(qualities)
dist = Counter(qualities)
buckets = sorted(dist)  # 50..100
counts = [dist[b] for b in buckets]

fig, ax = plt.subplots(figsize=(9.5, 5.0), dpi=200)

x = np.arange(len(buckets))
bars = ax.bar(x, counts, width=0.7, color="#777777", edgecolor="black", linewidth=0.5,
              label="«Сырая» оценка одиночной модели (≥50, код не прошёл ни одного теста)")
for bar, c in zip(bars, counts):
    if c > 0:
        ax.text(bar.get_x() + bar.get_width() / 2, c + 2, str(c),
                ha="center", va="bottom", fontsize=8)

# Потолок после коррекции
ax.axhline(0, color="black", linewidth=0.6)
ceiling_x = -0.6
ax.annotate("", xy=(len(buckets) - 0.4, 0), xytext=(ceiling_x, 0))

# Горизонтальная линия «потолок после коррекции = 40» в координатах оси оценок.
# Используем вторую ось сверху как смысловую отметку.
ax2 = ax.twinx()
ax2.set_ylim(0, 105)
ax2.axhline(clamp_target, color="#c0392b", linestyle="--", linewidth=1.8,
            label=f"Потолок после коррекции = {clamp_target}")
ax2.set_ylabel("Шкала оценки качества (0–100)")
# Маркеры порога проходной оценки
ax2.axhline(50, color="#2c3e50", linestyle=":", linewidth=1.0,
            label="Порог проходной оценки = 50")

ax.set_xticks(x)
ax.set_xticklabels([str(b) for b in buckets])
ax.set_ylabel("Число случаев")
ax.set_xlabel("«Сырая» оценка качества, выставленная одиночной моделью")
ax.set_title("Завышение оценки одиночной моделью и его коррекция детерминированным слоем")
ax.grid(axis="y", linestyle="--", linewidth=0.4, alpha=0.6)

# Объединённая легенда
h1, l1 = ax.get_legend_handles_labels()
h2, l2 = ax2.get_legend_handles_labels()
ax.legend(h1 + h2, l1 + l2, loc="upper right", framealpha=0.9, fontsize=8)

ax.text(0.02, 0.95,
        f"Все {n} оценок ≥ 50 на коде с 0 пройденных тестов\nскорректированы до {clamp_target} (100 %)",
        transform=ax.transAxes, fontsize=9, va="top",
        bbox=dict(boxstyle="round", facecolor="#f5f5f5", edgecolor="#999999"))

fig.tight_layout()
fig.savefig(OUT, dpi=300, bbox_inches="tight", facecolor="white")
print(f"Saved {OUT} (n={n}, ceiling={clamp_target})")

#!/usr/bin/env python3
"""Render fig — П3: доля успешных prompt-инъекций по конфигурациям B1/B1f/B2
(все нули) с 95 % доверительным интервалом Вильсона.

Источник: experiment/results/SUMMARY.md (injection_success_rate, по 60 атак
на конфигурацию). ДИ Вильсона для 0/60 = [0,000; 0,060].
"""
import math
import pathlib
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[3]
OUT = ROOT / "thesis/figures/15_injection_defense.png"

VARIANTS = ["B1 (zero-shot)", "B1f (few-shot)", "B2 (AST + guard)"]
SUCCESS = [0, 0, 0]
N_ATTACKS = 60


def wilson(k, n, z=1.959963985):
    if n == 0:
        return (0.0, 0.0)
    phat = k / n
    denom = 1 + z * z / n
    centre = (phat + z * z / (2 * n)) / denom
    half = (z * math.sqrt(phat * (1 - phat) / n + z * z / (4 * n * n))) / denom
    return (max(0.0, centre - half), centre + half)

rates = [s / N_ATTACKS for s in SUCCESS]
cis = [wilson(s, N_ATTACKS) for s in SUCCESS]
hi = [c[1] for c in cis]
lo = [c[0] for c in cis]

fig, ax = plt.subplots(figsize=(8.5, 5.0), dpi=200)

x = np.arange(len(VARIANTS))
bars = ax.bar(x, rates, width=0.5, color="#2c7a2c", edgecolor="black", linewidth=0.5,
              label="Наблюдаемая доля успешных атак")

# Верхняя граница ДИ Вильсона как «усы»
yerr = np.array([[0] * len(x), hi])
ax.errorbar(x, rates, yerr=yerr, fmt="none", ecolor="#c0392b", capsize=8,
            elinewidth=1.6, label="95 % ДИ Вильсона (верхняя граница)")

for i in x:
    ax.text(i, hi[i] + 0.003, f"[{lo[i]:.3f}; {hi[i]:.3f}]",
            ha="center", va="bottom", fontsize=9, color="#c0392b")
    ax.text(i, 0.0008, f"0 из {N_ATTACKS}", ha="center", va="bottom",
            fontsize=9, color="white", fontweight="bold")

ax.set_xticks(x)
ax.set_xticklabels(VARIANTS)
ax.set_ylabel("Доля успешных prompt-инъекций")
ax.set_xlabel("Конфигурация анализатора")
ax.set_title("Устойчивость к prompt-инъекциям по конфигурациям анализатора")
ax.set_ylim(0, 0.075)
ax.grid(axis="y", linestyle="--", linewidth=0.4, alpha=0.6)
ax.legend(loc="upper right", framealpha=0.9, fontsize=9)

ax.annotate("Ни одной успешной атаки (0 из 60)",
            xy=(1, 0.0), xytext=(1, 0.045),
            ha="center", fontsize=11, fontweight="bold", color="#2c7a2c",
            bbox=dict(boxstyle="round", facecolor="#eafaea", edgecolor="#2c7a2c"))

fig.tight_layout()
fig.savefig(OUT, dpi=300, bbox_inches="tight", facecolor="white")
print(f"Saved {OUT} (rates={rates}, CI_upper={[round(h,4) for h in hi]})")

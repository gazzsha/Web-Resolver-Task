"""
Восстановленный скрипт построения гистограммы качества по категориям.

Источник числовых данных — `experiment/results/SUMMARY.md`, таблица по диагнозам
codeQuality avg (n / avg_quality) для трёх вариантов анализатора.

Запуск (из thesis/figures):
    python3 src/11_quality_by_category.py
Выход: 11_quality_by_category.png в figures/.
"""

import matplotlib.pyplot as plt
import numpy as np
from pathlib import Path

CATEGORIES = ["RTE", "SECURITY", "STUB", "STYLE", "TLE", "WA"]
B1  = [38.6, 52.8, 35.2, 88.8, 40.0, 40.0]
B1F = [37.6, 45.8, 20.5, 87.9, 40.0, 37.1]
B2  = [40.0, 53.3, 38.6, 85.0, 40.0, 40.0]

x = np.arange(len(CATEGORIES))
width = 0.27

fig, ax = plt.subplots(figsize=(10, 5.5), dpi=150)
bars1 = ax.bar(x - width, B1,  width, label="B1 — ZERO_SHOT",  color="#9CA3AF", edgecolor="#374151")
bars2 = ax.bar(x,         B1F, width, label="B1f — FEW_SHOT",  color="#60A5FA", edgecolor="#1E40AF")
bars3 = ax.bar(x + width, B2,  width, label="B2 — AST-HYBRID", color="#34D399", edgecolor="#065F46")

ax.set_xlabel("Категория диагноза", fontsize=11)
ax.set_ylabel("Средняя оценка codeQuality (по шкале 0–100)", fontsize=11)
ax.set_title("Средняя оценка качества по категориям дефектов\nи вариантам анализатора (n=180 на вариант)", fontsize=12)
ax.set_xticks(x)
ax.set_xticklabels(CATEGORIES)
ax.set_ylim(0, 100)
ax.legend(loc="upper left", framealpha=0.95)
ax.grid(axis="y", alpha=0.3, linestyle="--")

for bars in (bars1, bars2, bars3):
    for b in bars:
        h = b.get_height()
        ax.annotate(f"{h:.1f}", xy=(b.get_x() + b.get_width() / 2, h),
                    xytext=(0, 3), textcoords="offset points",
                    ha="center", va="bottom", fontsize=8, color="#374151")

plt.tight_layout()
out = Path(__file__).resolve().parent.parent / "11_quality_by_category.png"
plt.savefig(out, dpi=150, bbox_inches="tight")
print(f"saved: {out}")

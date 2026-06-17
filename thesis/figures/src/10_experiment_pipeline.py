import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Polygon

NAVY="#1F3864"; BLUE="#2E5C9E"; CARD="#EEF3FB"; LINE="#C9D6EC"; GRAY="#333333"; MUTE="#7A7A7A"; WHITE="#FFFFFF"

fig, ax = plt.subplots(figsize=(14, 3.9))
ax.set_xlim(0, 16); ax.set_ylim(0, 4.0); ax.axis("off")

def rbox(cx, cy, w, h, text, fill, fg=GRAY, fs=11, bold=False):
    ax.add_patch(FancyBboxPatch((cx-w/2, cy-h/2), w, h, boxstyle="round,pad=0.02,rounding_size=0.08",
                                linewidth=1.2, edgecolor=(fill if fill!=CARD else LINE), facecolor=fill, zorder=2))
    ax.text(cx, cy, text, ha="center", va="center", fontsize=fs, color=fg, fontweight=("bold" if bold else "normal"), zorder=3)

def para(cx, cy, w, h, text, fill, fg=GRAY, fs=10.5):
    sk=0.28
    ax.add_patch(Polygon([(cx-w/2+sk,cy-h/2),(cx+w/2,cy-h/2),(cx+w/2-sk,cy+h/2),(cx-w/2,cy+h/2)],
                         closed=True, linewidth=1.2, edgecolor=LINE, facecolor=fill, zorder=2))
    ax.text(cx, cy, text, ha="center", va="center", fontsize=fs, color=fg, zorder=3)

def arr(x0,y0,x1,y1):
    ax.add_patch(FancyArrowPatch((x0,y0),(x1,y1), arrowstyle="-|>", mutation_scale=13, linewidth=1.4, color=MUTE, zorder=1))

para(1.55, 2.0, 2.9, 1.3, "Датасет: 250 элементов\n90 ошибок · 90 дефектов\n40 атак · 30 корректных", CARD)
arr(3.05, 2.0, 3.75, 2.0)
rbox(4.55, 2.0, 1.5, 0.7, "Runner.kt", CARD)
arr(5.32, 2.0, 6.0, 2.0)
rbox(7.15, 2.0, 2.3, 0.95, "Синтетический\nsandbox-вердикт\n(WA/TLE/RTE/STUB/…)", CARD, fs=9.5)
# две конфигурации
arr(8.32, 2.3, 9.1, 3.0)
arr(8.32, 1.7, 9.1, 1.0)
rbox(10.4, 3.05, 2.7, 0.85, "B1: обычный промпт\nGigaChat, без AST", CARD, fs=10)
rbox(10.4, 0.95, 2.7, 0.85, "B2: AstHybridAnalyzer\nGigaChat + AST + verdict-cap", BLUE, WHITE, fs=10, bold=True)
arr(11.78, 3.0, 12.5, 2.25)
arr(11.78, 1.0, 12.5, 1.75)
rbox(13.25, 2.0, 1.5, 0.8, "Metrics\nAggregator", CARD, fs=10)
arr(14.02, 2.0, 14.55, 2.0)
para(15.25, 2.0, 1.9, 1.4, "Метрики:\nкачество,\nатаки,\nлатентность", CARD, fs=9.5)

plt.tight_layout()
plt.savefig("/Users/aidar/aidar_diplom/thesis/figures/10_experiment_pipeline.png", dpi=200, bbox_inches="tight")
print("saved 10_experiment_pipeline.png")

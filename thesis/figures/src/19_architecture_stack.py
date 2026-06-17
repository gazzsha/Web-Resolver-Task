import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

NAVY="#1F3864"; BLUE="#2E5C9E"; CARD="#EEF3FB"; LINE="#C9D6EC"; GRAY="#333333"; MUTE="#7A7A7A"; WHITE="#FFFFFF"
SAND="#3A6B45"; AICOL="#8A4B9E"; DB="#0B6E6E"; KAFKA="#B5651D"

fig, ax = plt.subplots(figsize=(13.0, 7.3))
ax.set_xlim(0, 13); ax.set_ylim(0, 7.3); ax.axis("off")

def box(cx, cy, w, h, name, tech, fill, fg=WHITE):
    ax.add_patch(FancyBboxPatch((cx-w/2, cy-h/2), w, h, boxstyle="round,pad=0.02,rounding_size=0.10",
                                linewidth=1.3, edgecolor=fill, facecolor=fill, zorder=2))
    ny = cy + (h*0.18 if tech else 0)
    ax.text(cx, ny, name, ha="center", va="center", fontsize=13, fontweight="bold", color=fg, zorder=3)
    if tech:
        sub = "#D7E2F2" if fill in (NAVY, BLUE) else "#EAEAEA"
        ax.text(cx, cy-h*0.22, tech, ha="center", va="center", fontsize=9.2, color=sub, zorder=3)

def varrow(x, y0, y1, label=None):
    ax.add_patch(FancyArrowPatch((x, y0), (x, y1), arrowstyle="-|>", mutation_scale=15, linewidth=1.6, color=MUTE, zorder=1))
    if label: ax.text(x+0.2, (y0+y1)/2, label, ha="left", va="center", fontsize=9, color=MUTE, style="italic")

CX, W = 6.3, 8.4
# поток (архитектура — главный фокус)
box(CX, 6.85, 3.4, 0.55, "Браузер студента", None, CARD, GRAY)
varrow(CX, 6.57, 6.27, "HTTPS")
box(CX, 5.85, W, 0.78, "Фронтенд — SPA", "React · TypeScript · Vite · Monaco", BLUE)
varrow(CX, 5.46, 5.16, "REST · OpenAPI")
box(CX, 4.74, W, 0.78, "task-resolver — приём запросов и REST API", "Spring Boot · Kotlin · JDK 21", NAVY)
varrow(CX, 4.35, 4.05, "событие (Kafka)")
box(CX, 3.63, W, 0.74, "Apache Kafka — асинхронная очередь", "развязка приёма и тяжёлой обработки", KAFKA)
varrow(CX, 3.26, 2.96)
box(CX, 2.54, W, 0.74, "worker — фоновая оркестрация проверки", "запуск тестов · вызов анализатора", NAVY)
# две ветви
box(CX-2.15, 1.40, 4.0, 0.86, "sandbox", "Docker · hardening", SAND)
box(CX+2.15, 1.40, 4.0, 0.86, "ai-analyzer", "AstHybrid + GigaChat", AICOL)
ax.add_patch(FancyArrowPatch((CX, 2.17), (CX-2.0, 1.83), arrowstyle="-|>", mutation_scale=13, linewidth=1.4, color=MUTE))
ax.add_patch(FancyArrowPatch((CX, 2.17), (CX+2.0, 1.83), arrowstyle="-|>", mutation_scale=13, linewidth=1.4, color=MUTE))
box(CX, 0.55, 4.6, 0.6, "PostgreSQL — задачи · решения · анализ", None, DB)
ax.add_patch(FancyArrowPatch((CX-2.15, 0.97), (CX-0.6, 0.78), arrowstyle="-|>", mutation_scale=12, linewidth=1.3, color=MUTE))
ax.add_patch(FancyArrowPatch((CX+2.15, 0.97), (CX+0.6, 0.78), arrowstyle="-|>", mutation_scale=12, linewidth=1.3, color=MUTE))

# наблюдаемость — сбоку, ненавязчиво
ax.add_patch(FancyBboxPatch((10.85, 3.05), 1.95, 1.25, boxstyle="round,pad=0.02,rounding_size=0.08",
                            linewidth=1.1, edgecolor=LINE, facecolor="#F5F7FB", zorder=2))
ax.text(11.82, 3.95, "Наблюдаемость", ha="center", fontsize=9.5, fontweight="bold", color=NAVY)
ax.text(11.82, 3.55, "Prometheus", ha="center", fontsize=8.6, color=MUTE)
ax.text(11.82, 3.28, "Grafana", ha="center", fontsize=8.6, color=MUTE)
ax.add_patch(FancyArrowPatch((10.85, 3.6), (CX+W/2, 3.0), arrowstyle="-", linestyle=(0,(3,3)), linewidth=1.0, color=LINE, zorder=1))

# тонкая строка стека снизу (минимум внимания)
ax.text(6.5, 0.08, "Стек: Kotlin · Spring Boot · React · TypeScript · Apache Kafka · Docker · PostgreSQL · GigaChat · JavaParser",
        ha="center", va="center", fontsize=8.4, color=MUTE)

plt.tight_layout()
plt.savefig("/Users/aidar/aidar_diplom/thesis/figures/19_architecture_stack.png", dpi=200, bbox_inches="tight")
print("saved 19_architecture_stack.png")

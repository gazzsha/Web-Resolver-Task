import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
raw={30:5,40:3,50:58,60:22,65:13,70:8,75:40,85:5,90:2,95:2}
xs=sorted(raw); ys=[raw[x] for x in xs]
fig,ax=plt.subplots(figsize=(8,4.5))
bars=ax.bar([str(x) for x in xs], ys, color='#c0504d', edgecolor='black')
ax.axhline(0)
ax.set_xlabel('«Сырая» оценка модели неработающему коду (0–100)')
ax.set_ylabel('Число случаев')
ax.set_title('Завышение оценки моделью и его исправление анализатором\n(150 случаев; все приведены к уровню ≤ 20 по результату тестов)')
ax.axvspan(-0.5, 1.5, color='#9bbb59', alpha=0.15)
ax.text(0.5,0.92,'порог «проходной» оценки ≥ 50 — справа от красной линии',
        transform=ax.transAxes, ha='center', fontsize=9, color='#555')
for b,y in zip(bars,ys):
    ax.text(b.get_x()+b.get_width()/2, y+0.3, str(y), ha='center', fontsize=9)
plt.tight_layout(); plt.savefig('thesis/figures/13_b2_correction.png', dpi=300); print('saved 13')

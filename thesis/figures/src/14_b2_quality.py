import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
dist={0:5,1:7,2:13,3:25,4:169}
N=sum(dist.values())
xs=list(dist); ys=[dist[x] for x in xs]
fig,ax=plt.subplots(figsize=(7,4.5))
bars=ax.bar([str(x) for x in xs], ys, color='#4f81bd', edgecolor='black')
ax.set_xlabel('Оценка корректности объяснения (сумма по 4 критериям, 0–4)')
ax.set_ylabel(f'Число объяснений (из {N})')
ax.set_title('Корректность объяснений AstHybridAnalyzer\nпо оценке независимого эксперта Claude (среднее 3,58; 77 % — максимум)')
for b,y in zip(bars,ys):
    if y: ax.text(b.get_x()+b.get_width()/2, y+0.4, f'{y}\n({y/N*100:.0f} %)', ha='center', fontsize=9)
plt.tight_layout(); plt.savefig('thesis/figures/14_b2_quality.png', dpi=300); print('saved 14')

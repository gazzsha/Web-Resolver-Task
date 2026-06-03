import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
dist={0:0,1:0,2:3,3:10,4:47}
xs=list(dist); ys=[dist[x] for x in xs]
fig,ax=plt.subplots(figsize=(7,4.5))
bars=ax.bar([str(x) for x in xs], ys, color='#4f81bd', edgecolor='black')
ax.set_xlabel('Оценка корректности объяснения (сумма по 4 критериям, 0–4)')
ax.set_ylabel('Число объяснений (из 60)')
ax.set_title('Корректность объяснений AstHybridAnalyzer\nпо оценке независимого эксперта Claude (среднее 3,73; 78 % — максимум)')
for b,y in zip(bars,ys):
    if y: ax.text(b.get_x()+b.get_width()/2, y+0.4, f'{y}\n({y/60*100:.0f} %)', ha='center', fontsize=9)
plt.tight_layout(); plt.savefig('thesis/figures/14_b2_quality.png', dpi=300); print('saved 14')

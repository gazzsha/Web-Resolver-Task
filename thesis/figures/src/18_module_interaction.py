import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch
plt.rcParams['font.family']='DejaVu Sans'

fig,ax=plt.subplots(figsize=(13,6.8)); ax.set_xlim(0,15); ax.set_ylim(0,7); ax.axis('off')

COL={'client':'#ffffff','svc':'#eeeeee','infra':'#e9e3c8','ext':'#dfe9d4'}
boxes={
 'FE':   (1.4,3.3,'Браузер студента\n(React)','client'),
 'TR':   (4.3,3.3,'task-resolver\n(приём REST,\nфиксация)','svc'),
 'KAFKA':(7.2,3.3,'Kafka\ntask-execution /\ntask-results','infra'),
 'WK':   (10.0,3.3,'worker\n(оркестрация\nконвейера)','svc'),
 'SBX':  (12.7,5.0,'sandbox\n(изолированное\nисполнение)','svc'),
 'AI':   (12.7,3.3,'ai-analyzer\n(нейро-символьный\nанализ)','svc'),
 'GIGA': (12.7,1.5,'GigaChat API','ext'),
 'DB':   (4.3,1.2,'PostgreSQL','infra'),
}
W,H=2.0,1.0
cx={}
for k,(x,y,lbl,cls) in boxes.items():
    ax.add_patch(FancyBboxPatch((x-W/2,y-H/2),W,H,boxstyle="round,pad=0.06,rounding_size=0.12",
                                fc=COL[cls],ec='black',lw=1.6))
    ax.text(x,y,lbl,ha='center',va='center',fontsize=8.5)
    cx[k]=(x,y)

def arrow(a,b,label,rad=0.0,dy_a=0,dx_a=0,dx_b=0,dy_b=0,lx=0,ly=0,color='#222'):
    (x1,y1)=cx[a]; (x2,y2)=cx[b]
    x1+=dx_a; y1+=dy_a; x2+=dx_b; y2+=dy_b
    ax.annotate("",xy=(x2,y2),xytext=(x1,y1),
        arrowprops=dict(arrowstyle="-|>",color=color,lw=1.6,
                        connectionstyle=f"arc3,rad={rad}",shrinkA=14,shrinkB=14))
    ax.text((x1+x2)/2+lx,(y1+y2)/2+ly,label,fontsize=7.5,color=color,ha='center',
            bbox=dict(boxstyle="round,pad=0.12",fc='white',ec='none',alpha=0.9))

# прямой путь
arrow('FE','TR','1. отправка решения',rad=0.22,ly=0.55)
arrow('TR','KAFKA','2. сообщение',rad=0.22,ly=0.55)
arrow('KAFKA','WK','3. приём',rad=0.22,ly=0.55)
arrow('WK','SBX','4. прогон\nтестов',rad=0.0,lx=-0.2)
arrow('WK','AI','5. запрос\nанализа',rad=0.0,ly=0.2)
arrow('AI','GIGA','6. разбор',rad=0.0,lx=0.6)
# обратный путь (ниже, другим цветом)
arrow('WK','KAFKA','7. результат',rad=0.22,ly=-0.55,color='#777')
arrow('KAFKA','TR','8. готовый результат',rad=0.22,ly=-0.6,color='#777')
arrow('TR','DB','9. сохранение',rad=0.0,lx=0.9,color='#333')
arrow('FE','TR','10. опрос статуса',rad=-0.5,ly=-0.7,color='#777')

ax.set_title('Взаимодействие модулей при обработке решения',fontsize=11,pad=8)
plt.tight_layout(); plt.savefig('thesis/figures/18_module_interaction.png',dpi=300,bbox_inches='tight')
print('saved')

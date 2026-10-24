"""Create portable CSV/JSON and an offline, filterable chart report from actual rounds."""
from pathlib import Path
import argparse,collections,csv,html,json,math,statistics
p=argparse.ArgumentParser();p.add_argument('--input',default='verification/local/experience-300');a=p.parse_args();root=Path(__file__).resolve().parents[1];out=root/a.input
rows=json.loads((out/'rounds.json').read_text());environment=json.loads((out/'environment.json').read_text())
assert len({r['round'] for r in rows})==len(rows)
def percentile(values,q):
 values=sorted(values)
 if not values:return None
 i=(len(values)-1)*q;f=math.floor(i);c=math.ceil(i);return values[f]+(values[c]-values[f])*(i-f)
def stats(values):return {'count':len(values),'median':percentile(values,.5),'p95':percentile(values,.95),'max':max(values) if values else None}
summary={'rounds':len(rows),'passed':sum(r.get('passed',False) for r in rows),'failed':sum(not r.get('passed',False) for r in rows),'cold':stats([r['openMs'] for r in rows if r.get('launchKind')=='cold' and 'openMs' in r]),'warm':stats([r['openMs'] for r in rows if r.get('launchKind')=='warm' and 'openMs' in r]),'pssMiB':stats([r['after']['pssKiB']/1024 for r in rows if 'pssKiB' in r.get('after',{})]),'scenarios':{}}
for name in dict.fromkeys(r['scenario'] for r in rows):
 group=[r for r in rows if r['scenario']==name];summary['scenarios'][name]={'rounds':len(group),'passed':sum(r.get('passed',False) for r in group),'actionsMs':stats([r['actionMs'] for r in group if 'actionMs'in r])}
events='\n'.join(p.read_text() for p in out.glob('batch-*/events.txt'))
summary['crashEvents']=sum('am_crash' in l for l in events.splitlines());summary['anrEvents']=sum('am_anr' in l for l in events.splitlines())
(out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
fields=['round','launchKind','scenario','passed','openMs','actionMs','totalMs','pssKiB','javaHeapBytes','station','volume','tempo','language','playerIdentity','width','height','error','preview']
with (out/'rounds.csv').open('w',newline='',encoding='utf-8-sig') as f:
 writer=csv.DictWriter(f,fieldnames=fields);writer.writeheader()
 for r in rows:writer.writerow({k:r.get(k,r.get('after',{}).get(k,'')) for k in fields})
colors={'cold':'#e3a05e','warm':'#70bfd2'}
def plot(title,series,ylabel):
 width,height=1160,300;l,t,rr,b=70,40,25,45;w=width-l-rr;h=height-t-b
 points=[v for values,_,_ in series for _,v in values];top=max(points or [1])*1.12 or 1
 svg=[f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" role="img" aria-label="{title}"><rect width="100%" height="100%" fill="#152024"/><g font-family="sans-serif" fill="#d9e2df" font-size="13"><text x="20" y="24" font-size="18">{title}</text>']
 for i in range(5):
  v=top*i/4;y=t+h-h*i/4;svg.append(f'<path d="M{l},{y:.1f}H{l+w}" stroke="#324044"/><text x="{l-9}" y="{y+4:.1f}" text-anchor="end">{v:.0f}</text>')
 for values,color,name in series:
  path=' '.join(f'{"M" if i==0 else "L"}{l+(n-1)*w/max(len(rows)-1,1):.1f},{t+h-v*h/top:.1f}' for i,(n,v) in enumerate(values))
  svg.append(f'<path d="{path}" stroke="{color}" stroke-width="1.4" fill="none"/>')
  for n,v in values:svg.append(f'<circle cx="{l+(n-1)*w/max(len(rows)-1,1):.1f}" cy="{t+h-v*h/top:.1f}" r="2.6" fill="{color}"><title>Round {n}: {v:.1f} {ylabel}</title></circle>')
 for n in (1,50,100,150,200,250,len(rows)):
  if n<=len(rows):svg.append(f'<text x="{l+(n-1)*w/max(len(rows)-1,1):.1f}" y="{height-20}" text-anchor="middle">{n}</text>')
 svg.append(f'<text x="{width-30}" y="{height-4}" text-anchor="end">Round / {ylabel}</text>')
 for i,(_,color,name) in enumerate(series):svg.append(f'<text x="{width-300+i*130}" y="24" fill="{color}">{name}</text>')
 svg.append('</g></svg>');return ''.join(svg)
launch=plot('Launch to interactive committed frame',[( [(r['round'],r['openMs']) for r in rows if r.get('launchKind')==kind and 'openMs'in r],colors[kind],kind) for kind in colors],'ms')
memory=plot('Observed app memory at round completion',[([(r['round'],r['after']['pssKiB']/1024) for r in rows if 'pssKiB'in r.get('after',{})],'#a0c68d','PSS MiB'), ([(r['round'],r['after']['javaHeapBytes']/1024**2) for r in rows if 'javaHeapBytes'in r.get('after',{})],'#bda5de','Java MiB')],'MiB')
actions=plot('Action sequence and condition waits',[([(r['round'],r['actionMs']) for r in rows if 'actionMs'in r],'#70bfd2','Actions')],'ms')
charts=out/'charts';charts.mkdir(exist_ok=True)
for name,data in [('launch',launch),('memory',memory),('actions',actions)]: (charts/(name+'.svg')).write_text(data)
scenarioRows=''.join(f'<tr><td>{html.escape(k)}</td><td>{v["passed"]}/{v["rounds"]}</td><td>{v["actionsMs"]["median"] or 0:.0f}</td><td>{v["actionsMs"]["p95"] or 0:.0f}</td></tr>' for k,v in summary['scenarios'].items())
data=json.dumps(rows,ensure_ascii=False).replace('</','<\\/')
style='body{font:15px system-ui;background:#0b1215;color:#d9e2df;margin:28px auto;max-width:1200px;padding:0 22px}h1{font-size:32px}h2{margin-top:32px}p{line-height:1.7;color:#b7c6c3}svg{width:100%;margin:10px 0}a{color:#80c7df}table{width:100%;border-collapse:collapse;font-size:13px}td,th{padding:9px;text-align:left;border-bottom:1px solid #324044}th{color:#80c7df}.cards{display:flex;gap:16px;flex-wrap:wrap}.card{padding:20px;background:#152024;min-width:170px;border-radius:8px}.value{font-size:30px}select{padding:10px;background:#203137;color:#d9e2df;border:1px solid #41545a;margin:5px}.pass{color:#9dc78d}.fail{color:#f19b81}details{margin:10px 0;padding:12px;background:#152024}pre{white-space:pre-wrap;word-break:break-word;font-size:12px}img{max-width:100%;height:auto}'
body=f'''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>Aether 300 轮体验测试</title><style>{style}</style><h1>漫频 Aether · {len(rows)} 轮打开与使用测试</h1><p>Android API 36 ARM64 模拟器 · 3408×2272 · 实际非 debug APK · 冷启动与前台恢复分列。此处是自动交互和状态验证，不能替代平板实机触感、听感或主观体验评价。</p><div class="cards"><div class="card">已执行<div class="value">{len(rows)}</div></div><div class="card">通过 / 失败<div class="value">{summary['passed']} / {summary['failed']}</div></div><div class="card">崩溃 / ANR 事件<div class="value">{summary['crashEvents']} / {summary['anrEvents']}</div></div><div class="card">冷 / 热返回<div class="value">{summary['cold']['count']} / {summary['warm']['count']}</div></div></div><h2>时间与资源图表</h2><p>openMs 从 Activity 启动请求计至界面可交互且 GPU 已提交一帧；不含 instrumentation 进程引导。冷启动包含资源准备、墨水刷新及验收等待。actionMs 包含整段操作、状态等待与提交，不能解释为单次触摸延迟或 FPS。PSS 为该时刻进程内存，不是泄漏证明。</p>{launch}{actions}{memory}<h2>场景覆盖</h2><table><thead><tr><th>场景</th><th>通过 / 执行</th><th>操作序列中位数 ms</th><th>P95 ms</th></tr></thead><tbody>{scenarioRows}</tbody></table><h2>逐轮原始记录</h2><p><a href="rounds.csv">CSV</a> · <a href="rounds.json">完整 JSON</a> · <a href="summary.json">汇总统计</a> · <a href="environment.json">环境与方法</a>。每轮均有 852×568 派生预览，冷启动及失败轮次保留 3408×2272 原图。</p><select id="status"><option value="all">全部结果</option><option value="fail">仅失败</option><option value="pass">仅通过</option></select><select id="scene"><option value="all">全部场景</option></select><div id="records"></div><script>const rows={data};const scene=document.querySelector('#scene');for(const s of new Set(rows.map(r=>r.scenario))){{const o=document.createElement('option');o.value=s;o.textContent=s;scene.append(o)}}function render(){{const status=document.querySelector('#status').value;const host=document.querySelector('#records');host.replaceChildren();for(const r of rows){{if(status==='fail'&&r.passed||status==='pass'&&!r.passed||scene.value!=='all'&&r.scenario!==scene.value)continue;const d=document.createElement('details');const head=document.createElement('summary');head.className=r.passed?'pass':'fail';head.textContent=`${{r.round.toString().padStart(3,'0')}} · ${{r.launchKind}} · ${{r.scenario}} · ${{r.passed?'PASS':'FAIL'}} · open ${{r.openMs??'—'}}ms`;d.append(head);const pre=document.createElement('pre');pre.textContent=JSON.stringify(r,null,2);d.append(pre);if(r.preview){{const img=document.createElement('img');img.loading='lazy';img.alt='Derived native screenshot preview';img.src='screens/round-'+r.round.toString().padStart(3,'0')+'-preview.png';d.append(img)}}host.append(d)}}}}scene.onchange=render;document.querySelector('#status').onchange=render;render();</script></html>'''
(out/'report.html').write_text(body)
print(json.dumps(summary,indent=2))

"""Package actual native verification; refuse missing or mismatched evidence."""
from pathlib import Path
import argparse,hashlib,html,json,shutil,zipfile
from PIL import Image
p=argparse.ArgumentParser();p.add_argument('--out',default='verification/local/repair-evidence-final');p.add_argument('--prepared',default='verification/local/repair-delivery-5');a=p.parse_args()
root=Path(__file__).resolve().parents[1];out=root/a.out
experience=root/'verification/local/experience-repair-300';rows=json.loads((experience/'rounds.json').read_text());environment=json.loads((experience/'environment.json').read_text());identity=json.loads((root/a.prepared/'inspection.json').read_text())
completed=[r for r in rows if 'before' in r and 'after' in r]
assert len(completed)==300 and len({r['round'] for r in rows})==len(rows)
assert environment['installedApkSha256']==identity['manifest']['sha256']
assert json.loads((root/'verification/BUILD.json').read_text())['apk']['sha256']==environment['installedApkSha256']
for row in rows:
 if 'after' not in row:continue
 assert row['after']['width']==3408 and row['after']['height']==2272
 assert (experience/'screens'/row['preview']).is_file(),row['round']
 if row.get('passed') and row['scenario']=='decoded-spectrum':assert min(row['visibleSpectrumChangedPixels'].values())>100
 if row.get('passed') and row['scenario']=='four-station-presets':assert len(row['stationKeys'])==4 and len({s['id'] for s in row['stationKeys']})==4
assert not out.exists(),'Use a fresh output directory; never silently replace a prepared bundle'
out.mkdir(parents=True);shutil.copytree(experience,out/'experience',ignore=shutil.ignore_patterns('*.tmp'))
shutil.copytree(root/'verification/local/repair-reference-final',out/'design');shutil.copytree(root/'verification/local/derived-reference',out/'design/derived-reference')
shutil.copytree(root/'verification/evidence/repair',out/'checks');shutil.copyfile(root/'verification/BUILD.json',out/'BUILD.json');(out/'REPORT.md').write_text((root/'verification/REPORT.md').read_text().replace('evidence/repair/','checks/'));shutil.copyfile(root/'verification/HISTORICAL-PREVIEW1.md',out/'HISTORICAL-PREVIEW1.md')
images=out/'images';images.mkdir();debug=root/'app/build/outputs/apk/debug/app-debug.apk'
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
items=[('before-live',root/'verification/local/live/aac-pcm-spectrum.png','preview.1 旧真实播放截图：可见文字损坏','historical native debug',None),('modern-live',experience/'screens/round-012-modern-a-native.png','最终非 debug APK：真实电台与主屏频谱','final release',identity['manifest']['sha256']),('immersive-live',experience/'screens/round-012-immersive-a-native.png','最终非 debug APK：同播放器与真实左右声道频率图','final release',identity['manifest']['sha256']),('modern-lit',root/'verification/local/repair-final/modern-lit.png','点亮主屏：最终同源 debug 构建的针对性检查','final same-source debug',digest(debug)),('settings-zh',root/'verification/local/repair-final/settings-zh.png','中文设置与完整设备时区：最终同源 debug 构建','final same-source debug',digest(debug))]
manifest=[];figures=[]
for name,source,caption,kind,apk_hash in items:
 im=Image.open(source);assert im.size==(3408,2272)
 shutil.copyfile(source,images/(name+'-native.png'));im.convert('RGB').resize((1704,1136),Image.Resampling.LANCZOS).save(images/(name+'-preview.jpg'),quality=92)
 manifest.append({'name':name,'sourcePixels':[3408,2272],'sha256':digest(source),'kind':kind,'apkSha256':apk_hash,'caption':caption})
 figures.append(f'<figure><figcaption>{html.escape(caption)} · <a href="images/{name}-native.png">3408×2272 原图</a></figcaption><img loading="lazy" src="images/{name}-preview.jpg"></figure>')
(images/'provenance.json').write_text(json.dumps(manifest,indent=2,ensure_ascii=False)+'\n')
(out/'index.html').write_text('''<!doctype html><html lang="zh"><meta charset="utf-8"><title>Aether preview.2 修复核查</title><style>body{background:#101719;color:#dae2df;font:16px system-ui;max-width:1500px;margin:auto;padding:24px}p{line-height:1.7}img{width:100%}figure{margin:36px 0}a{color:#9fd5dc}figcaption{padding:14px 0}</style><h1>漫频 Aether · preview.2 修复核查</h1><p>目标为小米 Pad 9 Pro Max 3408×2272 横屏。图片及循环均来自独立API36 ARM64模拟器；实机触感、扬声器听感和高刷节奏仍未验证。旧版图片来自项目原有原生测试，未收录用户私人照片。最终发行与同源 debug 截图逐项标注，不混用构建身份。</p><p><a href="experience/report.html">300轮记录、图表与逐轮截图</a> · <a href="REPORT.md">验证说明</a> · <a href="design/metrics.json">设计差分</a> · <a href="design/derived-reference/recipe.json">第四预设派生参考配方</a> · <a href="BUILD.json">构建与文件哈希</a></p><p>八个原始冻结全页参考与一个由未改动冻结源码复现的第四预设参考单独记录。实际直播内容不同，图片不能作为与静态MOCK稿逐像素相同的证明。私有签名文件不在本包内。</p>'''+''.join(figures))
# Preserve local originals; redact personal workspace prefixes only in the public copy.
for path in out.rglob('*'):
 if path.is_file() and path.suffix in {'.json','.csv','.txt','.html','.md','.log'}:
  content=path.read_text();content=content.replace(str(root),'${PROJECT_ROOT}/android').replace(str(root.parent),'${PROJECT_ROOT}');path.write_text(content)
files={str(p.relative_to(out)):digest(p) for p in sorted(out.rglob('*')) if p.is_file()};(out/'SHA256-MANIFEST.json').write_text(json.dumps(files,indent=2)+'\n')
archive=out.with_suffix('.zip')
assert not archive.exists()
with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for path in sorted(out.rglob('*')):
  if path.is_file():z.write(path,str(path.relative_to(out)))
print(json.dumps({'bundle':archive.name,'bytes':archive.stat().st_size,'sha256':digest(archive),'files':len(files),'completedNativeCycles':len(completed),'attempts':len(rows),'passed':sum(r.get('passed',False) for r in rows)},indent=2))

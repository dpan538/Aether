"""Render evidence from actual release instrumentation; never synthesize pass records."""
from pathlib import Path
import json,html,shutil,hashlib,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1];local=root/'verification/local/album';out=root/'verification/evidence/album';out.mkdir(parents=True,exist_ok=True)
def read(name):
 for line in (local/name).read_text().splitlines():
  if line.startswith('INSTRUMENTATION_RESULT: aetherResult='):return json.loads(line.split('=',1)[1])
 raise ValueError(name+' has no successful result')
results={n:read(n) for n in ['release-native.txt','release-import.txt','release-restore.txt']}
assert all(r['passed'] for r in results.values())
checks=[c for r in results.values() for c in r['checks']];assert len(checks)==26 and all(c['passed'] for c in checks)
before,after=read('before-upgrade.txt'),read('after-upgrade.txt')
assert before['versionCode']==3 and after['versionCode']==4
retained=['selectedId','favorites','volume','tempo','language'];assert all(before[k]==after[k] for k in retained)
inspection=json.loads((local/'publish/inspection.json').read_text())
manifest=json.loads((root/'app/src/main/assets/albums/before-the-rise/manifest.json').read_text())
for t in manifest['tracks']:
 p=root/'app/src/main/assets/albums/before-the-rise'/t['file'];assert hashlib.sha256(p.read_bytes()).hexdigest()==t['sha256']
assert hashlib.sha256((root/'app/src/main/assets/albums/before-the-rise/cover.png').read_bytes()).hexdigest()==manifest['cover']['sha256']
units=[ET.parse(p).getroot() for p in (root/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
assert all(int(r.get('failures','0'))==0 and int(r.get('errors','0'))==0 for r in units)
data={'version':'0.1.0-preview.3','versionCode':4,'environment':{'type':'Android emulator, not physical Xiaomi','api':36,'resolution':[3408,2272],'densityDpi':320},'nativeChecks':len(checks),'passed':True,'unitTests':sum(int(r.get('tests','0')) for r in units),'checks':checks,'upgrade':{'method':'ADB package-manager in-place update, same signer','beforeVersion':3,'afterVersion':4,'preserved':{k:before[k] for k in retained}},'apk':inspection,'audioSourceIntegrity':{'files':11,'bytes':manifest['audioBytes'],'durationMs':manifest['durationMs'],'allHashesMatch':True,'coverHashMatches':True}}
(out/'acceptance.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
# A static graph of measured durations. RMS checks below are short decoded samples, not loudness analysis.
svg=['<svg xmlns="http://www.w3.org/2000/svg" width="900" height="510" viewBox="0 0 900 510"><rect width="900" height="510" fill="#f7f7f5"/><g font-family="Arial" fill="#222"><text x="28" y="34" font-size="21">Before the Rise — 11 verified recordings</text>']
for i,t in enumerate(manifest['tracks']):
 y=70+i*37;w=t['durationMs']/1000*2.1
 svg.append(f'<text x="28" y="{y+17}" font-size="14">{i+1:02}  {html.escape(t["title"])}</text><rect x="355" y="{y}" width="{w:.2f}" height="23" fill="#222"/><text x="{365+w:.2f}" y="{y+17}" font-size="13">{t["durationMs"]/1000:.2f}s · PASS</text>')
svg.append('<text x="28" y="500" font-size="12" fill="#666">Source durations from ffprobe; every recording also decoded by the signed Android release.</text></g></svg>')
(out/'recordings.svg').write_text(''.join(svg))
for file in ['album-library.png','album-selected.png','album-notes.png','album-immersive.png','album-modern.png','album-imported.png','album-picker.png']:
 shutil.copyfile(local/'release-captures'/file,local/file)
rows=''.join(f'<tr><td>{i+1:02}</td><td>{html.escape(c["name"])}</td><td>PASS</td><td>{html.escape(str(c.get("track","—")))}</td><td>{c.get("samples",0)}</td></tr>' for i,c in enumerate(checks))
page=f'''<!doctype html><meta charset="utf-8"><title>Aether · Before the Rise native evidence</title><style>body{{max-width:1300px;margin:60px auto;padding:0 35px;font:16px/1.7 system-ui;background:#f7f7f5;color:#191919}}h1{{font:52px Georgia}}h2{{margin-top:54px}}img{{max-width:100%;display:block;margin:24px 0}}table{{width:100%;border-collapse:collapse}}td,th{{text-align:left;border-bottom:1px solid #ddd;padding:10px}}small{{color:#666}}code{{word-break:break-all}}</style><h1>Before the Rise</h1><p>Dai Pan (潘岱) · Aether preview.3 / versionCode 4</p><p><b>26 / 26 native checks passed</b> · {data['unitTests']} unit tests · release build and lint completed with no errors (13 existing warnings).</p><p>Android API 36 emulator, 3408×2272, density 320. This is an album/source extension verification, not another 300-cycle run and not a physical Xiaomi Pad test. The earlier 300-cycle repair evidence remains separate.</p><img src="album-library.png"><h2>Recordings</h2><img src="recordings.svg"><h2>Native checks</h2><table><tr><th>#</th><th>Check</th><th>Result</th><th>Track</th><th>Decoded sample count</th></tr>{rows}</table><p>Each track check confirms real MP3 decoding, duration and a short PCM sample. It does not claim full-length subjective listening, measured instrumentation or BPM. Automatic advance and final-track end were tested by seeking near the end. The final UI intentionally has no progress/control dock.</p><h2>Upgrade and package</h2><p>Same-signer ADB package-manager installation upgraded 3 → 4, retaining station, favorites, volume 17, tempo 90 and Chinese. This new version did not repeat the previous public download-cache/system-installer flow; that unchanged updater was verified for preview.2.</p><p>APK: {inspection['bytes']:,} bytes. SHA-256: <code>{inspection['manifest']['sha256']}</code>. Non-debuggable; signature verified.</p><p>All eleven source hashes and the byte-identical 3072×3072 project cover verified. No new storage/microphone permissions. Source menus retain only document read grants.</p><h2>App captures</h2><img src="album-selected.png"><img src="album-notes.png"><img src="album-immersive.png"><img src="album-modern.png"><h2>Earlier test attempts</h2><p>The first debug harness used dialog-local coordinates as screen coordinates and timed out. Two import attempts first missed the Downloads root, then clicked Android’s preview affordance. Those raw attempts remain in the local evidence and portable bundle. The corrected system document selection and all final release cases passed.</p>'''
(local/'report.html').write_text(page);shutil.copyfile(out/'recordings.svg',local/'recordings.svg');shutil.copyfile(out/'acceptance.json',local/'acceptance.json')
print(json.dumps({'nativeChecks':26,'unitTests':data['unitTests'],'sourceHashes':11,'apkBytes':inspection['bytes']},indent=2))

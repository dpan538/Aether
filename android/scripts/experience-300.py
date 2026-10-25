"""Run 300 actual release opens/uses; retain native records, screenshots and Android diagnostics."""
from pathlib import Path
import argparse,datetime,hashlib,json,subprocess,time
p=argparse.ArgumentParser();p.add_argument('--rounds',type=int,default=300);p.add_argument('--out',default='verification/local/experience-300');a=p.parse_args()
root=Path(__file__).resolve().parents[1];out=root/a.out;out.mkdir(parents=True,exist_ok=True)
adb=[str(root/'.tooling/sdk/platform-tools/adb'),'-s','emulator-5582'];pkg='io.github.dpan538.aether'
def run(*args,timeout=60):return subprocess.run(adb+list(args),capture_output=True,check=True,timeout=timeout).stdout
rows=[];ledger=out/'rounds.json'
if ledger.exists():rows=json.loads(ledger.read_text())
metadata={'requestedRounds':a.rounds,'package':pkg,'startedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'designPixels':[3408,2272],'batchSize':10,'method':'30 process-cold Activity starts and 270 warm foreground returns; native pointer events and actual release/player state; per-round PNG previews, native PNGs on cold starts and failures. openMs is Activity launch request to interactive committed frame, excluding instrumentation process bootstrap; actionMs includes condition waits and frame commit.'}
if not (out/'environment.json').exists():
    metadata['installedPackage']=run('shell','dumpsys','package',pkg).decode()
    metadata['device']=run('shell','getprop','ro.product.model').decode().strip()
    metadata['screen']=run('shell','wm','size').decode().strip()
    metadata['density']=run('shell','wm','density').decode().strip()
    metadata['renderer']='\n'.join(l for l in run('shell','dumpsys','SurfaceFlinger').decode().splitlines() if 'GLES:' in l)
    apkPath=run('shell','pm','path',pkg).decode().strip().removeprefix('package:')
    metadata['installedApkSha256']=run('shell','sha256sum',apkPath).decode().split()[0]
    assert metadata['installedApkSha256']=='9f81bbd8977a9a8bf2d354497652c3e75fee95b0a3ef2d3f483e1650d1670810'
    metadata['guestMemTotal']='\n'.join(l for l in run('shell','cat','/proc/meminfo').decode().splitlines() if l.startswith('MemTotal:'))
    (out/'environment.json').write_text(json.dumps(metadata,indent=2))
while len(rows)<a.rounds:
    first=len(rows)+1;count=min(10,a.rounds-len(rows));batch=out/f'batch-{first}';batch.mkdir(exist_ok=True)
    run('shell','am','force-stop',pkg)
    run('shell','rm','-f',f'/sdcard/Android/data/{pkg}/files/evidence/experience/batch-{first}.json')
    t=time.monotonic();epoch=time.time()
    try:
        output=run('shell','am','instrument','-w','-e','operation','soak','-e','startRound',str(first),'-e','rounds',str(count),pkg+'.test/com.aether.radio.NativeEvidence',timeout=360)
        (batch/'instrumentation.txt').write_bytes(output)
        raw=run('shell','cat',f'/sdcard/Android/data/{pkg}/files/evidence/experience/batch-{first}.json')
        observed=json.loads(raw);assert observed and [r['round'] for r in observed]==list(range(first,first+len(observed)))
        rows.extend(observed)
        run('pull',f'/sdcard/Android/data/{pkg}/files/evidence/experience/.',str(out/'screens'),timeout=120)
    except Exception as error:
        (batch/'harness-error.txt').write_text(str(error))
        # This is a real attempted cold open, counted as failed; never invent
        # unexecuted passes for the rest of a failed batch.
        try:
            observed=json.loads(run('shell','cat',f'/sdcard/Android/data/{pkg}/files/evidence/experience/batch-{first}.json'))
            rows.extend(observed)
        except Exception:rows.append({'round':first,'launchKind':'cold','scenario':'launch-or-harness-failure','passed':False,'error':str(error),'totalMs':round((time.monotonic()-t)*1000)})
    ledger.write_text(json.dumps(rows,indent=2,ensure_ascii=False)+'\n')
    events=run('logcat','-b','events','-d','-v','epoch').decode(errors='replace')
    own=[line for line in events.splitlines() if pkg in line and line.strip().split(' ')[0].replace('.','',1).isdigit() and float(line.strip().split(' ')[0])>=epoch]
    (batch/'events.txt').write_text('\n'.join(own)+'\n')
    (batch/'gfxinfo.txt').write_bytes(run('shell','dumpsys','gfxinfo',pkg,'framestats'))
    print(json.dumps({'completed':len(rows),'passed':sum(r.get('passed',False) for r in rows),'failed':sum(not r.get('passed',False) for r in rows),'lastBatchSeconds':round(time.monotonic()-t,1)}),flush=True)
assert len(rows)==a.rounds
print('Completed',len(rows),'actual rounds; passed',sum(r.get('passed',False) for r in rows),flush=True)

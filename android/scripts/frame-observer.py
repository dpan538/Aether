"""Read-only gfxinfo sampling while the 300-round native test is running."""
from pathlib import Path
import datetime,json,re,subprocess,time
root=Path(__file__).resolve().parents[1];out=root/'verification/local/experience-300';folder=out/'frame-samples';folder.mkdir(parents=True,exist_ok=True)
adb=[str(root/'.tooling/sdk/platform-tools/adb'),'-s','emulator-5582'];samples=[];started=time.monotonic()
while time.monotonic()-started<7200:
    try:
        if len(json.loads((out/'rounds.json').read_text()))>=300:break
    except Exception:pass
    try:pid=subprocess.run(adb+['shell','pidof','io.github.dpan538.aether'],capture_output=True,text=True,timeout=10).stdout.strip()
    except subprocess.TimeoutExpired:time.sleep(5);continue
    if pid:
        result=subprocess.run(adb+['shell','dumpsys','gfxinfo','io.github.dpan538.aether','framestats'],capture_output=True,text=True,timeout=15)
        if 'Total frames rendered:' in result.stdout:
            name=f'sample-{len(samples)+1:04}.txt';(folder/name).write_text(result.stdout)
            item={'atUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'pid':pid,'file':name}
            for field,pattern in [('frames',r'Total frames rendered: (\d+)'),('janky',r'Janky frames: (\d+)'),('p50ms',r'50th percentile: (\d+)ms'),('p95ms',r'95th percentile: (\d+)ms')]:
                match=re.search(pattern,result.stdout)
                if match:item[field]=int(match[1])
            samples.append(item);(folder/'samples.json').write_text(json.dumps(samples,indent=2)+'\n')
    time.sleep(20)
print('Retained',len(samples),'read-only emulator frame samples; not a physical-device FPS guarantee')

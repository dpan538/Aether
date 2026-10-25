"""Raw Android captures. Run against the independent Aether emulator only."""
import argparse, json, os, subprocess, time
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--out',default='verification/local/round-02')
p.add_argument('--states',nargs='*',default=['modern-ink','modern-lit','modern-settings','modern-language-menu','modern-genre-menu','modern-mute','modern-off','preset004','immersive'])
p.add_argument('--serial',default='emulator-5582')
a=p.parse_args();root=Path(__file__).resolve().parents[1];out=root/a.out;out.mkdir(parents=True,exist_ok=True)
adb=str(root/'.tooling/sdk/platform-tools/adb');package='io.github.dpan538.aether.debug'
def run(*args,**kwargs):return subprocess.run([adb,'-s',a.serial,*args],check=True,capture_output=True,**kwargs).stdout
for name in a.states:
    run('shell','am','force-stop',package)
    run('shell','run-as',package,'rm','-f','files/frame-ready.txt')
    run('shell','am','start','-n',package+'/com.aether.radio.MainActivity','--es','mode','reference','--es','scenario',name)
    ready=False
    for _ in range(100):
        try:
            if run('shell','run-as',package,'cat','files/frame-ready.txt').decode().strip()==name:ready=True;break
        except subprocess.CalledProcessError:pass
        time.sleep(.3)
    if not ready:raise RuntimeError('Android first frame did not finish: '+name)
    if name=='immersive':
        for _ in range(100):
            try:
                if json.loads(run('shell','run-as',package,'cat','files/runtime-state.json')).get('artworkReady'):break
            except Exception:pass
            time.sleep(.4)
        else:raise RuntimeError('GLES artwork did not finish')
        time.sleep(.6)
    else:time.sleep(2.1)
    for _ in range(120):
        try:
            committed=json.loads(run('shell','run-as',package,'cat','files/runtime-state.json'))
            if committed.get('assetsReady') and committed.get('committedRevision',-1)>=0 and committed.get('contentRevision')==committed.get('committedRevision') and committed.get('committedView')==committed.get('view') and not committed.get('committedRefresh',True) and committed.get('committedPage')==committed.get('page') and abs(committed.get('committedFrontlight',-1)-committed.get('frontlight',0))<.001:break
        except Exception:pass
        time.sleep(.25)
    else:raise RuntimeError('Final native frame was not committed: '+name)
    (out/(name+'.png')).write_bytes(run('exec-out','screencap','-p'))
    try:state=json.loads(run('shell','run-as',package,'cat','files/runtime-state.json'));(out/(name+'.json')).write_text(json.dumps(state,indent=2))
    except Exception as error:print('state read failed',error,flush=True)
    print('captured',name,flush=True)
run('logcat','-d','-s','AetherSVG','AetherDisc','AndroidRuntime','AetherState')

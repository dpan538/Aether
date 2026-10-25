import json,subprocess,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ADB=[str(ROOT/'.tooling/sdk/platform-tools/adb'),'-s','emulator-5582']
PACKAGE='io.github.dpan538.aether.debug'
def run(*args):return subprocess.run(ADB+list(args),check=True,capture_output=True).stdout
def state():return json.loads(run('shell','run-as',PACKAGE,'cat','files/runtime-state.json'))
def wait(predicate,seconds=35):
    end=time.monotonic()+seconds;last={}
    while time.monotonic()<end:
        try:
            last=state()
            if predicate(last):return last
        except Exception:pass
        time.sleep(.25)
    raise AssertionError(last)
def launch(mode='reference',scenario='modern-ink',extras=()):
    run('shell','am','force-stop',PACKAGE);run('shell','run-as',PACKAGE,'rm','-f','files/frame-ready.txt')
    run('shell','am','start','-n',PACKAGE+'/com.aether.radio.MainActivity','--es','mode',mode,'--es','scenario',scenario,*extras)
    for _ in range(120):
        try:
            if run('shell','run-as',PACKAGE,'cat','files/frame-ready.txt').decode().strip()==scenario:break
        except Exception:pass
        time.sleep(.25)
    else:raise RuntimeError('first native frame did not finish')
    return wait(lambda s: s.get('assetsReady') and not s.get('refreshing',False) and not s.get('committedRefresh',True)) if not any(x=='refresh_time' for x in extras) else state()
def capture(out,name):
    out.mkdir(parents=True,exist_ok=True)
    wait(lambda s:s.get('committedRevision',-1)>=0 and s.get('contentRevision')==s.get('committedRevision') and s.get('committedPage')==s.get('page') and s.get('committedView')==s.get('view') and abs(s.get('committedOffset',1))<.01 and not s.get('committedRefresh',True))
    time.sleep(2)
    (out/(name+'.png')).write_bytes(run('exec-out','screencap','-p'))
    observed=state();(out/(name+'.json')).write_text(json.dumps(observed,indent=2,ensure_ascii=False));print('captured',name,flush=True);return observed

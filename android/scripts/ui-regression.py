"""Exercise real Android touch/key events; report every observed state."""
import json,subprocess,time
from pathlib import Path
root=Path(__file__).resolve().parents[1];out=root/'verification/local/interaction';out.mkdir(parents=True,exist_ok=True)
adb=[str(root/'.tooling/sdk/platform-tools/adb'),'-s','emulator-5582'];pkg='io.github.dpan538.aether.debug';evidence=[]
def run(*args):return subprocess.run(adb+list(args),check=True,capture_output=True).stdout
def nodes(name):
    def walk(n):yield n;[None for c in n.get('children',[]) for _ in []]
    data=json.loads((root/f'app/src/main/assets/scenes/{name}.json').read_text())
    result=[]
    def rec(n):result.append(n);[rec(c) for c in n.get('children',[])]
    rec(data['root']);return result
def box(action,scene='modern-ink'):
    n=next(x for x in nodes(scene) if x.get('action')==action or x.get('id')==action);return n['r']
def tap(action,scene='modern-ink'):
    x,y,w,h=box(action,scene);run('shell','input','tap',str(round((x+w/2)*2)),str(round((y+h/2)*2)));time.sleep(.25)
def state():return json.loads(run('shell','run-as',pkg,'cat','files/runtime-state.json'))
def check(label,**expected):
    for _ in range(30):
        s=state()
        if all(s.get(k)==v for k,v in expected.items()):break
        time.sleep(.2)
    else:raise AssertionError(f'{label}: expected {expected}, got {s}')
    evidence.append({'check':label,'expected':expected,'observed':s});print('PASS',label,flush=True);return s
def capture(name):(out/(name+'.png')).write_bytes(run('exec-out','screencap','-p'))
run('shell','am','force-stop',pkg);run('shell','run-as',pkg,'rm','-f','files/frame-ready.txt')
run('shell','am','start','-n',pkg+'/com.aether.radio.MainActivity','--es','mode','reference','--es','scenario','modern-ink')
for _ in range(100):
    try:
        if run('shell','run-as',pkg,'cat','files/frame-ready.txt').strip()==b'modern-ink':break
    except subprocess.CalledProcessError:pass
    time.sleep(.2)
time.sleep(2.2)
try:
    check('reference starts offline',volume=20,selected=0,playerCount=0,connection='DISCONNECTED')
    tap('play-toggle');check('reference pause feedback',presentationPlaying=False)
    tap('play-toggle');check('reference play feedback',presentationPlaying=True)
    tap('volume');check('pressing relative encoder does not seek',volume=20)
    x,y,w,h=box('volume');cx=round((x+w/2)*2);cy=round((y+h/2)*2)
    run('shell','input','swipe',str(cx),str(cy),str(cx),str(cy-130),'120')
    time.sleep(.25);v=state()['volume'];assert 20<=v<=21,('fast drag must not burst',v)
    check('fast centre drag is clamped',volume=v)
    time.sleep(.5);check('no delayed volume jump',volume=v)
    run('shell','input','swipe',str(cx),str(cy),str(cx),str(cy-100),'700')
    time.sleep(.3);previous=state()['volume'];assert v<previous<=v+3,('slow drag must operate',v,previous)
    check('slow centre drag consumes fresh travel',volume=previous)
    tap('stereo-button');check('mute',volume=0)
    tap('stereo-button');check('restore previous volume',volume=previous)
    tap('volume');run('shell','input','keyevent','KEYCODE_MOVE_END');check('volume upper bound',volume=30)
    run('shell','input','keyevent','KEYCODE_DPAD_UP');check('upper bound holds',volume=30)
    run('shell','input','keyevent','KEYCODE_MOVE_HOME');check('volume lower bound',volume=0)
    tap('station:3');check('fourth physical preset',selected=3)
    tap('device-lock');check('lock',locked=True)
    tap('station:0');check('locked station controls disabled',selected=3)
    tap('device-light');check('light remains available while locked',light=True,locked=True)
    time.sleep(1.2);tap('device-lock');check('unlock',locked=False)
    x,y,w,h=box('tempo');run('shell','input','swipe',str(round((x+w/2)*2)),str(round((y+h/2)*2)),str(round((x+w-22)*2)),str(round((y+h/2)*2)),'500');check('tempo follows to 150',tempo=150)
    run('shell','input','swipe',str(round((x+w-22)*2)),str(round((y+h/2)*2)),str(round((x+22)*2)),str(round((y+h/2)*2)),'500');check('tempo follows to 50',tempo=50)
    tap('menu:genre-filter');tap('option:3','genre-ink');check('genre filter',filteredCount=1,menu=None,selected=3)
    tap('menu:region-filter');tap('option:2','region-ink');check('combined region and genre',filteredCount=1)
    # With one result, the row fills the available list well.
    x,y,w,h=box('station-list');run('shell','input','tap',str(round((x+w/2)*2)),str(round((y+h/2)*2)));check('filtered row selects NTS',selected=2)
    tap('clear-filters');check('reset filters',filteredCount=4)
    tap('device-settings');check('settings',view='settings')
    tap('menu:ui-language','settings-ink');tap('option:1','ui-language-ink');check('custom language menu',view='settings',menu=None,language='zh')
    tap('check-updates','settings-ink');tap('about-toggle','settings-ink');check('updates honest and About visible',updateMessage=True,about=True);capture('settings-zh-update-about')
    tap('device-settings');check('return directory',view='directory')
    # A blank channel between the upper deck and console is safe for navigation.
    run('shell','input','swipe','2500','1150','1600','1150','550');check('continuous horizontal view switch',page=1,selected=2,volume=0)
    run('shell','input','swipe','900','1100','900','900','400');check('immersive vertical station step',selected=3,page=1)
    tap('previous','immersive');check('immersive previous triangle',selected=2)
    tap('next','immersive');check('immersive next triangle',selected=3)
    run('shell','input','swipe','900','1100','1500','1100','500');check('return shares session',page=0,selected=3,volume=0)
    tap('device-power');check('power off retains station',powered=False,selected=3)
    time.sleep(2.1);capture('off-retained-004');tap('station:0');check('off controls disabled',selected=3,powered=False)
    tap('device-power');time.sleep(2.1);check('power on restores session',powered=True,selected=3)
    capture('final-session')
finally:
    (out/'results.json').write_text(json.dumps(evidence,indent=2,ensure_ascii=False))

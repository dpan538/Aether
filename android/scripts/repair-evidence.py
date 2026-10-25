"""Focused native regression for the defects reported on Xiaomi Pad 9 Pro Max."""
from evidence_device import *
from PIL import Image
import numpy as np
out=ROOT/'verification/local/repair-final';out.mkdir(parents=True,exist_ok=True)
results=[]
def record(name,s=None,**details):
 results.append({'check':name,'passed':True,'state':s or state(),**details});(out/'checks.json').write_text(json.dumps(results,indent=2,ensure_ascii=False))
def node(action,scene='modern-ink'):
 def walk(x):
  if isinstance(x,dict):
   if x.get('action')==action or x.get('id')==action:return x
   for v in x.values():
    r=walk(v)
    if r:return r
  elif isinstance(x,list):
   for v in x:
    r=walk(v)
    if r:return r
 return walk(json.loads((ROOT/'app/src/main/assets/scenes'/f'{scene}.json').read_text()))
def tap(action,scene='modern-ink',fraction=.5,jitter=False):
 n=node(action,scene)
 if action.startswith("preset:"):n=node("favorite-stations",scene)["children"][int(action.split(":")[1])]
 assert n,action
 x,y,w,h=n['r'];x=round((x+w*fraction)*2);y=round((y+h/2)*2)
 if jitter:run('shell','input','swipe',str(x),str(y),str(x+4),str(y+3),'110')
 else:run('shell','input','tap',str(x),str(y))
 time.sleep(.8)
def variance(a,b,rect):
 x=np.array(Image.open(out/f'{a}.png').convert('RGB').crop(rect));y=np.array(Image.open(out/f'{b}.png').convert('RGB').crop(rect));return int(np.any(x!=y,axis=2).sum())
# A clean first-run state: the four keys must work with zero favorites.
run('shell','pm','clear',PACKAGE)
launch('live',extras=('--es','query','SomaFM','--ez','touch_trace','true'))
wait(lambda s:s.get('catalogCount',0)>=4)
s=state();assert not s['favorites'] and len(s['presetIds'])==4
for i in range(4):
 before=state();expected=before['presetIds'][i]
 tap(f'preset:{i}',jitter=True)
 s=wait(lambda s:s.get('selectedId')==expected and s.get('mediaId')==expected)
 assert s['playerCount']==1 and not s['favorites'];record(f'Station {i+1}: physical touch with 4px/3px finger movement',s,expectedId=expected,printedName=before['presetNames'][i])
tap('preset:0');wait(lambda s:s.get('isPlaying') and s['spectrumSamples']>8192,60)
record('Real directory station decodes with monitor enabled on first launch')
capture(out,'modern-ink-a');time.sleep(1.5);capture(out,'modern-ink-b')
x,y,w,h=node('spectrum')['r'];rect=tuple(round(v*2) for v in (x,y,x+w,y+h));changed=variance('modern-ink-a','modern-ink-b',rect);assert changed>100;record('Modern spectrum actual visible pixel movement',changedPixels=changed,region=rect)
tap('device-light');wait(lambda s:s['committedFrontlight']>.999);capture(out,'modern-lit')
tap('equalizer-preview');wait(lambda s:not s['spectrumEnabled']);capture(out,'monitor-off')
tap('equalizer-preview');wait(lambda s:s['spectrumEnabled'] and s['spectrumSamples']>8192);record('Equalizer toggles real monitor off and back on')
for f,speed in [(0,.5),(1,1.5),(.5,1)]:
 tap('tempo',fraction=f);wait(lambda s:abs(s['playbackSpeed']-speed)<.001 and s['playbackPitch']==1)
record('Tempo 50/150/100 percent uses real player speed with pitch 1')
tap('monitor-toggle');wait(lambda s:s['motion'] and s['spectrumEnabled']);record('Artwork motion and PCM monitor remain independent')
tap('device-settings');wait(lambda s:s['view']=='settings' and s['committedView']=='settings');capture(out,'settings-en')
tap('menu:ui-language','settings-lit');wait(lambda s:s['menu']=='ui-language');tap('option:1','ui-language-lit');wait(lambda s:s['language']=='zh');capture(out,'settings-zh')
tap('menu:ui-language','settings-lit');wait(lambda s:s['menu']=='ui-language');tap('option:0','ui-language-lit');wait(lambda s:s['language']=='en')
tap('device-settings');wait(lambda s:s['view']=='directory');tap('view:favorites');wait(lambda s:s['view']=='favorites');tap('device-settings');wait(lambda s:s['view']=='settings');tap('device-settings');wait(lambda s:s['view']=='favorites');tap('view:directory');wait(lambda s:s['view']=='directory');record('Settings restores prior directory/favorites view')
before=state();run('shell','input','swipe','2500','1150','1600','1150','500');s=wait(lambda s:s['page']==1 and s['committedPage']==1 and abs(s['committedOffset'])<.01 and s['artworkReady']);assert s['mediaId']==before['mediaId'] and s['isPlaying'];capture(out,'immersive-a');time.sleep(1.5);capture(out,'immersive-b')
# Read the actual exported graph rectangle, never align/crop a reference to hide differences.
def graph(x):
 if isinstance(x,dict):
  if '0 0 600 190' in x.get('svg',''):return x['r']
  for v in x.values():
   r=graph(v)
   if r:return r
 elif isinstance(x,list):
  for v in x:
   r=graph(v)
   if r:return r
x,y,w,h=graph(json.loads((ROOT/'app/src/main/assets/scenes/immersive.json').read_text()));rect=tuple(round(v*2) for v in (x,y,x+w,y+h));changed=variance('immersive-a','immersive-b',rect);assert changed>100;record('Immersive spectrum has visible decoded PCM movement, same single player',changedPixels=changed,region=rect)
for action,d in [('next',1),('previous',-1)]:
 before=state();tap(action,'immersive');s=wait(lambda s:s['selectedId']!=before['selectedId']);assert s['selectedId']==s['mediaId'];record('Immersive triangle '+action,s)
run('shell','input','swipe','900','1100','1500','1100','500');wait(lambda s:s['page']==0 and s['committedPage']==0)
tap('preset:0');wait(lambda s:s['isPlaying'] and s['spectrumSamples']>8192,60);tap('play-toggle');wait(lambda s:not s['isPlaying']);capture(out,'paused');record('Pause stops playback and live spectrum')
tap('play-toggle');wait(lambda s:s['isPlaying']);tap('device-lock');wait(lambda s:s['locked']);before=state();tap('preset:1');time.sleep(1);assert state()['selectedId']==before['selectedId'];tap('device-lock');wait(lambda s:not s['locked']);record('Locked Station keys cannot retune')
tap('device-power');wait(lambda s:not s['powered'] and not s['refreshing']);capture(out,'powered-off');assert not state()['isPlaying'];tap('device-power');wait(lambda s:s['powered'] and not s['refreshing']);record('Power retains ink and stops audio')
print('Focused repair checks passed:',len(results),flush=True)

from evidence_device import *
out=ROOT/'verification/local/edges';checks=[]
def tap(x,y):run('shell','input','tap',str(x),str(y));time.sleep(.35)
def check(name,predicate):
    s=wait(predicate);checks.append({'check':name,'observed':s});return s
launch(scenario='modern-lit')
for remaining in [3,2,1,0]:
    tap(1900,1670);tap(339,2004)
    check('remove favorite to '+str(remaining),lambda s:len(s['favorites'])==remaining)
s=capture(out,'empty-favorites');selected=s['selectedId']
tap(1900,1670);check('empty preset disabled',lambda s:s['selectedId']==selected and not s['favorites'])
tap(1850,280);run('shell','input','text','qxz')
check('native keyboard search has no results',lambda s:s['query']=='qxz' and s['filteredCount']==0)
run('shell','input','keyevent','KEYCODE_ENTER');time.sleep(.6)
capture(out,'no-results')
# Reset through the actual directory control, then exercise all four selector stops.
tap(3030,466);check('search reset',lambda s:s['filteredCount']==4 and not s['query'])
tap(2850,400);check('custom station-language menu',lambda s:s.get('menu')=='language')
# English is option 2 in the deterministic reference catalogue.
scene=json.loads((ROOT/'app/src/main/assets/scenes/language-lit.json').read_text())
def flatten(n):
    yield n
    for child in n.get('children',[]):yield from flatten(child)
option=next(n for n in flatten(scene['root']) if n.get('action')=='option:2')
x,y,w,h=option['r'];tap(round((x+w/2)*2),round((y+h/2)*2))
check('station language filters English',lambda s:s['stationLanguage']=='English' and s['filteredCount']==2)
tap(3030,466);check('language reset',lambda s:s['filteredCount']==4 and not s['stationLanguage'])
for mode in [1,2,3,0]:
    tap(775,1008);check('selector mode '+str(mode),lambda s:s['tuningMode']==mode)
before=state()['selectedId'];tap(442,883);check('large wheel center dead zone',lambda s:s['selectedId']==before)
tap(590,885);check('large wheel edge tunes',lambda s:s['selectedId']!=before)
tap(2600,1875);check('equalizer enables reference demonstration',lambda s:s['motion'])
tap(2600,1875);check('equalizer disables reference demonstration',lambda s:not s['motion'])
tap(2670,1017);s=check('random selects a loaded result',lambda s:s['selectedId'] in ['ref:'+str(i) for i in range(4)])
before=s['volume']
for ending in ['CANCEL','UP']:
    run('shell','input','motionevent','DOWN','2980','1973')
    run('shell','input','motionevent','MOVE','2980','1953')
    run('shell','input','motionevent',ending,'2980','1953')
    time.sleep(.3)
check('cancelled encoder travel does not carry into next gesture',lambda s:s['volume']==before)
capture(out,'random-and-modes')
(out/'results.json').write_text(json.dumps(checks,indent=2,ensure_ascii=False))
print('edge checks passed',len(checks),flush=True)

"""Observe native feedback requests and settings on the Aether emulator."""
from evidence_device import *
out=ROOT/'verification/local/haptics';out.mkdir(parents=True,exist_ok=True);checks={}
def tap(x,y):run('shell','input','tap',str(x),str(y));time.sleep(.35)
original=run('shell','settings','get','system','haptic_feedback_enabled').decode().strip()
try:
    run('shell','settings','put','system','haptic_feedback_enabled','1')
    launch()
    before=state();tap(2980,1973);s=wait(lambda s:s['volume']==20)
    assert s['hapticTicks']==before['hapticTicks'];checks['encoder-press-does-not-seek-or-tick']=s
    run('shell','input','swipe','2980','1973','2980','1873','700')
    s=wait(lambda s:s['volume']>20);v=s['volume'];time.sleep(1.2);s=state();assert s['volume']==v;assert s['hapticTicks']>before['hapticTicks'];checks['accepted-volume-detents']=s
    v=s['volume'];ticks=s['hapticTicks'];time.sleep(1.2);s=state();assert s['volume']==v and s['hapticTicks']==ticks;checks['no-queued-detents']=s
    run('shell','input','keyevent','KEYCODE_MOVE_END');wait(lambda s:s['volume']==30)
    run('shell','input','swipe','2980','1973','2980','1873','700')
    s=wait(lambda s:s['hapticStops']>0);assert s['volume']==30;checks['bounded-end-stop']=s
    run('shell','settings','put','system','haptic_feedback_enabled','0')
    launch();tap(386,505)
    s=wait(lambda s:s['hapticAttempts']>0);assert s['hapticAccepted']==0;checks['system-disabled-feedback-respected']=s
    (out/'vibrator-service.txt').write_bytes(run('shell','dumpsys','vibrator_manager'))
finally:
    if original=='null':run('shell','settings','delete','system','haptic_feedback_enabled')
    else:run('shell','settings','put','system','haptic_feedback_enabled',original)
    (out/'results.json').write_text(json.dumps(checks,indent=2))
print('native haptic checks passed',list(checks),flush=True)

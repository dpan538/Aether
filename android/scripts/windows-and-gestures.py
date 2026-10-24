from evidence_device import *
out=ROOT/'verification/local/windows';results={}
try:
    launch(scenario='modern-lit')
    for w,h in [(1704,1136),(2560,1440),(1136,1704)]:
        run('shell','wm','size',f'{w}x{h}');s=wait(lambda s:s.get('width')==w and s.get('height')==h)
        # Android can recreate the Activity when the screen-layout bucket changes.
        # Let its specified startup ink lock complete before sending controls.
        time.sleep(2.1);wait(lambda s:not s['refreshing'])
        results[f'{w}x{h}']=capture(out,f'{w}x{h}')
        sx=min(w/1704,h/1136);ox=(w-1704*sx)/2;oy=(h-1136*sx)/2
        # Real target mapping at the adapted frame, fourth preset then first.
        for x,target in [(1439,3),(949,0)]:
            run('shell','input','tap',str(round(ox+x*sx)),str(round(oy+835*sx)))
            wait(lambda s:s['selected']==target)
finally:run('shell','wm','size','3408x2272')
(out/'results.json').write_text(json.dumps(results,indent=2,ensure_ascii=False))
launch(scenario='immersive',extras=('--ez','touch_trace','true'));wait(lambda s:s.get('artworkReady'),60)
run('shell','input','swipe','900','1100','1500','1100','500');wait(lambda s:s['page']==0)
run('shell','input','motionevent','DOWN','2500','1150')
run('shell','input','motionevent','MOVE','2300','1150');capture(out,'drag-100-logical')
run('shell','input','motionevent','MOVE','2100','1150');capture(out,'drag-200-logical')
run('shell','input','motionevent','CANCEL','2100','1150');time.sleep(.6)
s=capture(out,'cancelled');assert s['page']==0 and s['selected']==0
(out/'input-trace.txt').write_bytes(run('logcat','-d','-s','AetherInput'))
# Record at a native 1704x1136 surface: avoid a 4K software-render/readback
# bottleneck changing input timing while the emulator's encoder is running.
run('shell','wm','size','1704x1136');wait(lambda s:s.get('viewWidth')==1704 and s.get('viewHeight')==1136)
time.sleep(2.1);wait(lambda s:not s['refreshing'])
(out/'video-state.json').write_text(json.dumps(state(),indent=2))
def vt(x,y):run('shell','input','tap',str(round(x/2)),str(round(y/2)))
def vs(x1,y1,x2,y2,ms):run('shell','input','swipe',str(round(x1/2)),str(round(y1/2)),str(round(x2/2)),str(round(y2/2)),str(ms))
record=subprocess.Popen(ADB+['shell','screenrecord','--size','1704x1136','--bit-rate','6000000','--time-limit','18','/sdcard/aether-motion.mp4'],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
try:
    time.sleep(1)
    for delay in (.45,.45,1.2):vt(631,505);time.sleep(delay)
    vt(386,270);wait(lambda s:not s['powered']);wait(lambda s:not s['refreshing'])
    vt(386,270);wait(lambda s:s['powered']);wait(lambda s:not s['refreshing'])
    vt(2878,1670);time.sleep(.4)
    vs(2980,1973,2980,1873,700)
    vs(620,883,442,673,700)
    vs(2050,1865,2425,1865,500)
    vs(2500,1150,1600,1150,500);wait(lambda s:s['page']==1)
    vs(900,1100,900,900,400)
    vs(900,1100,1500,1100,500)
    record.communicate(timeout=24)
finally:
    if record.poll() is None:record.terminate()
    run('shell','wm','size','3408x2272')
run('pull','/sdcard/aether-motion.mp4',str(out/'aether-motion.mp4'))
print('window adaptation, cancellation and native recording saved',flush=True)

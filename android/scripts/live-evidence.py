from evidence_device import *
import atexit
out=ROOT/'verification/local/live';out.mkdir(parents=True,exist_ok=True);results={}
atexit.register(lambda:(out/'results.json').write_text(json.dumps(results,indent=2,ensure_ascii=False)))
def tap(x,y):run('shell','input','tap',str(x),str(y));time.sleep(.3)
for codec,url in [('aac','https://ice6.somafm.com/groovesalad-128-aac'),('mp3','https://ice6.somafm.com/groovesalad-128-mp3')]:
    launch('live',extras=('--es','stream',url,'--es','query','SomaFM'))
    s=wait(lambda s:s.get('isPlaying') and s.get('positionMs',0)>3000 and s.get('catalogCount',0)>1,45)
    assert s['selectedId']==s['mediaId']=='verification:stream' and s['station']=='Verification stream'
    assert s['decodedFormat'] and s['playerCount']==1
    results[codec]=capture(out,codec+'-playing')
    if codec=='aac':
        tap(2600,1875)
        fft=wait(lambda s:s['spectrumEnabled'] and s['spectrumSamples']>4096,30)
        assert fft['spectrumRms']>0 and max(fft['spectrumLevels'])>-80
        results['decoded-pcm-spectrum']=capture(out,'aac-pcm-spectrum')
        run('shell','input','swipe','2050','1865','2425','1865','500')
        results['real-tempo-150']=wait(lambda s:s['tempo']==150 and abs(s['playbackSpeed']-1.5)<.001 and s['playbackPitch']==1)
        run('shell','input','swipe','2425','1865','1738','1865','500')
        results['real-tempo-50']=wait(lambda s:s['tempo']==50 and abs(s['playbackSpeed']-.5)<.001 and s['playbackPitch']==1)
        run('shell','input','tap','2088','1865');wait(lambda s:s['tempo']==100 and s['playbackSpeed']==1)
        before=s['positionMs'];run('shell','input','swipe','2500','1150','1600','1150','500')
        p=wait(lambda s:s['page']==1 and s['isPlaying']);assert p['mediaId']==s['mediaId'] and p['positionMs']>before
        results['same-player-on-immersive']=p
        run('shell','input','swipe','900','1100','1500','1100','500');wait(lambda s:s['page']==0)
        # One click stores a real station record even when it is outside the refreshed directory.
        if 'verification:stream' not in state()['favorites']:tap(339,2004)
        f=wait(lambda s:'verification:stream' in s['favorites']);results['favorite-saved']=f
        run('shell','input','keyevent','KEYCODE_HOME');paused=wait(lambda s:not s['isPlaying']);time.sleep(1)
        run('shell','am','start','-W','-n',PACKAGE+'/com.aether.radio.MainActivity')
        time.sleep(1.2) # Activity launch + foreground window transition before touch.
        resumed=wait(lambda s:s['mediaId']==paused['mediaId'] and not s['isPlaying'])
        assert resumed['positionMs']<=paused['positionMs']+350
        # Play key in the original logical frame.
        tap(2225,2042);results['resume-after-background']=wait(lambda s:s['isPlaying'] and s['positionMs']>resumed['positionMs']+1000)
        launch('live');persisted=wait(lambda s:'verification:stream' in s['favorites']);results['favorite-persists-after-restart']=persisted
        tap(1900,1670);results['favorite-recalls-saved-stream']=wait(lambda s:s.get('isPlaying') and s.get('mediaId')=='verification:stream',40)
launch('live',extras=('--es','stream','https://example.com/aether-deliberate-missing-stream.mp3'))
error=wait(lambda s:s.get('connection')=='ERROR',45);assert not error['isPlaying'] and not error.get('metadata');results['http-error']=capture(out,'http-error')
(out/'results.json').write_text(json.dumps(results,indent=2,ensure_ascii=False))
print('live checks passed',list(results),flush=True)

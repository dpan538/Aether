from evidence_device import *
out=ROOT/'verification/local/motion';observations={}
for ms in (0,525,1050):
    launch(scenario='modern-ink',extras=('--ei','light_time',str(ms)))
    observations[f'light-{ms:04}']=capture(out,f'light-{ms:04}')
for ms in (0,396,792,1152,1440,1800):
    launch(extras=('--el','refresh_time',str(ms)))
    observations[f'refresh-{ms:04}']=capture(out,f'refresh-{ms:04}')
for ms in (0,1000,2000):
    launch(scenario='modern-lit',extras=('--ez','motion','true','--el','phase_time',str(ms)))
    observations[f'spectrum-{ms:04}']=capture(out,f'spectrum-{ms:04}')
for ms in (0,60000,120000,166667):
    launch(scenario='immersive',extras=('--ez','motion','true','--el','phase_time',str(ms)))
    wait(lambda s:s.get('artworkReady'),60)
    observations[f'foil-{ms:06}']=capture(out,f'foil-{ms:06}')
run('shell','settings','put','global','animator_duration_scale','0')
try:
    launch(extras=('--ez','animate_light','true','--ez','motion','true'))
    s=capture(out,'reduced-motion');assert s['frontlight']==1 and not s['motion'] and not s['refreshing'];observations['reduced-motion']=s
finally:run('shell','settings','put','global','animator_duration_scale','1')
(out/'observations.json').write_text(json.dumps(observations,indent=2,ensure_ascii=False))

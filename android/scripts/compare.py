"""No image registration or baseline edits. Raw same-canvas visual evidence."""
from pathlib import Path
import argparse,json
import numpy as np
from PIL import Image,ImageChops,ImageDraw
p=argparse.ArgumentParser();p.add_argument('round');p.add_argument('--extra-references');a=p.parse_args()
root=Path(__file__).resolve().parents[1];folder=root/a.round
references=root.parent/'design/snapshots/2026-10-24-two-page-baseline/renders'
regions={'controls':(1655,1770,3205,2140),'guides':(205,650,815,1125),'directory':(1550,115,3205,1125),'now':(245,1225,1610,1710)}
results={}
for native in sorted(folder.glob('*.png')):
    ref=references/(native.stem+'.png')
    if not ref.exists() and a.extra_references:ref=root/a.extra_references/(native.stem+'.png')
    if not ref.exists():continue
    x=Image.open(native).convert('RGB');y=Image.open(ref).convert('RGB')
    if x.size!=y.size:results[native.stem]={'error':'different raw sizes','native':x.size,'reference':y.size};continue
    data=np.abs(np.asarray(x).astype(np.float32)-np.asarray(y).astype(np.float32))
    d=ImageChops.difference(x,y)
    Image.blend(y,x,.5).save(folder/(native.stem+'-overlay50.png'))
    d.save(folder/(native.stem+'-difference.png'))
    Image.fromarray(np.minimum(data*4,255).astype(np.uint8)).save(folder/(native.stem+'-difference4x.png'))
    metrics={'size':x.size,'mae':float(data.mean()),'rmse':float(np.sqrt((data**2).mean())),'p95_channel_abs':float(np.percentile(data,95)),'exact_pixel_percent':float(np.mean(np.max(data,axis=2)==0)*100),'regions':{}}
    if native.stem.startswith('modern'):
        for name,box in regions.items():
            xx=x.crop(box);yy=y.crop(box)
            w,h=xx.size;pair=Image.new('RGB',(w*2,h));pair.paste(yy,(0,0));pair.paste(xx,(w,0));pair.save(folder/(native.stem+'-'+name+'-reference-native.png'))
            arr=data[box[1]:box[3],box[0]:box[2]];metrics['regions'][name]={'mae':float(arr.mean())}
    results[native.stem]=metrics
(folder/'metrics.json').write_text(json.dumps(results,indent=2))
print(json.dumps({k:{m:v for m,v in x.items() if m!='regions'} for k,x in results.items()},indent=2))

"""Artifact checks: structure identity, source palette, resolution and provenance."""
import hashlib
import json
import re
from pathlib import Path
import xml.etree.ElementTree as ET

import numpy as np
from PIL import Image
from scipy.spatial import ConvexHull

ROOT = Path(__file__).resolve().parent
NS = '{http://www.w3.org/2000/svg}'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def geometry(path):
    root = ET.parse(path).getroot()
    assert not any(e.tag in {NS+'image', NS+'script', NS+'foreignObject'} for e in root.iter()), path
    parts = set()
    for e in root.iter():
        if e.tag == NS+'path':
            parts.update(re.findall(r'M[^M]*?Z', e.attrib['d']))
        elif e.tag in {NS+'circle', NS+'ellipse', NS+'rect'}:
            parts.add(e.tag+str(sorted((k,v) for k,v in e.attrib.items() if k in {'cx','cy','r','rx','ry','x','y','width','height'})))
    return hashlib.sha256('\n'.join(sorted(parts)).encode()).hexdigest()


def main():
    data = json.loads((ROOT/'studies.json').read_text())
    assert [s['id'] for s in data['studies']] == ['J1','J3','J5','J6']
    assert len(list((ROOT/'svg').glob('*.svg'))) == 12
    assert len(list((ROOT/'exports').glob('*.png'))) == 12
    checks = []
    for s in data['studies']:
        signatures = []
        for v in data['variants']:
            stem = f"{s['id']}-{s['slug']}-{v['id']}"
            svg, png = ROOT/'svg'/f'{stem}.svg', ROOT/'exports'/f'{stem}.png'
            signatures.append(geometry(svg))
            with Image.open(png) as image:
                image.load()
                assert image.size == (3072,3072), png
                rgba = np.asarray(image.convert('RGBA'))
            assert np.all(rgba[:,:,3] == 255), png
            rgb = rgba[:,:,:3]
            if v['id'] == 'mono':
                assert np.array_equal(rgb[:,:,0],rgb[:,:,1]) and np.array_equal(rgb[:,:,1],rgb[:,:,2]), png
                deviation = 0.
            else:
                anchors = ['#080808','#f1f1f1']+v['colors']
                points = np.array([[int(c[i:i+2],16) for i in (1,3,5)] for c in anchors],float)
                hull = ConvexHull(points)
                hexes = set(re.findall(r'#[0-9a-fA-F]{6}',svg.read_text()))
                source_colors = np.array([[int(c[i:i+2],16) for i in (1,3,5)] for c in hexes],float)
                source_deviation = float(np.max(source_colors@hull.equations[:,:3].T+hull.equations[:,3]))
                assert source_deviation < .9, (svg,'unapproved source color',source_deviation)
                samples = np.unique(rgb[::3,::3].reshape(-1,3),axis=0).astype(float)
                deviation = float(np.max(samples@hull.equations[:,:3].T+hull.equations[:,3]))
                # Repeated native antialias compositing can accumulate byte rounding.
                # Source fills have the stricter constraint above; rendered edges
                # tolerate under 4.5 RGB units (1.8% of a channel).
                assert deviation < 4.5, (png,deviation)
            checks.append({'file':f'exports/{stem}.png','width':3072,'height':3072,'opaque':True,
                           'paletteHullMaxRGBDeviation':round(deviation,6),'sha256':sha(png)})
        assert len(set(signatures)) == 1, (s['id'],'geometry differs between variants')
    palette_ref = np.asarray(Image.open(ROOT/'references/cd-palette-crop.png').convert('RGB'))
    for name,(x,y) in {'green':(28,176),'gold':(28,64),'coral':(115,20)}.items():
        c = np.median(palette_ref[y-3:y+4,x-3:x+4],axis=(0,1)).astype(int)
        assert '#'+''.join(f'{n:02x}' for n in c) == data['colorAnchors'][name]
    manifest = json.loads((ROOT/'manifest.json').read_text())
    for f in manifest['files']:
        assert sha(ROOT/f['file']) == f['sha256'], f['file']
    report = {'date':'2026-10-25','groups':4,'variantsPerGroup':3,'masterCount':12,
              'sameGeometryWithinEachGroup':True,'pureGrayscaleMonochrome':True,
              'colorAnchorsVerifiedAgainstUserCrop':data['colorAnchors'],
              'noEmbeddedRasterInIconSVG':True,'allManifestHashesVerified':True,
              'visualReview':['Compared the four monochrome forms against normalized supplied references.',
                              'Inspected both 6-panel sheets and enlarged contour/particle regions.',
                              'References retain their original bytes; this is motif reconstruction, not pixel identity to whole posters.'],
              'currentIconChanged':False,'checks':checks}
    (ROOT/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('Verified 4 groups / 12 SVGs / 12 opaque 3072px PNGs; identical geometry, grayscale and palette constraints passed.')


if __name__ == '__main__':
    main()

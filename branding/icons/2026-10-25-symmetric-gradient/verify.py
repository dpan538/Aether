"""Verify delivered images, reflection, source references and visible color ranges."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent
NS = '{http://www.w3.org/2000/svg}'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def rgb(path):
    return np.array(Image.open(path).convert('RGB'))

def main():
    data = json.loads((ROOT/'studies.json').read_text())
    assert [s['id'] for s in data['studies']] == ['J3','J5','J6']
    assert len(data['variants']) == 5 and data['count'] == 15
    for folder,ext in [('svg','svg'),('exports','png'),('previews','png'),('details','png')]:
        assert len(list((ROOT/folder).glob('*.'+ext))) == 15, folder
    checks = []
    for s in data['studies']:
        for v in data['variants']:
            stem = f"{s['id']}-{v['id']}"
            source = ET.parse(ROOT/'svg'/f'{stem}.svg').getroot()
            tags = [e.tag for e in source.iter()]
            assert not any(NS+x in tags for x in ['image','mask','feBlend','script','foreignObject'])
            uses = source.findall(NS+'use')
            assert len(uses) == 2
            assert uses[0].attrib['href'] == uses[1].attrib['href']
            assert uses[1].attrib['transform'] == 'translate(1024 0) scale(-1 1)'
            for table in source.iter(NS+'feFuncR'):
                if table.attrib.get('type') == 'table':
                    assert len(table.attrib['tableValues'].split()) == 256
            for folder,size in [('exports',(3072,3072)),('previews',(896,896))]:
                path = ROOT/folder/f'{stem}.png'
                im = Image.open(path).convert('RGBA')
                assert im.size == size
                a = np.array(im)
                assert np.all(a[:,:,3] == 255)
                assert np.array_equal(a,a[:,::-1]), (stem,'reflection differs')
            p = rgb(ROOT/'previews'/f'{stem}.png').astype(float)/255
            mx,mn = p.max(2),p.min(2)
            sat = np.divide(mx-mn,mx,out=np.zeros_like(mx),where=mx>0)
            # Pale highlights must exist in actual pixels; avoid magenta/acid tones.
            pink = (p[:,:,0]>.75)&(p[:,:,2]>p[:,:,1]+.015)&(p[:,:,0]>p[:,:,1]+.035)&(p[:,:,1]>.58)&(sat>.10)&(sat<.40)
            purple = (p[:,:,2]>.75)&(p[:,:,2]>p[:,:,0]+.015)&(p[:,:,0]>p[:,:,1]+.02)&(p[:,:,1]>.56)&(sat>.10)&(sat<.35)
            if v['id'] == '03': assert pink.sum()>75, stem
            if v['id'] == '04': assert purple.sum()>75, stem
            if v['id'] == '05': assert (pink|purple).sum()>75, stem
            checks.append({'id':stem,'size':[3072,3072],'mirrorDifferentPixels':0,
                           'pinkHighlightPixels':int(pink.sum()),'purpleHighlightPixels':int(purple.sum()),
                           'sha256':sha(ROOT/'exports'/f'{stem}.png')})
    for group,values in data['variations'].items():
        assert len(values)==5
        assert all(set(v)<= {'x','y','dy','rays'} for v in values), group
    original = ROOT.parent/'2026-10-25-fine-studies'
    assert np.array_equal(rgb(ROOT/'references/original-green-gold.png'),rgb(original/'previews/J3-axial-caustic-green-gold.png'))
    brightness=[]
    for group,slug in [('J3','axial-caustic'),('J5','phase-fold'),('J6','radial-phase')]:
        old=rgb(original/f'previews/{group}-{slug}-green-gold.png').astype(float)
        new=rgb(ROOT/f'previews/{group}-01.png').astype(float)
        if group=='J6':
            y,x=np.mgrid[:896,:896];mask=(x-447.5)**2+(y-447.5)**2 < (896*145/1024)**2
        else:mask=rgb(original/f'previews/{group}-{slug}-mono.png')[:,:,0]>24
        before=float(old.max(2)[mask].mean());after=float(new.max(2)[mask].mean())
        luma=np.array([.2126,.7152,.0722]);l0=float((old@luma)[mask].mean());l1=float((new@luma)[mask].mean())
        assert after>before*1.1,(group,'insufficient value increase',before,after)
        assert l1>l0,(group,'luminance did not increase',l0,l1)
        brightness.append({'id':group,'originalMeanMaxRGB':round(before,3),'newMeanMaxRGB':round(after,3),
                           'originalMeanLuminance':round(l0,3),'newMeanLuminance':round(l1,3)})
    manifest=json.loads((ROOT/'manifest.json').read_text())
    for item in manifest['files']:assert sha(ROOT/item['file'])==item['sha256'],item['file']
    assert sha(original/'contours.json')==manifest['geometrySha256']
    icon=ROOT.parent/'current/app-icon.png'
    assert sha(icon)=='00ed21739c8243aecc8b4c7d7b5d91361b34edc448464b8b2978bb4a7d53a48a'
    before=rgb(ROOT/'references/prior-soft-J3.png').astype(float)
    after=rgb(ROOT/'previews/J3-03.png').astype(float)
    y=np.indices(before.shape[:2])[0]
    green=(before[:,:,1]>before[:,:,0]*1.14)&(y>400)&(before.max(2)>25)
    beforeGreen=float(before.max(2)[green].mean());afterGreen=float(after.max(2)[green].mean())
    assert afterGreen>beforeGreen*1.15
    assert 'flow-' in (ROOT/'svg/J3-03.svg').read_text()
    report={'date':'2026-10-25','groups':3,'count':15,'exactPNGHorizontalReflection':True,
            'nativeSVGSharedHalf':True,'colorOverlayMasks':False,'originalReferencePixelsUnchanged':True,
            'currentE1Changed':False,'brightnessComparison':brightness,
            'axialFlowLowerGreenMeanMaxRGB':{'before':round(beforeGreen,3),'after':round(afterGreen,3)},'checks':checks}
    (ROOT/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('Verified 15 SVG / 15 opaque 3072px PNG; exact mirrored pixels, brighter base, visible pale highlights and hashes.')
    print(json.dumps(brightness,ensure_ascii=False))

if __name__=='__main__':main()

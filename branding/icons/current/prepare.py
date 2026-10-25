"""Package the user-selected Canvas logo from its frozen approved masters."""
import hashlib
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent
DENSITIES = {"mdpi":48,"hdpi":72,"xhdpi":96,"xxhdpi":144,"xxxhdpi":192}

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def describe(path):
    item={"file":path.relative_to(ROOT).as_posix(),"bytes":path.stat().st_size,"sha256":sha(path)}
    if path.suffix=='.png':
        with Image.open(path) as im:item.update(width=im.width,height=im.height,mode=im.mode)
    return item

def main():
    selection=json.loads((ROOT/'selection.json').read_text())
    approved=(ROOT/selection['approvedSource']).resolve()
    for name,spec in selection['masters'].items():
        if sha(ROOT/name)!=spec['sha256'] or sha(approved/spec['approvedFile'])!=spec['sha256']:
            raise ValueError(f'Selected master changed: {name}')
    paths=[ROOT/name for name in selection['masters']]
    with Image.open(ROOT/'app-icon.png') as source:
        if source.size!=(3072,3072) or source.mode!='RGBA':raise ValueError('Unexpected approved master')
        for density,size in DENSITIES.items():
            target=ROOT/'android-legacy'/f'mipmap-{density}'/'ic_launcher.png'
            target.parent.mkdir(parents=True,exist_ok=True)
            source.resize((size,size),Image.Resampling.LANCZOS).save(target,optimize=True)
            paths.append(target)
        target=ROOT/'preview-512.png';source.resize((512,512),Image.Resampling.LANCZOS).save(target,optimize=True);paths.append(target)
    with Image.open(ROOT/'app-icon-square.png') as source:
        target=ROOT/'preview-square-512.png';source.resize((512,512),Image.Resampling.LANCZOS).save(target,optimize=True);paths.append(target)
    paths += [ROOT/'selection.json',ROOT/'README.md',ROOT/'prepare.py']
    manifest={"selectedDate":selection['selectedDate'],"status":selection['status'],"canonicalVariant":"99-ring",
              "parameters":selection['parameters'],"origin":"User-provided Canvas concept, refined native mathematical geometry; no imagegen",
              "approvedSource":selection['approvedSource'],"supersededE1":"../archive/2026-10-25-provisional-E1/app-icon.png",
              "transform":"Frozen masters copied byte-for-byte; derivative PNGs only resized with Lanczos",
              "files":[describe(p) for p in paths],"launcherValidation":"Android task must integrate and verify actual launcher masking/display separately"}
    (ROOT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print('Packaged approved 99-ring masters and density assets; frozen source hashes verified.')

if __name__=='__main__':main()

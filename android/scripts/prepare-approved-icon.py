"""Copy the approved 99-ring identity into Android; never redraw at fixed dp stroke."""
from pathlib import Path
import hashlib,json,shutil
root=Path(__file__).resolve().parents[1];brand=root.parent/'branding/icons/current';res=root/'app/src/main/res'
selection=json.loads((brand/'selection.json').read_text());assert selection['canonicalVariant']=='99-ring'
for name,spec in selection['masters'].items():assert hashlib.sha256((brand/name).read_bytes()).hexdigest()==spec['sha256']
files=[]
for p in (brand/'android-legacy').glob('mipmap-*/ic_launcher.png'):
 target=res/p.parent.name/p.name;shutil.copyfile(p,target);files.append({'source':str(p.relative_to(root.parent)),'target':str(target.relative_to(root)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
p=brand/'preview-square-512.png';target=res/'drawable-nodpi/icon_canvas_99.png';shutil.copyfile(p,target);files.append({'source':str(p.relative_to(root.parent)),'target':str(target.relative_to(root)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
output=root/'verification/evidence/icon99-assets.json';output.write_text(json.dumps({'canonicalVariant':'99-ring','sourceSelection':str((brand/'selection.json').relative_to(root.parent)),'foregroundSource':'Unmasked square master derivative, byte-for-byte; one system mask','adaptiveInsetFraction':.169,'files':files},indent=2)+'\n')
print('Verified approved masters; copied five legacy densities and unmasked adaptive input.')

"""Record inspected release provenance without private paths or signing material."""
from pathlib import Path
import argparse,hashlib,json,subprocess
from xml.etree import ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--prepared',default='verification/local/delivery-pcm-final');p.add_argument('--release-commit');p.add_argument('--device',default='verification/local/final/modern-ink.json');p.add_argument('--output',default='verification/BUILD.json');a=p.parse_args()
root=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
sources=[]
for folder in ['app/src','scripts','gradle','third-party']:
    sources += [p for p in (root/folder).rglob('*') if p.is_file() and '__pycache__' not in p.parts]
sources += [root/p for p in ['build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat','app/build.gradle.kts','app/gradle.lockfile']]
files={str(p.relative_to(root)):sha(p) for p in sorted(sources)}
digest=hashlib.sha256(''.join(k+'\0'+v+'\n' for k,v in files.items()).encode()).hexdigest()
prepared=root/a.prepared;inspection=json.loads((prepared/'inspection.json').read_text());identity=inspection['manifest'];apk=prepared/inspection['apk']
assert sha(apk)==identity['sha256'] and inspection['signatureVerified'] and not inspection['debuggable']
suites=[ET.parse(p).getroot() for p in (root/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
lint=ET.parse(root/'app/build/reports/lint-results-release.xml').getroot()
frozen=root.parent/'design/snapshots/2026-10-24-two-page-baseline'
disc=(frozen/'source/design/immersive/disc.js').read_text().split('const fragmentSource = `')[1].split('`;')[0].encode()
native=(root/'app/src/main/assets/disc.frag').read_bytes();assert native==disc
snapshot=json.loads((frozen/'manifest.json').read_text())
for item in snapshot['files']:assert sha(frozen/item['file'])==item['sha256'],item['file']
device=json.loads((root/a.device).read_text())
manifest={
 'releaseSourceCommit':a.release_commit,
 'version':identity['versionName'],'versionCode':identity['versionCode'],'applicationId':identity['packageName'],
 'gitBaseCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
 'sourceSha256':digest,'sourceDigestRecipe':'SHA256 of sorted relative-path + NUL + per-file SHA256 + newline; excludes verification, caches and build outputs',
 'apk':{'releaseAsset':inspection['apk'],'bytes':apk.stat().st_size,'sha256':sha(apk),'variant':'release','debuggable':False,'signatureVerified':True,'certificateSha256':identity['certificateSha256']},
 'toolchain':{'gradle':'8.13','agp':'8.13.2','kotlin':'2.2.0','jdk':'21','compileSdk':36,'targetSdk':36,'minSdk':31},
 'unitTests':{k:sum(int(unit.get(k)) for unit in suites) for k in ['tests','failures','errors','skipped']},
 'lint':{s:sum(x.get('severity')==s for x in lint.findall('issue')) for s in ['Error','Warning','Hint']},
 'device':{k:device[k] for k in ['model','api','width','height','density','densityDpi','fontScale','insets']},
 'shaderSha256':hashlib.sha256(native).hexdigest(),'shaderMatchesFrozenSource':True,
 'frozenManifestSha256':sha(frozen/'manifest.json'),'frozenFilesVerified':len(snapshot['files']),
 'sourceFiles':files}
(root/a.output).write_text(json.dumps(manifest,indent=2)+'\n')
print('APK',manifest['apk']['sha256'],'source',digest)

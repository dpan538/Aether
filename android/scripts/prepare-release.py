"""Inspect the exact signed APK; prepare immutable GitHub release attachments."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,subprocess
p=argparse.ArgumentParser();p.add_argument('--apk',type=Path,default=Path('app/build/outputs/apk/release/app-release.apk'));p.add_argument('--out',type=Path,default=Path('verification/local/publish'));a=p.parse_args()
sdk=Path(os.environ['ANDROID_HOME']);tools=sdk/'build-tools/36.0.0'
environment=os.environ.copy();environment['PATH']=str(Path(os.environ['JAVA_HOME'])/'bin')+os.pathsep+environment['PATH']
certificate=subprocess.check_output([str(tools/'apksigner'),'verify','--verbose','--print-certs',str(a.apk)],env=environment,text=True)
certs=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)',certificate)
assert len(certs)==1,'Exactly one verified signer is required'
badging=subprocess.check_output([str(tools/'aapt2'),'dump','badging',str(a.apk)],env=environment,text=True)
match=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging)
assert match and match[1]=='io.github.dpan538.aether','Incorrect permanent package identity'
assert 'application-debuggable' not in badging,'Do not publish a debuggable APK'
version=match[3];assert re.fullmatch(r'[A-Za-z0-9._-]+',version)
a.out.mkdir(parents=True,exist_ok=True)
name='Aether-'+version+'.apk';target=a.out/name
if target.exists():assert target.read_bytes()==a.apk.read_bytes(),'Refusing to replace an already prepared artifact; use a fresh output folder'
else:shutil.copyfile(a.apk,target)
sha=hashlib.sha256(target.read_bytes()).hexdigest()
manifest={'schemaVersion':1,'packageName':match[1],'versionCode':int(match[2]),'versionName':version,'apk':name,'sha256':sha,'certificateSha256':certs[0].lower()}
(a.out/'aether-update.json').write_text(json.dumps(manifest,indent=2)+'\n')
(a.out/'SHA256SUMS.txt').write_text(sha+'  '+name+'\n')
(a.out/'inspection.json').write_text(json.dumps({'apk':name,'bytes':target.stat().st_size,'manifest':manifest,'debuggable':False,'signatureVerified':True},indent=2)+'\n')
print(json.dumps(manifest,indent=2))

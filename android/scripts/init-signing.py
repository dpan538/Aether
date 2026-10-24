"""Create Aether's own local preview distribution identity once. Never publish secrets."""
from pathlib import Path
import os,secrets,subprocess
root=Path(__file__).resolve().parents[1]
folder=root/'.tooling/signing';folder.mkdir(parents=True,exist_ok=True);folder.chmod(0o700)
properties=folder/'keystore.properties';key=folder/'aether-preview.p12'
if properties.exists():
    if not key.exists():raise SystemExit('Signing config exists but its key is missing. Restore the original key; do not generate a replacement.')
    print('Existing Aether signing identity retained.');raise SystemExit(0)
if key.exists():raise SystemExit('Key exists without configuration. Restore its original configuration.')
password=secrets.token_urlsafe(36)
environment=os.environ.copy();environment['AETHER_LOCAL_SIGNING_PASSWORD']=password
java=Path(os.environ['JAVA_HOME'])/'bin/keytool'
subprocess.run([str(java),'-genkeypair','-storetype','PKCS12','-keystore',str(key),'-storepass:env','AETHER_LOCAL_SIGNING_PASSWORD','-keypass:env','AETHER_LOCAL_SIGNING_PASSWORD','-alias','aether-preview','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Aether Preview Distribution'],env=environment,check=True,capture_output=True)
key.chmod(0o600)
properties.write_text('storeFile=.tooling/signing/aether-preview.p12\nstorePassword='+password+'\nkeyAlias=aether-preview\nkeyPassword='+password+'\n');properties.chmod(0o600)
print('Created the independent Aether preview signing identity in ignored local tooling. Back it up to retain in-place updates.')

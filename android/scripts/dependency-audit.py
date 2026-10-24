"""Query OSV for exact locked Maven versions; an empty result is not a security guarantee."""
from pathlib import Path
import datetime,json,urllib.request
root=Path(__file__).resolve().parents[1]
packages=[]
for line in (root/'app/gradle.lockfile').read_text().splitlines():
    name,_,scopes=line.partition('=')
    if not scopes or name.startswith('#') or name=='empty':continue
    group,artifact,version=name.split(':')
    packages.append({'package':{'ecosystem':'Maven','name':group+':'+artifact},'version':version})
endpoint='https://api.osv.dev/v1/querybatch'
request=urllib.request.Request(endpoint,data=json.dumps({'queries':packages}).encode(),headers={'Content-Type':'application/json'})
with urllib.request.urlopen(request,timeout=45) as response:results=json.load(response)['results']
assert len(results)==len(packages)
payload={'source':endpoint,'queriedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'packages':[dict(p,result=r) for p,r in zip(packages,results)],'matchedAdvisories':sum(len(r.get('vulns',[])) for r in results)}
out=root/'verification/local/security';out.mkdir(parents=True,exist_ok=True)
(out/'osv-dependencies.json').write_text(json.dumps(payload,indent=2)+'\n')
print(len(packages),'locked versions;',payload['matchedAdvisories'],'matched advisories')

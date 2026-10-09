from pathlib import Path
import json,zipfile,hashlib,subprocess
P=Path('/private/tmp/sora-bc-audit-20260925');ns={};exec((P/'audit.py').read_text().split('rows=[]')[0],ns);parse=ns['parse']
R=json.loads((P/'report.json').read_text());arts={x['coordinate']:x['path'] for x in R['artifacts']};jdk=Path('/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home')
def classdefs(path):
 out={}
 with zipfile.ZipFile(path) as z:
  for n in z.namelist():
   if n.endswith('.class') and not n.startswith('META-INF/versions/') and not n.endswith('module-info.class'):
    c=parse(z.read(n));out[c['name']]=c
 return out
J=classdefs(jdk/'jmods/java.base.jmod')
def member(defs,owner,kind,name,desc,seen=None):
 seen=set() if seen is None else seen
 if owner not in defs or owner in seen:return None
 seen.add(owner);c=defs[owner]
 for k,n,d,a in c['defs']:
  if (k,n,d)==(kind,name,desc):return owner
 if name in ('<init>','<clinit>'):return None
 for parent in ([c['super']] if c['super'] else [])+c['interfaces']:
  value=member(defs,parent,kind,name,desc,seen)
  if value:return value
 return None
provider=classdefs(arts['org.bouncycastle:bcprov-jdk18on:1.78.1'])
allrefs=R['references'];results=[]
for jv,uv in [('0.7.34','1.71'),('0.7.34','1.78.1'),('0.7.38','1.78.1'),('0.7.42','1.78.1')]:
 up=Path(arts['org.bouncycastle:bcutil-jdk18on:1.71']) if uv=='1.71' else P/'upstream'/f'bcutil-jdk18on-{uv}.jar'
 jp=Path(arts['org.jmrtd:jmrtd:0.7.34']) if jv=='0.7.34' else P/'upstream'/f'jmrtd-{jv}.jar'
 utils=classdefs(up);jmrtd=classdefs(jp);defs=J|provider|utils
 refs=[r for r in allrefs if r['artifact'] not in ('org.bouncycastle:bcutil-jdk18on:1.71','org.jmrtd:jmrtd:0.7.34')]
 for coordinate,contents in [(f'org.bouncycastle:bcutil-jdk18on:{uv}',utils),(f'org.jmrtd:jmrtd:{jv}',jmrtd)]:
  for c in contents.values():
   for kind,owner,name,desc,tag in c['refs']:
    refs.append({'artifact':coordinate,'sourceClass':c['name'],'kind':kind,'owner':owner,'name':name,'descriptor':desc,'constantTag':tag})
 missing=[]
 for r in refs:
  if member(defs,r['owner'],r['kind'],r['name'],r['descriptor']) is None:missing.append(r)
 # Check all shipped consumers' JMRTD method symbols against the proposed JMRTD version.
 ns['PREFIX']='org/jmrtd/';jmrefs=[]
 for a in R['artifacts']:
  if a['coordinate'].startswith(('org.jmrtd:', 'org.bouncycastle:')) or 'path' not in a or not a['path'].endswith('.jar'):continue
  with zipfile.ZipFile(a['path']) as z:
   for n in z.namelist():
    if not n.endswith('.class') or n.startswith('META-INF/versions/'):continue
    b=z.read(n)
    if b'org/jmrtd/' not in b:continue
    c=parse(b)
    for kind,owner,name,desc,tag in c['refs']:
     jmrefs.append({'artifact':a['coordinate'],'sourceClass':c['name'],'kind':kind,'owner':owner,'name':name,'descriptor':desc,'resolvedIn':member(J|jmrtd,owner,kind,name,desc)})
 ns['PREFIX']='org/bouncycastle/'
 out={'jmrtd':jv,'bcutil':uv,'unresolvedBcRefs':missing,'jmrtdConsumerRefs':jmrefs}
 results.append(out)
 from collections import Counter
 print('variant',jv,uv,'unresolvedBC',dict(Counter(r['artifact'] for r in missing)),'jmrtdRefs',len(jmrefs),'unresolvedJmrtd',len([r for r in jmrefs if r['resolvedIn'] is None]))
 cp=':'.join([str(P),arts['org.bouncycastle:bcprov-jdk18on:1.78.1'],str(up),str(jp)])
 cmd=[str(jdk/'bin/java'),'-cp',cp,'BcCompatibilityProbe']
 r=subprocess.run(cmd,text=True,capture_output=True);log=r.stdout+r.stderr
 (P/f'runtime-{jv}-{uv}.log').write_text(log)
 print(log)
(P/'variant-comparison.json').write_text(json.dumps(results,indent=2)+'\n')

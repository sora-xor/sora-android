import struct,zipfile,hashlib,json,io,re
from pathlib import Path
BASE=Path('/Users/takemiyamakoto/dev/sora-wallet/sora-android-release-candidate-20260924')
OUT=Path('/private/tmp/sora-bc-audit-20260925')
PREFIX='org/bouncycastle/'
class Reader:
 def __init__(self,b):self.b=b;self.p=0
 def take(self,n):v=self.b[self.p:self.p+n];self.p+=n;return v
 def u1(self):return self.take(1)[0]
 def u2(self):return struct.unpack('>H',self.take(2))[0]
 def u4(self):return struct.unpack('>I',self.take(4))[0]
def parse(b):
 r=Reader(b)
 if r.u4()!=0xcafebabe:raise ValueError('class magic')
 minor,major=r.u2(),r.u2();n=r.u2();cp=[None]*n;i=1
 while i<n:
  t=r.u1()
  if t==1:cp[i]=(t,r.take(r.u2()).decode('utf-8',errors='replace'))
  elif t in (3,4):cp[i]=(t,r.take(4))
  elif t in (5,6):cp[i]=(t,r.take(8));i+=1
  elif t in (7,8,16,19,20):cp[i]=(t,r.u2())
  elif t in (9,10,11,12,17,18):cp[i]=(t,r.u2(),r.u2())
  elif t==15:cp[i]=(t,r.u1(),r.u2())
  else:raise ValueError('tag '+str(t))
  i+=1
 def utf(i):return cp[i][1]
 def cls(i):return utf(cp[i][1]) if i else None
 access=r.u2();name=cls(r.u2());super_=cls(r.u2());interfaces=[cls(r.u2()) for _ in range(r.u2())]
 def attrs():
  for _ in range(r.u2()):r.u2();r.take(r.u4())
 defs=[]
 for kind in ('field','method'):
  for _ in range(r.u2()):
   flags=r.u2();nm=utf(r.u2());desc=utf(r.u2());defs.append((kind,nm,desc,flags));attrs()
 attrs()
 refs=[];classes=set();strings=[]
 for e in cp[1:]:
  if not e:continue
  if e[0] in (9,10,11):
   owner=cls(e[1]);nt=cp[e[2]]
   if owner.startswith(PREFIX):refs.append(('field' if e[0]==9 else 'method',owner,utf(nt[1]),utf(nt[2]),e[0]))
  elif e[0]==7:
   v=utf(e[1]);classes.update(re.findall(r'L(org/bouncycastle/[^;]+);',v) if v.startswith('[') else [v] if v.startswith(PREFIX) else [])
  elif e[0]==8:
   v=utf(e[1])
   if v.startswith('org.bouncycastle.'):strings.append(v)
 # descriptors appearing only in method signatures also require classes to link when reflection examines them
 for e in cp[1:]:
  if e and e[0]==1:classes.update(re.findall(r'L(org/bouncycastle/[^;<>]+);',e[1]))
 return {'name':name,'super':super_,'interfaces':interfaces,'access':access,'defs':defs,'refs':refs,'classes':sorted(classes),'reflectiveStrings':strings,'major':major}
rows=[]
for line in (OUT/'classpaths.log').read_text().splitlines():
 if line.startswith('BC_AUDIT\t'):
  _,scope,coord,path=line.split('\t');rows.append((scope,coord,Path(path)))
assert rows,'no Gradle artifacts'
runtime=[(coord,p) for scope,coord,p in rows if scope=='productionReleaseRuntimeClasspath']
for p in sorted(BASE.glob('*/build/intermediates/runtime_library_classes_jar/productionRelease/*/classes.jar')):runtime.append((f'project:{p.relative_to(BASE).parts[0]}',p))
# App's compiled output is an ordinary directory, included separately below.
appdirs=[BASE/'app/build/tmp/kotlin-classes/productionRelease',BASE/'app/build/intermediates/javac/productionRelease/compileProductionReleaseJavaWithJavac/classes']
artifacts=[];classes={};origins={};users=[];dupes=[]
for coord,p in runtime:
 data=p.read_bytes();entry={'coordinate':coord,'path':str(p),'sha256':hashlib.sha256(data).hexdigest(),'size':len(data),'classCount':0,'bcRefClassCount':0}
 with zipfile.ZipFile(io.BytesIO(data)) as z:
  for name in z.namelist():
   if not name.endswith('.class') or name.startswith('META-INF/versions/') or name=='module-info.class':continue
   entry['classCount']+=1;b=z.read(name)
   if b'org/bouncycastle/' not in b:continue
   c=parse(b);entry['bcRefClassCount']+=1
   if c['name'].startswith(PREFIX):
    if c['name'] in classes:dupes.append((c['name'],origins[c['name']],coord))
    classes[c['name']]=c;origins[c['name']]=coord
   users.append((coord,c))
 artifacts.append(entry)
for d in appdirs:
 if not d.exists():continue
 hashes=[];count=0;bc=0
 for p in sorted(d.rglob('*.class')):
  b=p.read_bytes();hashes.append(str(p.relative_to(d))+' '+hashlib.sha256(b).hexdigest());count+=1
  if b'org/bouncycastle/' in b:users.append(('project:app',parse(b)));bc+=1
 artifacts.append({'coordinate':'project:app','path':str(d),'treeSha256':hashlib.sha256('\n'.join(hashes).encode()).hexdigest(),'classCount':count,'bcRefClassCount':bc})
def resolve(owner,kind,name,desc,seen=None):
 if owner=='java/lang/Object' and kind=='method' and name in ('equals','hashCode','toString','getClass','clone','wait','notify','notifyAll','finalize'):return 'java/lang/Object'
 if owner not in classes:return None
 seen=set() if seen is None else seen
 if owner in seen:return None
 seen.add(owner);c=classes[owner]
 for k,n,d,a in c['defs']:
  if (k,n,d)==(kind,name,desc):return owner
 if name in ('<init>','<clinit>'):return None
 for parent in ([c['super']] if c['super'] else [])+c['interfaces']:
  got=resolve(parent,kind,name,desc,seen)
  if got:return got
 return None
missing=[];missingclasses=[];refs=[];refclasses=[];reflection=[]
for coord,c in users:
 for owner in c['classes']:
  if owner not in classes:missingclasses.append({'artifact':coord,'sourceClass':c['name'],'targetClass':owner})
 for kind,owner,name,desc,tag in c['refs']:
  r={'artifact':coord,'sourceClass':c['name'],'kind':kind,'owner':owner,'name':name,'descriptor':desc,'constantTag':tag,'resolvedIn':resolve(owner,kind,name,desc)}
  refs.append(r)
  if r['resolvedIn'] is None:missing.append(r)
 if c['refs']:refclasses.append({'artifact':coord,'sourceClass':c['name'],'references':len(c['refs'])})
 if c['reflectiveStrings']:reflection.append({'artifact':coord,'sourceClass':c['name'],'strings':c['reflectiveStrings']})
report={'scope':'Production Release resolved external classpath plus existing compiled Production Release project jars/app classes; excludes multi-release JAR overlays (Android base classes)','artifacts':artifacts,'runtimeBcClassCount':len(classes),'duplicateBcClasses':dupes,'referenceCount':len(refs),'missingMembers':missing,'missingClasses':missingclasses,'references':refs,'referencingClasses':refclasses,'reflectiveStrings':reflection}
(OUT/'report.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k not in ('references','artifacts','referencingClasses','reflectiveStrings')},indent=2))
print('artifacts',len(artifacts),'referencing classes',len(refclasses))
print('external referencing coordinates:',sorted(set(r['artifact'] for r in refclasses if not r['artifact'].startswith('org.bouncycastle:'))))

"""Read member/constant metadata only; skip every Code and other method attribute."""
from pathlib import Path
import hashlib,json,struct,zipfile
JAR=Path('/workspace/Refork-audit/work/master-v5/upstream/bettercubicchunks-fabric-0.1.1+mc1.21.1.jar')
class Reader:
 def __init__(self,b):self.b=b;self.i=0
 def take(self,n):r=self.b[self.i:self.i+n];self.i+=n;return r
 def u1(self):return self.take(1)[0]
 def u2(self):return struct.unpack('>H',self.take(2))[0]
 def u4(self):return struct.unpack('>I',self.take(4))[0]
def members(b):
 r=Reader(b);assert r.u4()==0xcafebabe;r.take(4);n=r.u2();cp=[None]*n;i=1
 while i<n:
  tag=r.u1()
  if tag==1:cp[i]=r.take(r.u2()).decode('utf8',errors='replace')
  elif tag==3:cp[i]=struct.unpack('>i',r.take(4))[0]
  elif tag==4:cp[i]=struct.unpack('>f',r.take(4))[0]
  elif tag in [5,6]:cp[i]=struct.unpack('>q' if tag==5 else '>d',r.take(8))[0];i+=1
  elif tag in [7,8,16,19,20]:cp[i]=(tag,r.u2())
  elif tag in [9,10,11,12,17,18]:r.take(4)
  elif tag==15:r.take(3)
  else:raise ValueError(tag)
  i+=1
 r.take(6);r.take(2*r.u2());out={}
 for category in ['fields','methods']:
  items=[]
  for _ in range(r.u2()):
   flags=r.u2();name=cp[r.u2()];desc=cp[r.u2()];row=dict(name=name,descriptor=desc,flags=flags)
   for _ in range(r.u2()):
    key=cp[r.u2()];length=r.u4()
    if category=='fields' and key=='ConstantValue':
     assert length==2;v=cp[r.u2()];row['constantValue']=cp[v[1]] if isinstance(v,tuple) and v[0]==8 else v
    else:r.take(length)
   items.append(row)
  out[category]=items
 return out
names=['core/CubePos','config/BccConfig','server/CubicServerLevel','server/tracking/CubeTrackingView','server/tracking/CubeViewTracker','server/net/ServerNetHandlers$ICubicServerPlayer','server/net/ServerNetHandlers','chunk/CubicColumnState','client/ClientCubes','server/storage/CubeStorage']
with zipfile.ZipFile(JAR) as z:
 classes={'dev/fallingcloud/bettercubicchunks/'+n:members(z.read('dev/fallingcloud/bettercubicchunks/'+n+'.class')) for n in names}
d=dict(scope='Member names/descriptors/access flags and field ConstantValue metadata only; all Code attributes skipped, no Java execution or upstream implementation copied',artifact=dict(path=str(JAR),bytes=JAR.stat().st_size,sha256=hashlib.sha256(JAR.read_bytes()).hexdigest()),classes=classes)
Path('work/cubic-fluid-unknown/native-interest-api.json').write_text(json.dumps(d,indent=2)+'\n')
for n,c in classes.items():
 print(n)
 for kind,rows in c.items():
  selected=rows if n.endswith(('CubeTrackingView','CubeViewTracker','BccConfig','ICubicServerPlayer')) else [x for x in rows if any(k in x['name'].lower() for k in ['tick','full','status','column','cube','view','distance','load','force','temporary','lookahead','evict','remove','contains','inside','bound'])]
  for row in selected:print(kind,row)

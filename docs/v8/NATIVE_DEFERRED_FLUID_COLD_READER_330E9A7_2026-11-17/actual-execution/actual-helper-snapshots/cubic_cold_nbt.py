"""Read saved fixture NBT without editing it or creating game state."""
import gzip,struct
from pathlib import Path
class Reader:
 def __init__(self,data):self.data=data;self.i=0
 def take(self,n):
  if n<0 or self.i+n>len(self.data):raise ValueError('truncated NBT')
  b=self.data[self.i:self.i+n];self.i+=n;return b
 def scalar(self,f):return struct.unpack('>'+f,self.take(struct.calcsize('>'+f)))[0]
 def text(self):return self.take(self.scalar('H')).decode('utf8')
 def tag(self,t):
  if t in [1,2,3,4,5,6]:return self.scalar({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7:return self.take(self.scalar('i')).hex()
  if t==8:return self.text()
  if t==9:
   k=self.scalar('B');n=self.scalar('i');return [self.tag(k) for _ in range(n)]
  if t==10:
   out={}
   while True:
    k=self.scalar('B')
    if k==0:return out
    name=self.text();out[name]=self.tag(k)
  if t in [11,12]:return [self.scalar('i' if t==11 else 'q') for _ in range(self.scalar('i'))]
  raise ValueError('unknown NBT tag '+str(t))
def read_bytes(blob):
 raw=gzip.decompress(blob);r=Reader(raw);kind=r.scalar('B');name=r.text();value=r.tag(kind)
 if r.i!=len(raw):raise ValueError('trailing NBT data')
 return value
def read(path):return read_bytes(Path(path).read_bytes())

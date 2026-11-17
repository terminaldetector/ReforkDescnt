"""Read actual cached class bytes; no Java process, class loading or mutation."""
from pathlib import Path
import hashlib,json,struct,zipfile

ROOT=Path(__file__).resolve().parents[2]
inputs=json.loads((ROOT/'work/city-runtime/packetdiag-first-4631871-20261117T164602Z/runGametest-actual-inputs.json').read_text())
mc=next(Path(x['path']) for x in inputs['classpath'] if 'minecraft-merged' in x['path'])
netty=next(Path(x['path']) for x in inputs['classpath'] if '/netty-transport/' in x['path'])

class ClassFile:
    def __init__(self,data):
        self.data=data;self.p=8;count=self.u2();self.cp={};i=1
        while i<count:
            tag=self.u1()
            if tag==1:
                n=self.u2();value=data[self.p:self.p+n].decode(errors='replace');self.p+=n
            elif tag in (7,8,16,19,20):value=self.u2()
            elif tag in (9,10,11,12,17,18):value=(self.u2(),self.u2())
            elif tag in (3,4):value=self.u4()
            elif tag in (5,6):value=(self.u4(),self.u4());self.cp[i]=(tag,value);i+=1
            elif tag==15:value=(self.u1(),self.u2())
            else:raise ValueError(tag)
            self.cp[i]=(tag,value);i+=1
        self.u2();self.this=self.name(self.u2());self.super=self.name(self.u2());interfaces=self.u2();self.p+=2*interfaces
        self.fields=[];self.methods=[]
        for out in (self.fields,self.methods):
            for _ in range(self.u2()):
                access,name,desc=self.u2(),self.name(self.u2()),self.name(self.u2());attrs={}
                for _ in range(self.u2()):
                    key=self.name(self.u2());n=self.u4();attrs[key]=data[self.p:self.p+n];self.p+=n
                out.append((name,desc,attrs))
    def u1(self):v=self.data[self.p];self.p+=1;return v
    def u2(self):v=struct.unpack_from('>H',self.data,self.p)[0];self.p+=2;return v
    def u4(self):v=struct.unpack_from('>I',self.data,self.p)[0];self.p+=4;return v
    def name(self,index):
        if not index:return '<none>'
        tag,value=self.cp[index]
        if tag==1:return value
        if tag in (7,8,16):return self.name(value)
        if tag==12:return self.name(value[0])+self.name(value[1])
        if tag in (9,10,11):return self.name(value[0])+'.'+self.name(value[1])
        if tag in (17,18):return 'dynamic:'+self.name(value[1])
        return repr(value)
    def code(self,method,refs_only=False):
        raw=method[2].get('Code')
        if not raw:return []
        n=struct.unpack_from('>I',raw,4)[0];code=raw[8:8+n];i=0;out=[]
        names={178:'getstatic',179:'putstatic',180:'getfield',181:'putfield',182:'invokevirtual',183:'invokespecial',184:'invokestatic',185:'invokeinterface',186:'invokedynamic',187:'new',189:'anewarray',192:'checkcast',193:'instanceof',3:'iconst_0',4:'iconst_1',42:'aload_0',43:'aload_1',44:'aload_2',45:'aload_3',177:'return',176:'areturn',172:'ireturn',154:'ifne',153:'ifeq',167:'goto',198:'ifnull',199:'ifnonnull'}
        while i<len(code):
            at=i;op=code[i];length=1;detail=''
            if op in (16,18,21,22,23,24,25,54,55,56,57,58,169,188):length=2
            elif op in (17,19,20,132,*range(153,169),178,179,180,181,182,183,184,187,189,192,193,198,199):length=3
            elif op in (185,186,200,201):length=5
            elif op==197:length=4
            elif op==196:length=6 if code[i+1]==132 else 4
            elif op in (170,171):
                start=i+1+(-i-1)%4
                if op==170:
                    low,high=struct.unpack_from('>ii',code,start+4);length=start-i+12+(high-low+1)*4
                else:length=start-i+8+struct.unpack_from('>i',code,start+4)[0]*8
            if op in (178,179,180,181,182,183,184,185,186,187,189,192,193):detail=self.name(struct.unpack_from('>H',code,i+1)[0])
            elif op in (18,19,20):detail=self.name(code[i+1] if op==18 else struct.unpack_from('>H',code,i+1)[0])
            elif op in (*range(153,169),198,199):detail='->'+str(at+struct.unpack_from('>h',code,i+1)[0])
            if not refs_only or detail:out.append(f'{at:04d} {names.get(op,hex(op))} {detail}')
            i+=length
        assert i==len(code)
        return out

targets=[(mc,'net/minecraft/server/network/ServerCommonNetworkHandler',lambda n:n in ('sendPacket','send','disableFlush','enableFlush')),
         (mc,'net/minecraft/server/network/ServerPlayNetworkHandler',lambda n:'Flush' in n or n=='sendPacket'),
         (mc,'net/minecraft/network/ClientConnection',lambda n:n in ('send','sendImmediately','sendInternal','flush','flushInternal','handleQueuedTasks')),
         (mc,'net/minecraft/server/MinecraftServer',lambda n:n=='tickWorlds' or n.startswith('method_')),
         (netty,'io/netty/channel/embedded/EmbeddedChannel',lambda n:n in ('flushOutbound','flushOutbound0','doWrite','handleOutboundMessage','runPendingTasks'))]
for jar,name,select in targets:
    with zipfile.ZipFile(jar) as z:raw=z.read(name+'.class')
    cf=ClassFile(raw)
    print('\nCLASS',name,'EXTENDS',cf.super,'JAR',jar,'CLASS_SHA256',hashlib.sha256(raw).hexdigest())
    print('FIELDS',[f[:2] for f in cf.fields if any(x in f[0].lower() for x in ('flush','queue','outbound'))])
    for method in cf.methods:
        if select(method[0]):
            if name.endswith('MinecraftServer') and method[0]!='tickWorlds' and not any('disableFlush' in x for x in cf.code(method,refs_only=True)):continue
            print('METHOD',method[0],method[1]);lines=cf.code(method,refs_only=name.endswith('MinecraftServer'))
            if name.endswith('MinecraftServer'):
                lines=[x for x in lines if any(k in x for k in ('Flush','getPlayer','networkHandler','sendPacket','NetworkIo'))]
            print('\n'.join(lines))

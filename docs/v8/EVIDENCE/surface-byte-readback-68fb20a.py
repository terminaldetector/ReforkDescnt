import hashlib,json,struct,zlib,zipfile
from pathlib import Path

runtime=Path('build/cubic-surface-arrival-final-cubic-surface-arrival-integrated-68fb20a-sodium')
out=Path('docs/v8/EVIDENCE/surface-byte-readback-68fb20a.json')
def decode(raw):
    assert len(raw)==5121 and raw[0]==1
    return {'height':struct.unpack_from('>h',raw,1)[0],
            'colour':int.from_bytes(raw[2049:2052],'big')}
packets=[]
for sequence in (1,2):
    p=runtime/f'surface-arrival-packets/packet-{sequence}.bin';raw=p.read_bytes()
    packets.append({'sequence':sequence,'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest(),'cell':decode(raw)})
assert packets[0]['sha256']=='2214c13e203e6d6c172a9e7a4452019960935564e57f51162c2d546ec34d32d3'
assert packets[1]['sha256']=='7fe32b531018119e62f5a3cccdcbe0eb3bea228b1fc30fa08fee75e0fd781552'
assert packets[0]['cell']=={'height':2301,'colour':0}
assert packets[1]['cell']=={'height':2301,'colour':16445005}
disk=runtime/'saves/Cubic-Gate/drmd/surface/L0/0_0.bin'
archive=Path('work/ufo-body/arrival-68fb20a-closed-world.zip')
with zipfile.ZipFile(archive) as preserved:
    assert preserved.testzip() is None
    packed=preserved.read('Cubic-Gate/drmd/surface/L0/0_0.bin')
raw=zlib.decompress(packed[4:]);assert len(raw)==struct.unpack_from('>I',packed)[0]
assert decode(raw)==packets[1]['cell']
result={'source':'68fb20a4be8b87ee6b280c0fce85bb5baacb76cd',
        'scope':'Independent Python decode of actual accepted network payloads and closed native disk; no fixture editing',
        'packets':packets,'disk':{'path':str(disk),'preservedArchive':str(archive),'member':'Cubic-Gate/drmd/surface/L0/0_0.bin','sha256':hashlib.sha256(packed).hexdigest(),'decodedCell':decode(raw)}}
out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))

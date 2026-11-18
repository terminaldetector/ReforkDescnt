"""Independently decode actual accepted packets and disk bytes from the sealed client proof."""
from pathlib import Path
import hashlib,json,struct,zlib,zipfile
source='7c502b49e9ca131c649280ca425f7dc0e4264827'
archive=Path('docs/v8/EVIDENCE/integrated-7c502b4.zip')
sha=lambda raw:hashlib.sha256(raw).hexdigest()
assert sha(archive.read_bytes())=='e2e83a110c6dd915ba51a82e41ce6ac8d174d02a021af00fa658a58edb9816da'
def decode(raw):
 assert len(raw)==5121 and raw[0]==1
 return dict(height=struct.unpack_from('>h',raw,1)[0],colour=int.from_bytes(raw[2049:2052],'big'))
with zipfile.ZipFile(archive) as z:
 assert z.testzip() is None
 packets=[]
 for sequence in (1,2):
  raw=z.read(f'arrival/surface-arrival-packets/packet-{sequence}.bin')
  packets.append(dict(sequence=sequence,bytes=len(raw),sha256=sha(raw),cell=decode(raw)))
 packed=z.read('arrival/disk/L0_0_0.bin')
assert packets[0]['cell']==dict(height=2301,colour=0)
assert packets[1]['cell']==dict(height=2301,colour=16445005)
assert packets[0]['sha256']=='2214c13e203e6d6c172a9e7a4452019960935564e57f51162c2d546ec34d32d3'
assert packets[1]['sha256']=='7fe32b531018119e62f5a3cccdcbe0eb3bea228b1fc30fa08fee75e0fd781552'
raw=zlib.decompress(packed[4:]);assert len(raw)==struct.unpack_from('>I',packed)[0] and decode(raw)==packets[1]['cell']
result=dict(source=source,scope='Independent raw network/disk decode; no fixture edit or terrain-smoothing acceptance',archive=str(archive),packets=packets,disk=dict(member='arrival/disk/L0_0_0.bin',sha256=sha(packed),decodedCell=decode(raw)))
Path('docs/v8/EVIDENCE/surface-byte-readback-7c502b4.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))

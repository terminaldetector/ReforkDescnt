"""Read exact integrated evidence; this does not launch or alter the game."""
from pathlib import Path
import gzip
import hashlib
import json
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile
import zlib

root = Path(__file__).resolve().parents[3]
source = '5057931efce99156ffdc8a195e90b1a926907ed8'
archive = root / 'docs/v8/EVIDENCE/integrated-5057931.zip'
sha = lambda raw: hashlib.sha256(raw).hexdigest()
assert sha(archive.read_bytes()) == 'faa4c5e1fb95f3e3947418f1ea6b17d785fdf39ceb88cc0db8226efb63546cd5'
postimages = json.loads((root / 'docs/v8/EVIDENCE/aa-recon-postimages-5057931.json').read_text())
for name, expected in postimages.items():
    raw = subprocess.check_output(['git', 'show', source + ':' + name], cwd=root)
    assert len(raw) == expected['bytes'] and sha(raw) == expected['sha256'], name

def decode(raw):
    assert len(raw) == 5121 and raw[0] == 1
    return dict(height=struct.unpack_from('>h', raw, 1)[0],
                colour=int.from_bytes(raw[2049:2052], 'big'))

with zipfile.ZipFile(archive) as saved:
    assert saved.testzip() is None
    manifest = json.loads(saved.read('SHA256-MANIFEST.json'))
    assert len(manifest) == 161
    assert set(saved.namelist()) == set(manifest) | {'SHA256-MANIFEST.json'}
    for name, entry in manifest.items():
        raw = saved.read(name)
        assert len(raw) == entry['bytes'] and sha(raw) == entry['sha256'], name
    proofs = {name: saved.read(name) for name in manifest if name.startswith('run/player-aa-proof/')}
    assert len(proofs) == 28
    nbt = {}
    for name, raw in proofs.items():
        if name.endswith('.dat'):
            decoded = gzip.decompress(raw)
            assert decoded[0] == 10, name  # Actual compound NBT, including gzip footer validation.
            nbt[name] = dict(bytes=len(raw), decodedBytes=len(decoded), sha256=sha(raw))
    assert len(nbt) == 12
    packets = []
    for sequence in (1, 2):
        raw = saved.read(f'arrival/surface-arrival-packets/packet-{sequence}.bin')
        packets.append(dict(sequence=sequence, bytes=len(raw), sha256=sha(raw), cell=decode(raw)))
    assert packets[0]['cell'] == dict(height=2301, colour=0)
    assert packets[1]['cell'] == dict(height=2301, colour=16445005)
    packed = saved.read('arrival/disk/L0_0_0.bin')
    raw = zlib.decompress(packed[4:])
    assert len(raw) == struct.unpack_from('>I', packed)[0] and decode(raw) == packets[1]['cell']
    arrival = json.loads(saved.read('arrival/surface-arrival-outcome.json'))
    assert arrival['status'] == 'PASS' and arrival['fixtureBlockEdits'] == 0
    assert arrival['mutationsAfterBaseline'] == arrival['droppedEventCount'] == 0
    cases = list(ET.fromstring(saved.read('run/full-game-results.xml')).iter('testcase'))
    assert len(cases) == 480
    new_cases = [case.get('name') for case in cases if any(
        term in case.get('name', '') for term in ('playeraa', 'playermechanism', 'reconadmission'))]
    assert len(new_cases) == 24
    presentation = json.loads(saved.read('presentation/presentation-ok.json'))
    assert len(presentation['scenes']) == 6
    assert all(scene['glError'] == 0 and scene['worldNearestGridRatio'] == 1
               and scene['projectedMirrorPanes'] == 8 and scene['mergedMirrorSurfaces'] == 1
               for scene in presentation['scenes'])
    palette = json.loads(saved.read('presentation/ps1-color-ok.json'))
    assert len(palette['states']) == 5
    assert all(state['rgbAgreement'] == 1 and state['glError'] == 0 for state in palette['states'])
    gun = json.loads(saved.read('presentation/viewmodel-proof/viewmodel-visibility.json'))
    assert gun['nativePixels'] == gun['ps1Pixels'] == 166
    assert gun['sameCamera'] and gun['sameModules'] and gun['maximumProjectedVertexDeltaPixels'] == 0

result = dict(source=source, archive=str(archive.relative_to(root)), payloads=len(manifest),
              exactSourcePostimages=len(postimages), aaProofFiles=len(proofs),
              gzipCompoundNbtFiles=nbt, packets=packets,
              disk=dict(sha256=sha(packed), decodedCell=decode(raw)),
              ordinaryGameTests=len(cases), newGameplayCases=new_cases,
              arrival=dict(serverFull=arrival['firstServerFullTick'],
                           bothServerFull=arrival['firstBothFullTick'],
                           finalTick=arrival['finalSnapshot']['serverTick'],
                           clientHighFull=arrival['finalSnapshot']['client']['highFull'],
                           clientLowFull=arrival['finalSnapshot']['client']['lowFull']),
              presentationScenes=6, paletteStates=5, nativeAndPs1GunPixels=166,
              wholeSpecificationAccepted=False)
out = Path(__file__).with_suffix('.json')
out.write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps({k: result[k] for k in ('source', 'payloads', 'exactSourcePostimages',
                                      'aaProofFiles', 'ordinaryGameTests', 'arrival')}))

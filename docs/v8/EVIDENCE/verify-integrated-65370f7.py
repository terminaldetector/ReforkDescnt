"""Verify retained current evidence without launching or changing Minecraft."""
from pathlib import Path
import gzip
import hashlib
import io
import json
import struct
import subprocess
import tarfile
import xml.etree.ElementTree as ET
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[3]
SOURCE = '65370f7266cea705ebf2f9e86217031370fa2a3d'
RUN = 'work/multi-agent/integrated-65370f7/'
ARRIVAL = 'build/cubic-surface-arrival-final-cubic-surface-arrival-integrated-65370f7-sodium/'
PRESENT = 'build/cubic-client-final-ps1-color-integrated-65370f7-sodium/'
sha = lambda b: hashlib.sha256(b).hexdigest()
archive = ROOT / 'docs/v8/EVIDENCE/integrated-65370f7.zip'
assert sha(archive.read_bytes()) == 'e540c66e4d5e39af73098f4b393aee98e1c2325d4e2ac7dba16e170aef07a5d3'

def unique_cases(raw):
    cases = list(ET.fromstring(raw).iter('testcase'))
    assert not any(c.find(k) is not None for c in cases for k in ('failure', 'error', 'skipped'))
    keys = {(c.get('classname'), c.get('name')) for c in cases}
    assert len(keys) == len(cases)
    return keys

def units(raw):
    result = set()
    with tarfile.open(fileobj=io.BytesIO(raw), mode='r:gz') as t:
        for member in t.getmembers():
            if member.isfile() and member.name.endswith('.xml'):
                cases = unique_cases(t.extractfile(member).read())
                assert not result & cases
                result |= cases
    return result

def cell(raw):
    assert len(raw) == 5121 and raw[0] == 1
    return dict(height=struct.unpack_from('>h', raw, 1)[0], colour=int.from_bytes(raw[2049:2052], 'big'))

with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    h = json.loads(z.read('MANIFEST.json'))
    assert h['source'] == SOURCE and len(h['files']) == 213
    assert set(z.namelist()) == set(h['files']) | {'MANIFEST.json'}
    for name, expected in h['files'].items():
        data = z.read(name)
        assert len(data) == expected['bytes'] and sha(data) == expected['sha256'], name
    source_rows = json.loads(z.read('SOURCE-DELTA.json'))
    assert len(source_rows) == 46
    for row in source_rows:
        raw = subprocess.check_output(['git', 'show', SOURCE + ':' + row['path']], cwd=ROOT)
        assert sha(raw) == row['newSHA256'] and z.read('source/' + row['path']) == raw
    u = units(z.read(RUN + 'unit-results.tar.gz'))
    g = unique_cases(z.read(RUN + 'full-game-results.xml'))
    assert len(u) == 998 and len(g) == 502
    old = ROOT / 'docs/v8/EVIDENCE/integrated-5057931.zip'
    assert sha(old.read_bytes()) == 'faa4c5e1fb95f3e3947418f1ea6b17d785fdf39ceb88cc0db8226efb63546cd5'
    with zipfile.ZipFile(old) as previous:
        assert previous.testzip() is None
        old_u = units(previous.read('run/unit-results.tar.gz'))
        old_g = unique_cases(previous.read('run/full-game-results.xml'))
    assert len(old_u) == 951 and len(old_g) == 480 and old_u <= u and old_g <= g
    assert len(u - old_u) == 47 and len(g - old_g) == 22
    manifest = json.loads(z.read(RUN + 'processed-full-manifest.json'))
    assert len(manifest['entrypoints']['fabric-gametest']) == 84
    for rel, digest in h['jars'].items():
        jar = z.read(RUN + 'artifacts/' + Path(rel).name)
        assert sha(jar) == digest
        with zipfile.ZipFile(io.BytesIO(jar)) as j:
            assert j.testzip() is None
            mod = json.loads(j.read('fabric.mod.json'))
            assert mod['id'] == ('drmd_cubiccheck' if '-cubiccheck' in rel else 'drmd')
            if mod['id'] == 'drmd':
                required = 'com/terminaldetector/drmd/world/contraption/BodyControlCore.class'
                assert struct.unpack('>H', j.read(required)[6:8])[0] == 65
                assert 'com/terminaldetector/drmd/BodyAssemblyTransactionGameTests.class' not in j.namelist()
    pair = json.loads(z.read(RUN + 'native-pair-first/receipt.json'))
    assert pair['gatePassed'] and pair['source'] == pair['endSource'] == SOURCE and pair['jars'] == h['jars']
    assert all(r['helperExit'] == r['javaExit'] == 0 for r in pair['results'])
    packets = [cell(z.read(ARRIVAL + f'surface-arrival-packets/packet-{i}.bin')) for i in (1, 2)]
    assert packets == [dict(height=2301, colour=0), dict(height=2301, colour=16445005)]
    with zipfile.ZipFile(io.BytesIO(z.read(RUN + 'native-saves/arrival.zip'))) as save:
        assert save.testzip() is None
        packed = save.read('drmd/surface/L0/0_0.bin')
        decoded = zlib.decompress(packed[4:])
        assert len(decoded) == struct.unpack('>I', packed[:4])[0] and cell(decoded) == packets[1]
    arrival = json.loads(z.read(ARRIVAL + 'surface-arrival-outcome.json'))
    assert arrival['status'] == 'PASS' and arrival['fixtureBlockEdits'] == arrival['mutationsAfterBaseline'] == arrival['droppedEventCount'] == 0
    scenes = json.loads(z.read(PRESENT + 'presentation-ok.json'))['scenes']
    assert len(scenes) == 6 and all(s['glError'] == 0 and s['worldNearestGridRatio'] == 1 and s['mergedMirrorSurfaces'] == 1 and s['projectedMirrorPanes'] == 8 for s in scenes)
    palette = json.loads(z.read(PRESENT + 'ps1-color-ok.json'))['states']
    assert len(palette) == 5 and all(s['rgbAgreement'] == 1 and s['glError'] == 0 for s in palette)
    gun = json.loads(z.read(PRESENT + 'viewmodel-proof/viewmodel-visibility.json'))
    assert gun['nativePixels'] == gun['ps1Pixels'] == 166 and gun['sameCamera'] and gun['sameModules'] and gun['maximumProjectedVertexDeltaPixels'] == 0
    aa = [n for n in h['files'] if n.startswith(RUN + 'player-aa-proof/')]
    nbt = [n for n in aa if n.endswith('.dat')]
    assert len(aa) == 28 and len(nbt) == 12
    assert all(gzip.decompress(z.read(n))[0] == 10 for n in nbt)
    parity = json.loads(z.read(RUN + 'component-remapped-equivalence-classified.json'))
    assert parity['classificationChecksPass'] and not parity['allHistoricalClassesByteEqual']
    assert parity['unchangedGuiClasses'] == 11 and parity['unchangedCloudCoreClasses'] == 16
    assert parity['groups']['ps1_geometry']['sameClasses'] == 15 and parity['groups']['climber_navigation']['sameClasses'] == 11
    assert parity['sharedConfigDelta']['classExactNewGeometryComponent']

result = dict(source=SOURCE, passed=True, payloads=213, sourcePostimages=46, units=998,
              games=502, entries=84, newUnits=47, newGames=22, removedOldCases=0,
              sameJarClients=2, savedDiskAndActualPackets=True, aaNbtGzipFooters=12,
              presentationScenes=6, paletteStates=5, viewmodelPixels=166,
              sharedConfigChangedAndClassified=True, fullSpecificationAccepted=False)
Path(__file__).with_suffix('.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))

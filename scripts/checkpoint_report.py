#!/usr/bin/env python3
"""Bind a milestone's test evidence and installable JAR to one source revision."""
import argparse
import csv
import math
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

MARKERS = ('rendercheck-ok', 'collapse-ok', 'ragdoll-ok', 'gravity-weapons-ok', 'swarm-ai-ok')
FRAMES = (
    'hybrid-before', 'hybrid-after', 'collapse-before', 'collapse-flight', 'collapse-after',
    'ragdoll-flight', 'ragdoll-after', 'gravity-swarm-held', 'gravity-swarm-flight',
    'gravity-rail-held', 'gravity-rail-flight', 'gravity-shift-held', 'gravity-shift-flight',
    'drone-swarm-dense', 'drone-swarm-flight',
)
SHOT_FRAMES = ('bolt-dark-ranges', 'bolt-light-ranges', 'orb-dark-ranges', 'orb-light-ranges',
               'rocket-dark-ranges', 'rocket-light-ranges', 'banked-fire', 'vertical-fire',
               'muzzle-impact', 'fast-motion', 'mounts-fov-70', 'mounts-fov-80', 'mounts-fov-90')

HYBRID_FRAMES = tuple(f'hybrid-{model}-{view}' for model in range(3)
                      for view in ('workshop','fov70','fov80','fov90','banked','vertical','external')) + ('hybrid-2-loaded',)

AUDIT_FRAMES = ('audit-flight-off','audit-lower-recovery','audit-inventory') + tuple(
    f'audit-gravity-{i}' for i in range(6)) + tuple(f'audit-gravity-release-{i}' for i in range(6)) + tuple(
    f'audit-settings-scale-{i}' for i in (2,3,4)) + tuple(f'audit-settings-bottom-{i}' for i in (2,3,4)) + tuple(
    f'audit-swarm-{n}' for n in (128,256,512,1024)) + tuple(f'audit-environment-{i}' for i in range(4)) + tuple(f'audit-profile-{i}' for i in range(4))


AUDIT_NATIVE_CASES = ('publishedScaffoldFrameFormatRemainsReadableWithoutAModeField',
 'poweredTntIsChargedOnceWhenItImmediatelyBecomesAnEntity',
 'explicitToolModesAreNotMaskedByAdaptiveDefaults',
 'multiCellPlacementCannotOverwriteAnUnvalidatedNeighbour',
 'carvedFragmentsRemainMineableWhileContainersAreProtected',
 'surfacePlacementConsumesExactlyOneAndCannotBuildWithoutMaterial',
 'hotbarMaterialIsChargedAndRejectedTargetsDoNotConsumeIt',
 'batchDuplicatesAndExhaustionNeverDuplicateOrDoubleCharge',
 'creativeKeepsInventoryButCannotPlaceInsideThePlayer',
 'explicitStandardSurvivesSavedWorldState',
 'legacyStateUsesExpandedAndEachWorldStoresItsOwnChoice',
 'regularClusterCreatesSevenChildrenExactlyOnce',
 'heavyClusterCreatesFourteenChildrenExactlyOnce',
 'expiredAndVoidParentsCannotSpawnAnotherGeneration',
 'softenedStoneMakesProgressInsteadOfReturningCobblestoneForever',
 'hotRockDoesNotCreateLavaAndProtectedBlocksOrContainersStayIntact',
 'repeatingOneBoreDoesNotRewriteIdenticalBoundaryMasks',
 'remoteMiningCannotPullAnUnloadedChunkIntoMemory',
 'clearFeetDoNotPermitAnExitWithTheHeadInsideAStoneBlock',
 'heightAndUnloadedExitRejectionsPreserveTheTraveller',
 'everyRegisteredDrmdItemHasAnActualModelResource',
 'aClearDoorWritesBothHalvesAndConsumesOneItem',
 'bedFootAndHeadFollowAllFourFacingDirections',
 'aTallPlantReceivesItsWholeFootprintFromOneItem',
 'collisionOnlyAtTheBedHeadCannotLeaveAPlacedFoot',
 'aMultiCellBatchCountsChangedCellsButChargesEachObjectOnce',
 'nativeQuietWriteFailureRollsBackEveryAttemptedCell',
 'anUpperPartBeyondWorldHeightCannotPlaceOrConsumeTheLowerPart',
 'previewPlanIsReadOnlyAndMatchesTheCommittedDoorFootprint',
 'constructionModeSynchronizesEvenWithoutALockedScaffold',
 'oversizedScaffoldFramesAreRejectedBeforeAllocation')

def verify_native_audit(root,started):
    names=[]
    for path in (root/'build/gametest/reports').glob('**/*.xml'):
        fresh(path,started)
        for case in ET.parse(path).getroot().iter('testcase'):
            names.append(re.sub('[^a-z0-9]','',case.get('name','').lower()))
    missing=[name for name in AUDIT_NATIVE_CASES if not any(name.lower() in case for case in names)]
    if missing: raise ValueError(f'Missing native audit GameTests: {missing}')
    fresh(root/'build/gametest/item-resource-inventory.tsv',started)


def verify_audit(root, started):
    marker=root/'build/shootcheck/audit-ok.json'
    fresh(marker,started)
    proof=json.loads(marker.read_text())
    expected=dict(lifecycleRoundTrips=8,gravityAxes=6,gravityRelease=True,lowerRecovery=True,
                  modalHud=True,inventoryDistinct=True,settingsScales=[2,3,4],horizonCases=168,
                  horizonOnPixels=True,swarmProfiles=[128,256,512,1024],environmentComparisons=4,profileCombinations=4)
    if (any(proof.get(key)!=value for key,value in expected.items()) or proof.get('frames',0)<369
            or not isinstance(proof.get('dhInstalled'),bool)):
        raise ValueError(f'Incomplete video audit verification: {proof}')
    viewports=proof.get('settingsViewports',[])
    if (len(viewports)!=3 or [v.get('requested') for v in viewports]!=[2,3,4]
            or any(v.get('requested')!=v.get('actual') for v in viewports)
            or [(v.get('width'),v.get('height')) for v in viewports]!=[(1280,720),(1280,720),(1920,1080)]):
        raise ValueError(f'GUI scale was not tested at the actual viewport: {viewports}')
    for name in AUDIT_FRAMES:
        fresh_png(root/f'build/shootcheck/screenshots/{name}.png',started)
    for height in (100,320,640,880,1024,1500,2150):
        for bank in (0,90,180):
            for speed in (0,50,300,628):
                for dh in (0,1):
                    for view in ('on','off'):
                        fresh_png(root/f'build/shootcheck/screenshots/audit-horizon-{height}-{bank}-{speed}-{dh}-{view}.png',started)
    metrics=root/'build/shootcheck/swarm-profile.csv';fresh(metrics,started)
    fresh(root/'build/shootcheck/inventory-models.tsv',started)
    with metrics.open() as stream: rows=list(csv.DictReader(stream))
    if [int(row['requested']) for row in rows]!=[128,256,512,1024]:
        raise ValueError('Missing real swarm load profiles')
    for row in rows:
        for key in ('server_work_mean_ms','server_work_p95_ms','tps','fps','frame_p95_ms'):
            if not math.isfinite(float(row[key])) or float(row[key])<=0:
                raise ValueError(f'Invalid measured swarm metric: {row}')
        count=int(row['requested'])
        if (int(row['server_alive'])!=count or int(row['client_synced'])!=count
                or not 0<=int(row['max_candidates'])<=96 or int(row['cleanup_remaining'])!=0
                or not 0<=int(row['moving'])<=count or not 0<=int(row['exact_overlaps'])<count
                or int(row['frames'])<2):
            raise ValueError(f'Incomplete real swarm profile: {row}')
        if count==128 and int(row['moving'])<96: raise ValueError(f'128-drone baseline stalled: {row}')
    return dict(proof=proof,profiles=rows)


BOARDING_SCENES = ('landing', 'riding', 'walking', 'jump', 'sixdof-handoff')


def verify_boarding(root, started):
    marker = root / 'build/boardingcheck/boarding-ok.json'
    fresh(marker, started)
    proof = json.loads(marker.read_text())
    if (any(proof.get(k) is not True for k in ('actualInputs', 'authoritativeContact', 'worldUpOnly'))
            or proof.get('teleportFollow') is not False):
        raise ValueError('Missing actual-input authoritative boarding verification')
    for key, limit in (('jumpWorldVelocityError', .03), ('maxPredictionError', .8)):
        value = proof.get(key)
        if not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 <= value < limit:
            raise ValueError(f'Invalid boarding metric {key}: {value}')
    if not isinstance(proof.get('predictionSamples'), int) or proof['predictionSamples'] < 30:
        raise ValueError('Missing command-aligned boarding prediction samples')
    scenes = proof.get('scenes', [])
    if [s.get('scene') for s in scenes] != list(BOARDING_SCENES) or any(s.get('frames', 0) < 3 for s in scenes):
        raise ValueError('Missing completed boarding scenes')
    for name in BOARDING_SCENES:
        fresh_png(root / f'build/boardingcheck/boarding-frames/{name}.png', started)
    return proof



SHIP_BOARDING_SCENES = ('ship-landing', 'ship-walking', 'roll-wall', 'roll-ceiling', 'roll-complete',
                        'pitch-wall', 'pitch-ceiling', 'pitch-complete', 'combined-axes', 'combined-complete',
                        'ship-jump', 'gravity-off-jump', 'flight-handoff')


def verify_ship_boarding(root, started):
    marker = root / 'build/shipboardingcheck/ship-boarding-ok.json'
    fresh(marker, started)
    proof = json.loads(marker.read_text())
    if (any(proof.get(k) is not True for k in ('actualInputs', 'shipGravity', 'orientedCollision'))
            or proof.get('teleportFollow') is not False):
        raise ValueError('Missing actual-input oriented ship-gravity verification')
    for key, limit in (('maxPredictionError', .8), ('jumpVelocityError', .1),
                       ('maxFrameError', .01), ('maxModelUpError', .05), ('maxCameraUpError', .001)):
        value = proof.get(key)
        if not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 <= value < limit:
            raise ValueError(f'Invalid ship-boarding metric {key}: {value}')
    for key, minimum in (('predictionSamples', 250), ('modelFrames', 40), ('cameraFrames', 40)):
        if not isinstance(proof.get(key), int) or proof[key] < minimum:
            raise ValueError(f'Missing actual ship-boarding samples: {key}')
    scenes = proof.get('scenes', [])
    if [s.get('scene') for s in scenes] != list(SHIP_BOARDING_SCENES) or any(s.get('frames', 0) < 3 for s in scenes):
        raise ValueError('Missing full roll/pitch/combined ship-gravity scenes')
    for name in SHIP_BOARDING_SCENES:
        fresh_png(root / f'build/shipboardingcheck/ship-boarding-frames/{name}.png', started)
    return proof


BODY_EDIT_SCENES = ('hull-intact','hull-cut','hull-patched','survival-cut','saved-state')


def verify_body_edits(root, started):
    marker=root/'build/bodyeditcheck/body-edit-ok.json'
    fresh(marker,started)
    proof=json.loads(marker.read_text())
    for key in ('actualInputs','survivalMining','materialCharged','savedState','comRebased'):
        if proof.get(key) is not True: raise ValueError(f'Missing body-edit proof: {key}')
    for key,minimum in (('revisions',3),('predictionSamples',40)):
        if type(proof.get(key)) is not int or proof[key]<minimum: raise ValueError(f'Missing body-edit samples: {key}')
    for key,limit in (('maxPredictionError',.8),('maxPivotStep',.8)):
        value=proof.get(key)
        if not isinstance(value,(int,float)) or not math.isfinite(value) or not 0<=value<limit: raise ValueError(f'Invalid body-edit metric: {key}')
    if proof.get('dropStacks')!=1 or proof.get('toolDamage')!=1: raise ValueError('Local mining loot/durability is incomplete')
    scenes=proof.get('scenes',[])
    if [s.get('scene') for s in scenes]!=list(BODY_EDIT_SCENES) or any(s.get('frames',0)<3 for s in scenes): raise ValueError('Missing body-edit input scenes')
    for name in BODY_EDIT_SCENES: fresh_png(root/f'build/bodyeditcheck/body-edit-frames/{name}.png',started)
    return proof


BODY_INDEX_SCENES = ('edge-intact','edge-cut','edge-patched','edge-hole')

def verify_body_index(root, started):
    marker=root/'build/bodyindexcheck/body-index-ok.json'
    fresh(marker,started)
    proof=json.loads(marker.read_text())
    for key in ('actualInputs','legacyMissClient','legacyMissServer','edgePicked','holeClear','materialCharged'):
        if proof.get(key) is not True: raise ValueError(f'Missing volume-index proof: {key}')
    if proof.get('teleportFollow') is not False: raise ValueError('Volume-index client must use real contact motion')
    for key,minimum in (('revisionChanges',3),('predictionSamples',40)):
        if type(proof.get(key)) is not int or proof[key]<minimum: raise ValueError(f'Missing volume-index samples: {key}')
    for key,minimum,maximum in (('maxPredictionError',0,.8),('travelDistance',1,1000)):
        value=proof.get(key)
        if type(value) not in (int,float) or not math.isfinite(value) or not minimum<=value<maximum: raise ValueError(f'Invalid volume-index metric: {key}')
    scenes=proof.get('scenes',[])
    if [s.get('scene') for s in scenes]!=list(BODY_INDEX_SCENES) or any(s.get('frames',0)<3 for s in scenes): raise ValueError('Missing distant-wing input scenes')
    for name in BODY_INDEX_SCENES: fresh_png(root/f'build/bodyindexcheck/body-index-frames/{name}.png',started)
    return proof


def properties(root):
    return dict(line.strip().split('=', 1) for line in
                (root / 'gradle.properties').read_text().splitlines()
                if '=' in line and not line.lstrip().startswith('#'))


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def fresh(path, started):
    if not path.is_file() or not path.stat().st_size:
        raise ValueError(f'Missing or empty evidence: {path}')
    if path.stat().st_mtime < started:
        raise ValueError(f'Stale evidence: {path}')


def fresh_png(path,started):
    fresh(path,started)
    with path.open('rb') as stream:
        if stream.read(8)!=b'\x89PNG\r\n\x1a\n':
            raise ValueError(f'Invalid PNG evidence: {path}')


def junit(folder, started=None):
    files = sorted(folder.glob('**/*.xml'))
    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in files:
        if started is not None:
            fresh(path, started)
        tree = ET.parse(path).getroot()
        cases = list(tree.iter('testcase'))
        counts['tests'] += len(cases)
        for kind in ('failure', 'error', 'skipped'):
            key = {'failure': 'failures', 'error': 'errors', 'skipped': 'skipped'}[kind]
            counts[key] += sum(case.find(kind) is not None for case in cases)
    return counts


def verify(root):
    out = root / 'build/checkpoint'
    started = int((out / 'started-at.txt').read_text().strip())
    source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    if (out / 'SOURCE_SHA.txt').read_text().strip() != source:
        raise ValueError('Evidence source SHA differs from the checked-out commit')
    results = {}
    for name, folder in [('unit', 'build/test-results/test'), ('gametest', 'build/gametest/reports')]:
        results[name] = junit(root / folder, started)
        if (results[name]['tests'] == 0 or results[name]['failures'] or
                results[name]['errors'] or results[name]['skipped']):
            raise ValueError(f'{name}: incomplete or failing test report: {results[name]}')
    for name in MARKERS:
        fresh(root / f'build/rendercheck/{name}.txt', started)
    for name in FRAMES:
        frame = root / f'build/rendercheck/screenshots/{name}.png'
        fresh(frame, started)
        with frame.open('rb') as stream:
            if stream.read(8) != b'\x89PNG\r\n\x1a\n':
                raise ValueError(f'Invalid PNG evidence: {frame}')
    swarm = (root / 'build/rendercheck/swarm-ai-ok.txt').read_text()
    match = re.fullmatch(r'Live swarm synchronized (\d+) drones; indexed=(\d+); '
                         r'moving=(\d+); maximum candidates/query=(\d+)\.\s*', swarm)
    if not match:
        raise ValueError(f'Unrecognized swarm evidence: {swarm!r}')
    synced, indexed, moving, candidates = map(int, match.groups())
    if synced != 128 or indexed < 128 or not 96 <= moving <= 128 or not 0 <= candidates <= 96:
        raise ValueError(f'Swarm criteria failed: {swarm}')

    shot_marker = root / 'build/shootcheck/shootcheck-ok.json'
    fresh(shot_marker, started)
    shot = json.loads(shot_marker.read_text())
    if (shot.get('scenes') != 10 or shot.get('fovs') != [70, 80, 90] or
            shot.get('rangePixelsChecked') is not True or shot.get('fastSpeed') != 8 or
            shot.get('fastTravel', 0) < 24 or shot.get('fastFrames', 0) < 3 or shot.get('fastPixelsChecked') is not True or
            shot.get('muzzleEvents', 0) < 1 or shot.get('impactEvents', 0) < 1):
        raise ValueError(f'Incomplete weapon verification: {shot}')
    for name in SHOT_FRAMES:
        fresh(root / f'build/shootcheck/screenshots/{name}.png', started)

    hybrid_marker = root / 'build/shootcheck/hybrid-weapons-ok.json'
    fresh(hybrid_marker, started)
    hybrid = json.loads(hybrid_marker.read_text())
    expected = dict(models=3, presetRoundTrips=3, serverSaves=3, muzzlePairs=3,
                    fovs=[70,80,90], banked=3, vertical=3, realUseAnimated=3, loadSynced=True, external=3)
    if any(hybrid.get(key) != value for key,value in expected.items()) or hybrid.get('frames',0) < 19:
        raise ValueError(f'Incomplete hybrid verification: {hybrid}')
    for name in HYBRID_FRAMES:
        fresh(root / f'build/shootcheck/screenshots/{name}.png', started)

    props = properties(root)
    version, base = props['mod_version'], props['archives_base_name']
    boarding = verify_boarding(root, started) if version.startswith(('1.1.25', '1.1.26','1.1.27','1.1.28')) else None
    body_edits = verify_body_edits(root,started) if version.startswith(('1.1.27','1.1.28')) else None
    ship_boarding = verify_ship_boarding(root, started) if version.startswith(('1.1.26','1.1.27','1.1.28')) else None
    body_index = verify_body_index(root,started) if version.startswith('1.1.28') else None
    audit=verify_audit(root,started) if version.startswith('1.1.14') else None
    if audit: verify_native_audit(root,started)
    jar = root / f'build/libs/{base}-{version}.jar'
    fresh(jar, started)
    with zipfile.ZipFile(jar) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'))
        if metadata['version'] != version or metadata['id'] != 'drmd':
            raise ValueError('JAR metadata does not match this source version')
        if 'com/terminaldetector/drmd/DescentMod.class' not in archive.namelist():
            raise ValueError('Not a compiled DRMD binary JAR')
        forbidden = ('AuditRenderCheck', 'AuditInventoryScreen', 'AuditSwarmProfile', 'AuditFrameProbe', 'AuditCameraProbe', 'AuditHorizonProbe', 'AuditHudProbe', 'HybridRenderCheck', 'HybridWeaponCheck', 'HybridMeshProbe', 'WorkshopPreviewProbe', 'WeaponRenderCheck', 'GameTests.class',
                     'ProjectileRendererProbe', 'ProjectileVisibilityProbe', 'WeaponCrosshairMask', 'drmd.shootcheck.mixins',
                     'drmd_gametest', 'drmd_rendercheck', 'drmd_shootcheck', 'com/terminaldetector/drmd/gate/',
                     'com/terminaldetector/drmd/boardingcheck/', '-ok.txt')
        if any(any(token in name for token in forbidden) for name in archive.namelist()):
            raise ValueError('Test-only code or evidence leaked into the production JAR')
        if any('check' in key.lower() or 'gametest' in key.lower()
               for key in metadata.get('entrypoints', {})):
            raise ValueError('Test entrypoint leaked into the production JAR')
    distribution = out / 'distribution'
    distribution.mkdir(exist_ok=True)
    target = distribution / f'{base}-{version}-fabric-1.21.1.jar'
    shutil.copy2(jar, target)
    if audit:
        for name in ('swarm-profile.csv','inventory-models.tsv'):
            shutil.copy2(root/'build/shootcheck'/name,distribution/name)
    digest = sha256(target)
    (distribution / 'INSTALL_TLAUNCHER.txt').write_text(
        f'DRMD {version} — Fabric / Minecraft 1.21.1 / Java 21\n'
        f'Установочный JAR: {target.name}\nSHA-256: {digest}\n\n'
        'Используйте отдельный профиль Fabric 1.21.1 с Fabric API для 1.21.1.\n'
        'Оставьте в mods один DRMD JAR; sources/dev/test JAR туда не помещать.\n'
        'Версию DRMD проверяйте в списке модов загрузчика.\n'
        'Экспериментальные кубические миры и дополнительные моды устанавливаются\n'
        'только по инструкции данного этапа; исходные сохранения не перезаписывать.\n',
        encoding='utf-8')
    (distribution / 'SHA256SUMS').write_text(f'{digest}  {target.name}\n')
    result = dict(source_sha=source, version=version, audit=audit, jar=target.name, sha256=digest,
                  unit=results['unit'], gametest=results['gametest'], swarm=swarm.strip(),
                  hybrid=hybrid, hybrid_frames=[name + '.png' for name in HYBRID_FRAMES],
                  frames=[name + '.png' for name in FRAMES], shootcheck=shot,
                  shot_frames=[name + '.png' for name in SHOT_FRAMES])
    if boarding is not None:
        result['boarding'] = boarding
    if ship_boarding is not None:
        result['ship_boarding'] = ship_boarding
    if body_edits is not None: result['body_edits']=body_edits
    if body_index is not None: result['body_index']=body_index
    (out / 'verified.json').write_text(json.dumps(result, indent=2) + '\n')
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
            output.write(f'version={version}\n')
    print(f'::notice title=Verified checkpoint::{version}; unit={results["unit"]["tests"]}; '
          f'GameTest={results["gametest"]["tests"]}; {swarm.strip()}')
    print(f'::notice title=Installable JAR SHA-256::{target.name}: {digest}')
    print(json.dumps(result, indent=2))


def report(root):
    out = root / 'build/checkpoint'
    out.mkdir(parents=True, exist_ok=True)
    source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    server = os.environ.get('GITHUB_SERVER_URL', 'https://github.com')
    repo, run = os.environ.get('GITHUB_REPOSITORY'), os.environ.get('GITHUB_RUN_ID')
    run_url = f'{server}/{repo}/actions/runs/{run}' if repo and run else 'Local run; no GHA run'
    lines = ['# DRMD checkpoint report', '', f'- Source SHA: `{source}`',
             f'- Version: `{properties(root)["mod_version"]}`', f'- Run: {run_url}',
             '- Target: Minecraft 1.21.1 / Fabric / Java 21', '',
             '| Stage | Outcome |', '|---|---|']
    for stage in ('unit', 'gametest', 'build', 'rendercheck', 'shootcheck', 'verify'):
        lines.append(f'| {stage} | {os.environ.get(stage.upper() + "_OUTCOME") or "NOT RUN"} |')
    lines += ['', '## Test reports', '']
    for name, folder in [('unit', 'build/test-results/test'), ('gametest', 'build/gametest/reports')]:
        try:
            counts = junit(root / folder)
            lines.append(f'- {name}: {counts} (`{folder}`); zero tests means NOT VERIFIED.')
        except (ET.ParseError, OSError) as error:
            lines.append(f'- {name}: INVALID report: {error}')
    proof = out / 'verified.json'
    if os.environ.get('VERIFY_OUTCOME') == 'success' and proof.is_file():
        result = json.loads(proof.read_text())
        if result['source_sha'] != source:
            raise ValueError('Refusing to report evidence from another source revision')
        lines += ['', '## Verified distribution', '', f'- JAR: `distribution/{result["jar"]}`',
                  f'- SHA-256: `{result["sha256"]}`', '- Rendercheck: 5/5 fresh markers.',
                  f'- {result["swarm"]}', '', '### Fresh frames', '']
        lines += ['- ' + name for name in result['frames']]
        if result.get('audit'):
            lines += ['', '### Video audit regression', '', str(result['audit']['proof']), '', '```csv', (root/'build/shootcheck/swarm-profile.csv').read_text().strip(), '```', '', 'DH installed: '+str(result['audit']['proof'].get('dhInstalled'))+'; installation=false does not certify actual DH interoperability.']
        lines += ['', '### Hybrid samples', '', f'- {result["hybrid"]}', ''] + ['- ' + name for name in result['hybrid_frames']]
        lines += ['', '### Weapon verification', '',
                  f'- {result["shootcheck"]}', ''] + ['- ' + name for name in result['shot_frames']]
    else:
        lines += ['', '**NOT VERIFIED: no installable-mod artifact is certified by this run.**',
                  'See `logs/`, JUnit XML, GameTest logs and client crash reports for the first failure.']
    lines += ['', '## Limits', '',
              '- Historical 1.1.12 results in the supplied MHT are not results of this run.',
              '- Manual playability smoke test: NOT RUN.',
              '- Audit camera sweeps up to 628 m/s stress the renderer; these are not production player-speed claims.',
              '- Swarm profiles use invulnerable controlled actors, capped client FPS and software graphics in GHA. Larger counts report actual limits, not a general 1000+ performance guarantee.',
              '- Workshop Apply Build publishes construction/muzzles; Stats/Projectile/Flak/Guidance drafts are saved/exported, not runtime behavior overrides.',
              '- Three hybrid samples: laser_pulse (impulse), plasma, gravy_railgun. Models tab in the existing Workshop; Creative + server operator for publishing.',
              '- Local named presets: config/drmd/weapon-presets/*.nbt; authoritative layouts: world/data/drmd/visual/*.nbt.',
              '- Source archives, reports and artifacts are retained by GHA for 90 days.', '']
    (out / 'REPORT.md').write_text('\n'.join(lines))
    (out / 'RUN_URL.txt').write_text(run_url + '\n')
    with zipfile.ZipFile(out / 'screenshots.zip', 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for kind in ('rendercheck', 'shootcheck'):
            for frame in sorted((root / f'build/{kind}/screenshots').glob('*.png')):
                archive.write(frame, f'{kind}/{frame.name}')
    diagnostics = []
    keywords = ('DRMD shootcheck:', 'IllegalStateException:', ' error:', 'Caused by:',
                'Exception', 'Error:', 'Description:', 'ValueError:', 'timed out', 'at com.terminaldetector.')
    for stage in ('unit', 'gametest', 'build', 'rendercheck', 'shootcheck', 'verify'):
        if os.environ.get(stage.upper() + '_OUTCOME') != 'failure':
            continue
        logs = [out / f'logs/{stage}.log']
        if stage in ('rendercheck', 'shootcheck'):
            logs += [root / f'build/{stage}/logs/latest.log']
            logs += sorted((root / f'build/{stage}/crash-reports').glob('*.txt'))
        for log in logs:
            if not log.is_file():
                continue
            errors = list(dict.fromkeys(line for line in log.read_text(errors='replace').splitlines()
                                       if len(line) < 2000 and any(word in line for word in keywords)))
            diagnostics += [str(log.relative_to(root))] + errors
            for error in errors[-20:]:
                safe = error.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
                print(f'::error title={stage} diagnostic::{safe[:1500]}')
    (out / 'diagnostics.txt').write_text('\n'.join(diagnostics) + '\n')
    print(out / 'REPORT.md')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument('--verify', action='store_true')
    action.add_argument('--report', action='store_true')
    args = parser.parse_args()
    try:
        (verify if args.verify else report)(Path.cwd())
    except (ValueError, OSError, KeyError) as error:
        safe = str(error).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
        print(f'::error title=Checkpoint evidence rejected::{safe}')
        raise

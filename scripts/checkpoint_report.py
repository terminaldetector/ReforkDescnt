#!/usr/bin/env python3
"""Bind a milestone's test evidence and installable JAR to one source revision."""
import argparse
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
            shot.get('fastTravel', 0) < 24 or shot.get('muzzleEvents', 0) < 1 or shot.get('impactEvents', 0) < 1):
        raise ValueError(f'Incomplete weapon verification: {shot}')
    for name in SHOT_FRAMES:
        fresh(root / f'build/shootcheck/screenshots/{name}.png', started)

    props = properties(root)
    version, base = props['mod_version'], props['archives_base_name']
    jar = root / f'build/libs/{base}-{version}.jar'
    fresh(jar, started)
    with zipfile.ZipFile(jar) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'))
        if metadata['version'] != version or metadata['id'] != 'drmd':
            raise ValueError('JAR metadata does not match this source version')
        if 'com/terminaldetector/drmd/DescentMod.class' not in archive.namelist():
            raise ValueError('Not a compiled DRMD binary JAR')
        forbidden = ('HybridRenderCheck', 'WeaponRenderCheck', 'GameTests.class',
                     'drmd_gametest', 'drmd_rendercheck', 'drmd_shootcheck', '-ok.txt')
        if any(any(token in name for token in forbidden) for name in archive.namelist()):
            raise ValueError('Test-only code or evidence leaked into the production JAR')
        if any('check' in key.lower() or 'gametest' in key.lower()
               for key in metadata.get('entrypoints', {})):
            raise ValueError('Test entrypoint leaked into the production JAR')
    distribution = out / 'distribution'
    distribution.mkdir(exist_ok=True)
    target = distribution / f'{base}-{version}-fabric-1.21.1.jar'
    shutil.copy2(jar, target)
    shutil.copy2(root / 'dist/INSTALL_TLAUNCHER.txt', distribution)
    digest = sha256(target)
    (distribution / 'SHA256SUMS').write_text(f'{digest}  {target.name}\n')
    result = dict(source_sha=source, version=version, jar=target.name, sha256=digest,
                  unit=results['unit'], gametest=results['gametest'], swarm=swarm.strip(),
                  frames=[name + '.png' for name in FRAMES], shootcheck=shot,
                  shot_frames=[name + '.png' for name in SHOT_FRAMES])
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
        lines += ['', '### Weapon verification', '',
                  f'- {result["shootcheck"]}', ''] + ['- ' + name for name in result['shot_frames']]
    else:
        lines += ['', '**NOT VERIFIED: no installable-mod artifact is certified by this run.**',
                  'See `logs/`, JUnit XML, GameTest logs and client crash reports for the first failure.']
    lines += ['', '## Limits', '',
              '- Historical 1.1.12 results in the supplied MHT are not results of this run.',
              '- Manual playability smoke test: NOT RUN.',
              '- Source archives, reports and artifacts are retained by GHA for 90 days.', '']
    (out / 'REPORT.md').write_text('\n'.join(lines))
    (out / 'RUN_URL.txt').write_text(run_url + '\n')
    with zipfile.ZipFile(out / 'screenshots.zip', 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for kind in ('rendercheck', 'shootcheck'):
            for frame in sorted((root / f'build/{kind}/screenshots').glob('*.png')):
                archive.write(frame, f'{kind}/{frame.name}')
    diagnostics = []
    keywords = ('DRMD shootcheck:', 'IllegalStateException:', ' error:', 'Caused by:',
                'Exception in', 'ValueError:', 'timed out', 'at com.terminaldetector.')
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
                                       if any(word in line for word in keywords)))
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

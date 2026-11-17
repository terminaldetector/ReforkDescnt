"""Read-only host observation of one frozen recorder control. Never launches/stops Java.

Prepare a fresh immutable observer snapshot after the separately authorised v1
control preparation. Start that exact snapshot before the unchanged Java runner.
Synthetic tests of this module are not measured Minecraft observations.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shlex
import struct
import time
import zlib

SOURCE = '9426a6549fc91abc8c7e060a2eef9f184742dc3d'
EXECUTOR = '1946e64f61de6d676f1cf4e09e519e59174fad3356de3eea56e13ed5ab56fc64'
JARS = {
    'drmd-6dof-1.1.35-ufo-dev.1.jar': 'cf3701b529b21633a8a3e910e6755d006d97d6349e8938d7701c82671027138d',
    'drmd-6dof-1.1.35-ufo-dev.1-cubiccheck.jar': 'cb1056d89d1435a5211c5a1c1728934fde2caf92c3c85e28016e2c5ff4bce70a',
}
RUNS = {'disabled': '9426-disabled-v1', 'capacity16': '9426-capacity16-v1'}
SCENARIOS = {'disabled': 'instrumentation-smoke-mounted-disabled-v1',
             'capacity16': 'instrumentation-smoke-mounted-capacity16-v1'}
SCENES = ['connected-off', 'laser-fire', 'rocket-fire', 'fire-off-reversed', 'bearing-detached']
NULL_CONTEXT = ['seed', 'runtime_source_sha', 'runtime_game_sha256', 'cubes_loaded', 'draw_calls', 'mesh_queue',
                'requested_entities', 'active_entities', 'moving_entities', 'rendered_entities', 'ragdolls', 'rigid_bodies']
NULL_SERIES = ['gpu_frame_ms', 'cubes_loaded', 'draw_calls', 'mesh_queue']
MAX_RAW = 2 * 1024 * 1024
MAX_LOG = 16 * 1024 * 1024
MAX_PNG = 32 * 1024 * 1024


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def read_bounded(path, maximum):
    require(not path.is_symlink(), 'Symlink is not a control input: ' + str(path))
    require(path.stat().st_size <= maximum, 'Input exceeds bounded control size: ' + str(path))
    with path.open('rb') as stream:
        raw = stream.read(maximum + 1)
    require(len(raw) <= maximum, 'Input grew past bounded control size: ' + str(path))
    return raw


def write_new(path, value):
    with path.open('x') as stream:
        json.dump(value, stream, indent=2, allow_nan=False)
        stream.write('\n')
        stream.flush()
        os.fsync(stream.fileno())


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def timestamp(text):
    require(isinstance(text, str), 'Missing timestamp')
    value = datetime.fromisoformat(text.replace('Z', '+00:00'))
    require(value.tzinfo is not None and value.utcoffset().total_seconds() == 0, 'Timestamp must carry UTC')
    return value


def unavailable(item):
    return isinstance(item, dict) and item.get('value') is None and isinstance(item.get('reason'), str) and bool(item['reason'].strip())


def series(item, unit, *, allow_missing=False, integer=False):
    require(isinstance(item, dict) and item.get('unit') == unit, 'Unexpected series/unit')
    values = item.get('values')
    if values is None:
        require(allow_missing and isinstance(item.get('reason'), str) and item['reason'].strip(), 'Unexplained missing series')
        return []
    require(isinstance(values, list) and 1 <= len(values) <= 16 and item.get('reason') is None, 'Invalid/bound-exceeding observed series')
    require(all(type(v) in (int, float) and math.isfinite(v) and (v >= 0 if integer else v > 0) for v in values), 'Invalid numeric sample')
    require(not integer or all(type(v) is int for v in values), 'Heap samples must be actual integer bytes')
    return values


def validate_capacity(raw):
    require(raw.get('schema') == 'ungravity.benchmark-observations.v1', 'Unsupported observation schema')
    require(raw.get('run_id') == RUNS['capacity16'] and raw.get('scenario') == SCENARIOS['capacity16'], 'Wrong/old run identity')
    issues = raw.get('known_issues')
    require(isinstance(issues, list) and all(isinstance(s, str) for s in issues), 'Missing issues/status')
    require([s for s in issues if s.startswith('capture_status=')] == ['capture_status=truncated_sample_capacity'], 'Not one explicit capacity close')
    require('requested_sample_seconds=30.0; max_samples_per_series=16' in issues, 'Wrong requested window/capacity')
    require(not any('Non-positive monotonic span' in s or 'inconsistent JVM heap' in s or 'BENCHMARK_RECORDER FAIL' in s for s in issues), 'Invalid clock/heap capture')
    window = raw.get('window')
    require(isinstance(window, dict) and isinstance(window.get('value'), dict) and window.get('reason') is None, 'No observed window')
    observed = window['value']
    duration = (timestamp(observed['ended_at']) - timestamp(observed['started_at'])).total_seconds()
    require(0 < duration < 30, 'Capacity control did not close within its requested window')
    require(type(observed.get('warmup_seconds')) in (int, float) and math.isfinite(observed['warmup_seconds']) and observed['warmup_seconds'] >= 1, 'Warmup contract not observed')
    context = raw.get('context', {})
    require(all(unavailable(context.get(name)) for name in NULL_CONTEXT), 'Invented/missing-reason context metric')
    maximum = context.get('heap_max_bytes', {}).get('value')
    require(type(maximum) is int and maximum > 0, 'Missing observed JVM heap maximum')
    require(context.get('gpu_hardware', {}).get('value') == 'software', 'Expected software-renderer control; no hardware claim permitted')
    samples = raw.get('samples', {})
    frames = series(samples.get('frame_time_ms'), 'ms')
    ticks = series(samples.get('server_tick_ms'), 'ms', allow_missing=True)
    heaps = series(samples.get('heap_used_bytes'), 'bytes', integer=True)
    require(all(v <= maximum for v in heaps), 'Used heap exceeds observed maximum')
    for name in NULL_SERIES:
        item = samples.get(name)
        require(isinstance(item, dict) and item.get('values') is None and isinstance(item.get('reason'), str) and item['reason'].strip(), 'Invented/unexplained unavailable sample series')
    counts = {'frame_time_ms': len(frames), 'server_tick_ms': len(ticks), 'heap_used_bytes': len(heaps)}
    require(max(counts.values()) == 16, 'No series actually reached capacity16')
    # CPU frame intervals, enclosing tick spans and UTC window are different observations.
    # Do not require their sums to equal each other or infer a complete benchmark window.
    return {'counts': counts, 'utc_window_seconds': duration, 'frame_interval_sum_ms': sum(frames),
            'server_tick_span_sum_ms': sum(ticks), 'coverage_is_complete': False}


def png_complete(raw):
    """Checks complete chunk framing/CRC, not pixels or graphics quality."""
    if not raw.startswith(b'\x89PNG\r\n\x1a\n'):
        return False
    position = 8
    header = False
    data = False
    while position + 12 <= len(raw):
        size = struct.unpack('>I', raw[position:position + 4])[0]
        end = position + 12 + size
        if end > len(raw):
            return False
        kind = raw[position + 4:position + 8]
        payload = raw[position + 8:position + 8 + size]
        checksum = struct.unpack('>I', raw[position + 8 + size:end])[0]
        if zlib.crc32(kind + payload) & 0xffffffff != checksum:
            return False
        if position == 8:
            header = kind == b'IHDR' and size == 13 and all(v > 0 for v in struct.unpack('>II', payload[:8]))
        if kind == b'IDAT' and size > 0:
            data = True
        if kind == b'IEND':
            return header and data and size == 0 and end == len(raw)
        position = end
    return False


def identity(path, raw):
    stat = path.stat()
    return {'sha256': sha(raw), 'bytes': len(raw), 'device': stat.st_dev, 'inode': stat.st_ino,
            'modified_ns': stat.st_mtime_ns, 'changed_ns': stat.st_ctime_ns}


class Observation:
    def __init__(self, case, runtime, run, out):
        require(case in RUNS, 'Unsupported case')
        self.case, self.runtime, self.run, self.out = case, runtime, run, out
        self.first = None
        self.baseline_names = set()
        self.later_frames = {}
        self.polls = 0
        self.first_log_bytes = None
        self.raw_summary = None
        self.started = time.monotonic_ns()
        self.events = (out / 'events.jsonl').open('x')

    def event(self, kind, **fields):
        row = {'event': kind, 'observed_utc': utc_now(), 'observed_monotonic_ns': time.monotonic_ns(), **fields}
        self.events.write(json.dumps(row, allow_nan=False) + '\n')
        self.events.flush()
        os.fsync(self.events.fileno())
        return row

    def close(self):
        self.events.close()

    def outputs(self):
        directory = self.runtime / 'benchmark-observations'
        if not directory.exists():
            return []
        require(directory.is_dir() and not directory.is_symlink(), 'Unexpected observation path type')
        entries = list(directory.iterdir())
        require(len(entries) <= 4, 'Unexpected output directory fan-out')
        require(all(p.is_file() and not p.is_symlink() for p in entries), 'Unexpected output member type')
        return entries

    def log_bytes(self):
        path = self.run / 'native.log'
        return read_bounded(path, MAX_LOG) if path.exists() else b''

    def poll(self):
        self.polls += 1
        outputs = self.outputs()
        log = self.log_bytes()
        require(b'BENCHMARK_RECORDER FAIL' not in log, 'Producer reported failure')
        if self.case == 'disabled':
            require(not outputs, 'Disabled recorder emitted a file')
            require(b'BENCHMARK_RECORDER armed' not in log and b'BENCHMARK_RECORDER wrote' not in log, 'Disabled recorder armed/emitted')
            return
        expected = 'benchmark-' + RUNS[self.case] + '.json'
        require(not outputs or len(outputs) == 1 and outputs[0].name == expected, 'Duplicate/unexpected recorder output')
        if not outputs:
            require(self.first is None, 'Closed output disappeared')
            return
        path = outputs[0]
        raw = read_bounded(path, MAX_RAW)
        current = identity(path, raw)
        if self.first is None:
            try:
                decoded = json.loads(raw)
            except (UnicodeError, json.JSONDecodeError):
                return  # producer may still be writing; incomplete JSON is not a capture
            self.raw_summary = validate_capacity(decoded)
            self.first = current
            folder = self.runtime / 'mounted-mechanism-frames'
            self.baseline_names = {p.name for p in folder.glob('*.png')}
            self.first_log_bytes = len(log)
            with (self.out / 'first-complete-output.json').open('xb') as stream:
                stream.write(raw)
            self.event('first_complete_output', identity=current, existing_png_names=sorted(self.baseline_names),
                       native_log_bytes=self.first_log_bytes, raw_summary=self.raw_summary)
        else:
            require(current == self.first, 'Closed output changed/replaced after first complete observation')
        folder = self.runtime / 'mounted-mechanism-frames'
        for frame in folder.glob('*.png'):
            if frame.name in self.baseline_names or frame.name in self.later_frames:
                continue
            raw_png = read_bounded(frame, MAX_PNG)
            if png_complete(raw_png):
                self.later_frames[frame.name] = self.event('later_completed_client_frame', name=frame.name,
                    sha256=sha(raw_png), bytes=len(raw_png))

    def finish(self, child_exit, gate):
        self.poll()
        require(child_exit.get('returncode') == 0 and child_exit.get('timed_out') is False and child_exit.get('forced_kill') is False, 'Native child did not close cleanly')
        require(gate.get('frames') == SCENES and gate.get('actualUseAndSneakKeys') is True and gate.get('operatorFollowTeleport') is False and gate.get('fixtureSuppliedJointOrShots') is False, 'Existing mounted gate incomplete or replaced by fixture behavior')
        folder = self.runtime / 'mounted-mechanism-frames'
        frames = list(folder.glob('*.png'))
        require(len(frames) == 5 and all(png_complete(read_bounded(p, MAX_PNG)) for p in frames), 'Five complete mounted PNGs required')
        log = self.log_bytes()
        armed = len(re.findall(rb'BENCHMARK_RECORDER armed\b', log))
        wrote = len(re.findall(rb'BENCHMARK_RECORDER wrote\b', log))
        result = {'case': self.case, 'source': SOURCE, 'run_id': RUNS[self.case], 'polls': self.polls,
                  'armed_lines': armed, 'wrote_lines': wrote, 'child_exit': child_exit,
                  'native_log_sha256': sha(log), 'full_s16_accepted': False, 'performance_accepted': False,
                  'release_accepted': False, 'scope': 'Instrumentation output/lifecycle only; software rendering, no hardware/performance certificate'}
        if self.case == 'disabled':
            require(armed == wrote == 0 and not self.outputs(), 'Disabled zero-emission contract failed')
            result.update(status='PASS_DISABLED_ZERO_EMISSION', allocation_measurement_performed=False)
        else:
            require(self.first is not None and armed == wrote == 1, 'Expected exactly one armed and one wrote event')
            require(self.later_frames, 'No actual later completed client PNG after first complete output')
            tail = log[self.first_log_bytes:]
            require(re.search(rb'\[Render thread/[^\n]*MOUNTED_', tail) is not None, 'No later observed render-thread gate activity')
            result.update(status='PASS_CAPACITY16_CLOSED_OUTPUT', output_identity=self.first,
                          later_client_frames=sorted(self.later_frames), raw_summary=self.raw_summary,
                          timing_counts_are_deterministic=False, closure_contract_is_interleaving_independent=True)
        return result


def validate_inputs(manifest_path, case):
    require(case in RUNS, 'Unsupported case')
    for name in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        require(not any(value.startswith(('-Ddrmd.gate.', '-Ddrmd.benchmark.')) for value in shlex.split(os.environ.get(name, ''))), 'Controlled flags in external Java environment')
    manifest_raw = read_bounded(manifest_path, MAX_RAW)
    manifest = json.loads(manifest_raw)
    run = manifest_path.parent.resolve()
    require(manifest.get('runner_sha256') == EXECUTOR and sha(read_bounded(run / 'runner.snapshot.py', MAX_RAW)) == EXECUTOR, 'Frozen executor bytes differ')
    launch_raw = read_bounded(run / 'launch.snapshot.json', MAX_RAW)
    require(sha(launch_raw) == manifest.get('launch_sha256'), 'Launch snapshot differs')
    require(sha(read_bounded(Path(manifest['original_launch_path']), MAX_RAW)) == manifest.get('original_launch_sha256'), 'Original launch bytes changed')
    args = json.loads(launch_raw)
    require(isinstance(args, list) and all(isinstance(a,str) for a in args) and args.count('--gameDir') == 1, 'Ambiguous actual game directory')
    runtime = Path(args[args.index('--gameDir') + 1]).resolve()
    require(runtime == Path(manifest['runtime']).resolve(), 'Runtime/launch mismatch')
    expected = {'-Ddrmd.gate.mountedMechanisms': 'true'}
    if case == 'capacity16':
        expected.update({'-Ddrmd.gate.benchmark': 'true', '-Ddrmd.benchmark.mode': 'sidecar',
            '-Ddrmd.benchmark.warmupSeconds': '1', '-Ddrmd.benchmark.sampleSeconds': '30',
            '-Ddrmd.benchmark.maxSamples': '16', '-Ddrmd.benchmark.runId': RUNS[case],
            '-Ddrmd.benchmark.scenario': SCENARIOS[case]})
    actual = {}
    for arg in args:
        if arg.startswith(('-Ddrmd.gate.', '-Ddrmd.benchmark.')):
            require('=' in arg, 'Implicit controlled property')
            name, value = arg.split('=', 1)
            require(name not in actual, 'Duplicate controlled property')
            actual[name] = value
    require(actual == expected, 'Wrong disabled/capacity flag contract')
    require(manifest.get('contract', {}).get('tested_source') == SOURCE and manifest['contract']['case'] == case, 'Missing frozen control contract identity')
    require(all(sha(read_bounded(runtime / 'mods' / name, 8 * MAX_RAW)) == wanted for name, wanted in JARS.items()), 'Actual staged game/observer hashes differ')
    mods = list((runtime / 'mods').iterdir())
    require(1 <= len(mods) <= 32 and all(p.is_file() and not p.is_symlink() and p.suffix == '.jar' for p in mods), 'Unexpected staged mods')
    staged = {p.name:sha(read_bounded(p, 8 * MAX_RAW)) for p in sorted(mods)}
    return {'runtime': str(runtime), 'run': str(run), 'runner_manifest': str(manifest_path.resolve()),
            'runner_manifest_sha256': sha(manifest_raw), 'launch_sha256': sha(launch_raw),
            'case': case, 'source': SOURCE, 'jars': JARS,'staged_mods_not_loaded_registry':staged}


def prepare(manifest_path, case, out):
    require(case in RUNS and not out.exists(), 'Fresh observer directory and supported case required')
    inputs = validate_inputs(manifest_path, case)
    run = Path(inputs['run'])
    require(not (run / 'native.log').exists() and not (run / 'child-exit.json').exists(), 'Observer must start before actual native execution')
    out.mkdir(parents=True)
    code = Path(__file__).read_bytes()
    path = out / 'observer.snapshot.py'
    with path.open('xb') as stream:
        stream.write(code)
    path.chmod(0o444)
    write_new(out / 'observer-inputs.json', {'inputs': inputs, 'observer_sha256': sha(code),
        'poll_seconds': 0.2, 'deadline_seconds': 270,
        'note': 'Only external read-only observation. No Java launch or client/world mutation.'})
    (out / 'observer-inputs.json').chmod(0o444)


def verify_observer_bytes(config_path, initial_config_sha, code_path, expected_code_sha):
    require(sha(read_bounded(config_path, MAX_RAW)) == initial_config_sha, 'Observer configuration changed')
    require(sha(read_bounded(code_path, MAX_RAW)) == expected_code_sha, 'Actual observer snapshot changed')


def watch(config_path):
    out = config_path.parent.resolve()
    require(Path(__file__).resolve() == out / 'observer.snapshot.py', 'Execute actual prepared observer snapshot')
    initial_config = read_bounded(config_path, MAX_RAW)
    config = json.loads(initial_config)
    require(config['poll_seconds'] == 0.2 and config['deadline_seconds'] == 270, 'Observer bound contract changed')
    verify_observer_bytes(config_path, sha(initial_config), Path(__file__), config['observer_sha256'])
    inputs = config['inputs']
    require(validate_inputs(Path(inputs['runner_manifest']), inputs['case']) == inputs, 'Prepared native inputs changed')
    run, runtime = Path(inputs['run']), Path(inputs['runtime'])
    require(not (run / 'native.log').exists() and not (run / 'child-exit.json').exists(), 'Late observer start is not lifecycle evidence')
    observer = Observation(inputs['case'], runtime, run, out)
    try:
        observer.event('watch_started', inputs=inputs)
        write_new(out / 'ready.json', {'observer_sha256': config['observer_sha256'],
            'observer_config_sha256': sha(initial_config), 'observed_utc': utc_now()})
        deadline = time.monotonic() + config['deadline_seconds']
        while True:
            observer.poll()
            exit_path = run / 'child-exit.json'
            if exit_path.exists():
                try:
                    child_exit = json.loads(read_bounded(exit_path, MAX_RAW))
                except json.JSONDecodeError:
                    child_exit = None
                if child_exit is not None:
                    gate = json.loads(read_bounded(runtime / 'mounted-mechanisms-ok.json', MAX_RAW))
                    outcome = observer.finish(child_exit, gate)
                    require(validate_inputs(Path(inputs['runner_manifest']), inputs['case']) == inputs, 'Inputs changed during native run')
                    verify_observer_bytes(config_path, sha(initial_config), Path(__file__), config['observer_sha256'])
                    outcome.update(observer_sha256=config['observer_sha256'], observer_config_sha256=sha(initial_config))
                    write_new(out / 'observer-outcome.json', outcome)
                    return
            require(time.monotonic() < deadline, 'Observer deadline; no valid clean native completion observed')
            time.sleep(config['poll_seconds'])
    except Exception as error:
        observer.event('observation_failed', error=repr(error))
        write_new(out / 'observer-failure.json', {'error': repr(error), 'observed_utc': utc_now(),
            'source': SOURCE, 'case': inputs['case'], 'native_exit_not_inferred': True, 'performance_accepted': False})
        raise
    finally:
        observer.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    prep = commands.add_parser('prepare')
    prep.add_argument('--runner-manifest', type=Path, required=True)
    prep.add_argument('--case', choices=RUNS, required=True)
    prep.add_argument('--out', type=Path, required=True)
    observe = commands.add_parser('watch')
    observe.add_argument('--config', type=Path, required=True)
    args = parser.parse_args()
    if args.command == 'prepare':
        prepare(args.runner_manifest, args.case, args.out)
    else:
        watch(args.config)

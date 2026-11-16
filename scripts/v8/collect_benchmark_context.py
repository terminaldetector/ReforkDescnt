#!/usr/bin/env python3
"""Collect S16 provenance and observed samples without launching or accepting a benchmark.

Only local Git/file reads and Python's standard library are used. Missing measurements
remain null with a reason. Staged JARs do not establish what a client actually loaded.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import re
import stat
import sys
import zipfile

from audit_task_evidence import AuditError, Repository


class ContextError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise ContextError(message)


def nonempty(value):
    return isinstance(value, str) and bool(value.strip())


def integer(value, minimum=0, maximum=2**63 - 1):
    return isinstance(value, int) and not isinstance(value, bool) and minimum <= value <= maximum


def finite(value):
    try:
        return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)
    except OverflowError:
        return False


def text(value):
    return nonempty(value) and value.strip().upper() not in {"UNKNOWN", "N/A", "UNAVAILABLE", "NOT_PROFILED"}


FIELDS = {
    "seed": lambda x: integer(x, -(2**63)),
    "coords": lambda x: isinstance(x, list) and len(x) == 3 and all(finite(v) for v in x),
    "dimension": lambda x: isinstance(x, str) and re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", x),
    "flight_state": text,
    "runtime_source_sha": lambda x: isinstance(x, str) and re.fullmatch(r"[0-9a-f]{40}", x),
    "runtime_game_sha256": lambda x: isinstance(x, str) and re.fullmatch(r"[0-9a-f]{64}", x),
    "java_version": text,
    "fabric_loader_version": text,
    "sodium_version": text,
    "cubic_backend": text,
    "ps1_profile": lambda x: x in ("native", "ps1", "debug"),
    "hybrid_on": lambda x: isinstance(x, bool),
    "expanded_physics": lambda x: isinstance(x, bool),
    "view_distance": integer,
    "sim_distance": integer,
    "gpu_vendor": text,
    "gpu_renderer": text,
    "gpu_version": text,
    "gpu_hardware": lambda x: x in ("physical", "software", "unknown"),
    "heap_max_bytes": lambda x: integer(x, 1),
    "requested_entities": integer,
    "active_entities": integer,
    "moving_entities": integer,
    "rendered_entities": integer,
    "ragdolls": integer,
    "rigid_bodies": integer,
    "cubes_loaded": integer,
    "mesh_queue": integer,
    "draw_calls": integer,
}
SERIES = {
    "server_tick_ms": "ms", "frame_time_ms": "ms", "gpu_frame_ms": "ms",
    "heap_used_bytes": "bytes", "cubes_loaded": "count", "draw_calls": "count", "mesh_queue": "count",
}
OBSERVATION_SCHEMA = "ungravity.benchmark-observations.v1"


def keys(value, allowed, label):
    require(isinstance(value, dict), label + " must be an object")
    require(set(value) <= set(allowed), label + ": unsupported fields " + str(sorted(set(value) - set(allowed))))


def unavailable(reason):
    return dict(value=None, reason=reason, evidence=None)


def strict_json(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, "duplicate JSON key: " + key)
            result[key] = value
        return result
    def constant(value):
        raise ContextError("non-finite JSON constant: " + value)
    return json.loads(raw, object_pairs_hook=pairs, parse_constant=constant)


def signature(path):
    s = path.lstat()
    require(stat.S_ISREG(s.st_mode), "input must be a regular file, not a symlink: " + str(path))
    return s.st_dev, s.st_ino, s.st_size, s.st_mtime_ns


def file_hash(path):
    before = signature(path)
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    require(signature(path) == before, "input changed during hashing: " + str(path))
    return h.hexdigest(), before


def json_file(path):
    before = signature(path)
    require(before[2] <= 64 * 1024 * 1024, "JSON exceeds supported 64 MiB input size")
    raw = path.read_bytes()
    require(signature(path) == before, "JSON changed while reading: " + str(path))
    return strict_json(raw), dict(path=str(path.absolute()), sha256=hashlib.sha256(raw).hexdigest()), before


def artifact(path):
    digest, before = file_hash(path)
    with zipfile.ZipFile(path) as jar:
        require(jar.namelist().count("fabric.mod.json") == 1, "expected one Fabric metadata entry: " + str(path))
        info = jar.getinfo("fabric.mod.json")
        require(info.file_size <= 1024 * 1024, "Fabric metadata exceeds supported size")
        metadata = strict_json(jar.read(info))
    require(isinstance(metadata, dict) and type(metadata.get("schemaVersion")) is int and metadata["schemaVersion"] == 1,
            "only Fabric schemaVersion 1 JARs are supported")
    require(isinstance(metadata.get("id"), str) and re.fullmatch(r"[a-z][a-z0-9_-]{1,63}", metadata["id"]),
            "invalid Fabric mod ID")
    require(nonempty(metadata.get("version")) and not re.search(r"[$}{]", metadata["version"]), "missing concrete Fabric mod version")
    require(signature(path) == before, "JAR changed during metadata reading: " + str(path))
    return dict(path=str(path.absolute()), sha256=digest, bytes=before[2],
                id=metadata["id"], version=metadata["version"]), before


def descriptor(record, validate, label, evidence, pointer):
    keys(record, {"value", "reason"}, label)
    value = record.get("value")
    if value is None:
        require(nonempty(record.get("reason")), label + ": null requires an unavailability reason")
        return unavailable(record["reason"])
    require(record.get("reason") is None, label + ": observed value cannot also claim unavailability")
    require(validate(value), label + ": unsupported value/type")
    return dict(value=value, reason=None, evidence=dict(**evidence, pointer=pointer))


def window_value(value):
    keys(value, {"started_at", "ended_at", "warmup_seconds"}, "window value")
    require(set(value) == {"started_at", "ended_at", "warmup_seconds"}, "incomplete observation window")
    times = []
    for key in ("started_at", "ended_at"):
        require(isinstance(value[key], str), "window timestamps must be ISO-8601 strings")
        try:
            dt = datetime.fromisoformat(value[key].replace("Z", "+00:00"))
        except ValueError as exc:
            raise ContextError("invalid ISO-8601 observation timestamp") from exc
        require(dt.tzinfo is not None and dt.utcoffset() is not None, "window timestamps require timezones")
        times.append(dt)
    require(times[1] > times[0], "observation window must have positive duration")
    require(finite(value["warmup_seconds"]) and value["warmup_seconds"] >= 0, "invalid warmup duration")
    return True


def summary(values):
    ordered = sorted(values)
    count = len(ordered)
    return dict(samples=count, minimum=ordered[0], maximum=ordered[-1],
                mean=sum(v / count for v in ordered),
                **{"p%d" % p: ordered[(p * count + 99) // 100 - 1] for p in (50, 95, 99)})


def observations(path):
    data, evidence, before = json_file(path)
    keys(data, {"schema", "run_id", "scenario", "window", "context", "samples", "known_issues"}, "observations")
    require(data.get("schema") == OBSERVATION_SCHEMA, "unsupported observation schema")
    for field in ("run_id", "scenario"):
        require(nonempty(data.get(field)) and not data[field].startswith("REPLACE_"), "missing actual " + field)
    context, samples = data.get("context", {}), data.get("samples", {})
    keys(context, FIELDS, "context")
    keys(samples, SERIES, "samples")
    window = descriptor(data.get("window", dict(value=None, reason="Sampling window not observed")),
                        window_value, "window", evidence, "/window/value")
    captured = {}
    for field, validate in FIELDS.items():
        captured[field] = descriptor(context[field], validate, field, evidence, "/context/" + field + "/value") if field in context else unavailable("Field not supplied by runtime observer")
    raw, metrics = {}, {}
    for name, unit in SERIES.items():
        if name not in samples:
            metrics[name] = dict(**unavailable("Raw samples not supplied by runtime observer"), unit=unit)
            continue
        record = samples[name]
        keys(record, {"values", "reason", "unit"}, name)
        require(record.get("unit") == unit, name + ": unsupported unit")
        values = record.get("values")
        if values is None:
            require(nonempty(record.get("reason")), name + ": null samples require a reason")
            metrics[name] = dict(**unavailable(record["reason"]), unit=unit)
            continue
        require(record.get("reason") is None, name + ": observed samples cannot also claim unavailability")
        require(window["value"] is not None, "raw samples require an observed sampling window")
        require(isinstance(values, list) and bool(values), name + ": raw samples must be a nonempty array")
        require(all(finite(v) and v > 0 for v in values) if unit == "ms" else all(integer(v) for v in values),
                name + ": unsupported sample/type/range")
        raw[name] = values
        metrics[name] = dict(value=summary(values), reason=None, unit=unit,
                             evidence=dict(**evidence, pointer="/samples/" + name + "/values"))
    if "frame_time_ms" in raw:
        fps = [1000 / value for value in raw["frame_time_ms"]]
        require(all(math.isfinite(value) for value in fps), "frame durations produce non-finite FPS")
        metrics["client_fps"] = dict(value=summary(fps), reason=None, unit="frames/second",
                                     evidence=dict(**evidence, pointer="/samples/frame_time_ms/values"),
                                     derivation="1000 / each positive frame duration; nearest-rank percentiles of FPS samples")
    else:
        metrics["client_fps"] = dict(**unavailable("No observed frame-duration samples"), unit="frames/second")
    maximum = captured["heap_max_bytes"]["value"]
    if maximum is not None and "heap_used_bytes" in raw:
        require(max(raw["heap_used_bytes"]) <= maximum, "used heap exceeds reported JVM maximum")
    renderer = captured["gpu_renderer"]["value"]
    if renderer and re.search(r"llvmpipe|softpipe|swiftshader|software rasterizer", renderer, re.I):
        require(captured["gpu_hardware"]["value"] != "physical", "software renderer cannot attest physical GPU measurements")
    issues = data.get("known_issues", [])
    require(isinstance(issues, list) and all(nonempty(issue) for issue in issues), "known_issues must be an array of nonempty strings")
    return dict(run_id=data["run_id"], scenario=data["scenario"], window=window,
                context=captured, metrics=metrics, known_issues=issues, input=evidence), before


def collect(repo_path, source, game_path, mods_dir, observation_path, build_stamp=None):
    repo = Repository(repo_path)
    head = repo.git("rev-parse", "HEAD").decode().strip()
    source = repo.commit(source or head)
    observed, observation_sig = observations(observation_path)
    game, game_sig = artifact(game_path)
    require(game["id"] == "drmd", "game JAR must have Fabric id drmd")
    require(mods_dir.is_dir() and not mods_dir.is_symlink(), "mods directory must be a real directory")
    children = sorted(mods_dir.iterdir())
    require(not any(p.is_dir() or p.is_symlink() for p in children), "nested/symlink mods entries are unsupported")
    paths = [p for p in children if p.suffix.lower() == ".jar"]
    require(bool(paths), "staged mods directory has no JARs")
    mods, signatures = [], {}
    for path in paths:
        mod, sig = artifact(path)
        mods.append(mod)
        signatures[path] = sig
    require(len({m["id"] for m in mods}) == len(mods), "duplicate staged outer mod ID")
    games = [m for m in mods if m["id"] == "drmd"]
    require(len(games) == 1 and games[0]["sha256"] == game["sha256"], "staged game JAR differs from supplied game artifact")
    inputs = repo.source_inputs(source)
    association = unavailable("No build stamp supplied; source/JAR association has not been established")
    if build_stamp is not None:
        stamp, stamp_evidence, stamp_sig = json_file(build_stamp)
        require(isinstance(stamp, dict) and stamp.get("inputs") == inputs, "build stamp inputs differ from pinned source")
        require(isinstance(stamp.get("jars"), dict)
                and stamp["jars"].get("build/libs/" + game_path.name) == game["sha256"], "build stamp game digest differs")
        association = dict(value="recorded_build_inputs_and_game_digest_match", reason=None, evidence=stamp_evidence)
        require(signature(build_stamp) == stamp_sig, "build stamp changed during collection")
    for field, expected in (("runtime_source_sha", source), ("runtime_game_sha256", game["sha256"])):
        actual = observed["context"][field]["value"]
        require(actual is None or actual == expected, field + " differs from collected provenance")
    production = ["src", "build.gradle", "settings.gradle", "gradle.properties"]
    dirty = repo.git("diff", "--name-only", "HEAD", "--", *production).decode().splitlines()
    untracked = repo.git("ls-files", "--others", "--exclude-standard", "--", *production).decode().splitlines()
    require(repo.git("rev-parse", "HEAD").decode().strip() == head, "checkout HEAD changed during collection")
    require(sorted(mods_dir.iterdir()) == children and all(signature(p) == sig for p, sig in signatures.items()),
            "staged mods changed during collection")
    require(signature(game_path) == game_sig and signature(observation_path) == observation_sig, "benchmark inputs changed during collection")
    if build_stamp is not None:
        require(signature(build_stamp) == stamp_sig, "build stamp changed during collection")
    missing_context = [k for k, v in observed["context"].items() if v["value"] is None]
    missing_series = [k for k, v in observed["metrics"].items() if v["value"] is None]
    return dict(schema="ungravity.benchmark-context.v1", collected_at=datetime.now(timezone.utc).isoformat(),
                collector=dict(python=sys.version.split()[0], script_sha256=file_hash(Path(__file__))[0],
                               git_reader_sha256=file_hash(Path(__file__).with_name("audit_task_evidence.py"))[0]),
                **observed, source=dict(revision=source, checkout_head=head, committed_input_count=len(inputs),
                    input_digest=hashlib.sha256(json.dumps(inputs, sort_keys=True).encode()).hexdigest(),
                    tracked_input_changes_from_head=dirty, untracked_input_paths=untracked,
                    jar_association=association),
                artifacts=dict(game=game, staged_outer_mods=mods, ignored_nonjar_entries=[p.name for p in children if p not in paths],
                    loaded_runtime_mods=unavailable("Only staged outer JARs inventoried; loaded/nested/loader mods require runtime observation")),
                completeness=dict(missing_context=missing_context, missing_series=missing_series,
                    observed_window=observed["window"]["value"] is not None),
                sample_coverage_in_window_verified=False,
                gameplay_launched=False, performance_accepted=False, release_accepted=False,
                scope="Local provenance plus supplied observations only; no runtime loading, sample authenticity, benchmark thresholds or physical-hardware acceptance is inferred.")


def template():
    return dict(schema=OBSERVATION_SCHEMA, run_id="REPLACE_WITH_ACTUAL_RUN_ID", scenario="REPLACE_WITH_ACTUAL_SCENARIO",
                window=dict(value=None, reason="Sampling window not observed"),
                context={k: dict(value=None, reason="Not instrumented") for k in FIELDS},
                samples={k: dict(values=None, unit=unit, reason="Not instrumented") for k, unit in SERIES.items()},
                known_issues=[])


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    sample = sub.add_parser("template", help="Write unavailable fields, never example benchmark numbers")
    sample.add_argument("--out", type=Path, required=True)
    run = sub.add_parser("collect")
    run.add_argument("--repo", type=Path, default=Path("."))
    run.add_argument("--source", help="Exact local Git source SHA; defaults to HEAD, not assumed to have built the JAR")
    for arg in ("game-jar", "mods-dir", "observations", "out"):
        run.add_argument("--" + arg, type=Path, required=True)
    run.add_argument("--build-stamp", type=Path)
    args = parser.parse_args(argv)
    try:
        require(not args.out.exists() and not args.out.is_symlink(), "output already exists; choose a fresh result path")
        if args.command == "template":
            result = template()
        else:
            result = collect(args.repo, args.source, args.game_jar, args.mods_dir, args.observations, args.build_stamp)
        serialized = json.dumps(result, indent=2, allow_nan=False) + "\n"
        args.out.parent.mkdir(parents=True, exist_ok=True)
        with args.out.open("x") as output:
            output.write(serialized)
    except (ContextError, AuditError, OSError, ValueError, KeyError, TypeError, OverflowError, zipfile.BadZipFile) as exc:
        print("CONTEXT FAIL: " + str(exc), file=sys.stderr)
        return 1
    print("Wrote " + str(args.out) + "; no game launched or performance accepted.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

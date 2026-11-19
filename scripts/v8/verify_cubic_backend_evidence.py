#!/usr/bin/env python3
"""Recheck actual BCC bytes and preserved smoke/load evidence; never launch or modify a world.

Historical verification retains the original source/JAR identities. It is not a current-runtime,
upstream-only C0, hardware, migration, or complete light/fluid/BlockEntity acceptance claim.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

BCC_SHA = "8660f6e18fafaaa1dea2ef344e86188a943b0f3661c65fdcd294f326f3c6e8b6"


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def file_receipt(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return {"path": str(path.resolve()), "bytes": path.stat().st_size, "sha256": h.hexdigest()}


def checked_archive(path):
    archive = zipfile.ZipFile(path)
    require(archive.testzip() is None, f"ZIP CRC failure: {path}")
    return archive, {**file_receipt(path), "zipCrc": "PASS", "entries": len(archive.infolist())}


def witnesses(text, patterns):
    lines = text.splitlines()
    result = []
    for pattern in patterns:
        found = [(i + 1, line) for i, line in enumerate(lines) if re.search(pattern, line)]
        require(found, f"missing actual log witness: {pattern}")
        line, value = found[0]
        result.append({"pattern": pattern, "line": line, "text": value})
    return result


def upstream(path):
    archive, receipt = checked_archive(path)
    with archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        expected = {"id": "bettercubicchunks", "version": "0.1.1+mc1.21.1", "environment": "*", "license": "All-Rights-Reserved"}
        require(all(metadata.get(k) == v for k, v in expected.items()), "unexpected upstream identity/license")
        require(receipt["sha256"] == BCC_SHA and receipt["bytes"] == 806910, "upstream artifact changed")
        require(metadata["depends"] == {"fabricloader": ">=0.19.5", "minecraft": "~1.21.1", "java": ">=21", "fabric-api": "*"}, "upstream dependency metadata changed")
        license_bytes = archive.read("LICENSE_bettercubicchunks")
        require(b"all rights reserved" in license_bytes.lower(), "license resource differs from metadata")
        dimension = json.loads(archive.read("data/bettercubicchunks/dimension_type/overworld.json"))
        require(dimension["min_y"] == -16384 and dimension["height"] == 32768, "native dimension bounds changed")
        preset = json.loads(archive.read("data/bettercubicchunks/worldgen/world_preset/cubic_default.json"))
        require(preset["dimensions"]["minecraft:overworld"]["type"] == "bettercubicchunks:overworld", "cubic preset does not select native dimension type")
        return {"status": "PASS", "artifact": receipt, "metadata": metadata,
                "licenseResourceSha256": hashlib.sha256(license_bytes).hexdigest(),
                "buildManifest": archive.read("META-INF/MANIFEST.MF").decode(),
                "nativeHeight": {"bottomY": -16384, "topYExclusive": 16384, "sections": 2048},
                "preset": "bettercubicchunks:cubic_default", "implementationSourceCopied": False}


def historical(checkpoints, world_tree, repository):
    first, first_receipt = checked_archive(checkpoints / "gate0-server/evidence.zip")
    second, second_receipt = checked_archive(checkpoints / "cubic-lighting-1.1.18/native-evidence.zip")
    access, access_receipt = checked_archive(checkpoints / "world-access-1.1.19/native-evidence.zip")
    with first, second, access:
        one = first.read("server-fixed.log").decode()
        two_name = "work/gate0/server-seed2.log"
        two = second.read(two_name).decode()
        common = [r"No existing world data, creating new world", r"Cubic streaming enabled for minecraft:overworld", r"Stamped world with position packing TALL", r"Done \(", r"Starting minecraft server version 1\.21\.1"]
        one_result = json.loads(first.read("result.json"))
        two_inputs = json.loads(second.read("work/gate0/verified-seed2-server/inputs.json"))
        one_jar = checkpoints / "gate0-server/drmd-6dof-1.1.16-physics.1.jar"
        two_jar = world_tree / "build/cubic-seed2-gate/mods/drmd-6dof-1.1.18-cubic.1.jar"
        one_artifact, two_artifact = file_receipt(one_jar), file_receipt(two_jar)
        require(one_artifact["sha256"] == one_result["candidate_sha256"], "first smoke game no longer matches original result")
        two_expected = next(x for x in two_inputs["mods"] if "drmd-6dof" in x["path"])
        require(two_artifact["sha256"] == two_expected["sha256"], "second smoke game no longer matches original inputs")
        require(any(x["sha256"] == BCC_SHA for x in two_inputs["mods"]), "second smoke upstream identity absent")
        new_saves = [{"seed": 261026, "sourceSha": one_result["physics_source_sha"], "game": one_artifact,
                      "logEntry": "server-fixed.log", "logSha256": hashlib.sha256(one.encode()).hexdigest(),
                      "witnesses": witnesses(one, common + [r"Seed: \[261026\]", r"y=\s+2144\.\.\s+2159\s+FULL", r"\.mcr3d"])},
                     {"seed": 271026, "sourceSha": two_inputs["source_sha"], "game": two_artifact,
                      "logEntry": two_name, "logSha256": hashlib.sha256(two.encode()).hexdigest(),
                      "witnesses": witnesses(two, common + [r"Seed: \[271026\]"])}]
        lifecycle_entry = "work/world-access/native-vertical-lifecycle.log"
        lifecycle_log = access.read(lifecycle_entry).decode()
        lifecycle = json.loads(access.read("build/nonblocking/cubic-seed2-gate/native-cube-lifecycle-ok.json"))
        require(lifecycle == {"status": "OBSERVED_NATIVE_LOAD_UNLOAD_PASS", "cube": [0,143,0], "parentColumnRetained": True, "singleExternalTicket": True, "observerTickets": 0, "ticks": 1300, "events": 4}, "native lifecycle evidence changed")
        source_sha = "0f76a0789fe8cb62ff98c3e08d6232f222d72cb8"
        client_root = "build/cubic-client-final-release-baseline-sodium/"
        client_inputs = json.loads(access.read(client_root + "inputs.json"))
        client_result = json.loads(access.read(client_root + "cubic-client-ok.json"))
        require(client_inputs["sourceSha"] == source_sha and "independent_vertical_be_unload" in client_result["checks"], "client source/independent unload evidence missing")
        client_game = file_receipt(checkpoints / "world-access-1.1.19/drmd-6dof-1.1.19-world.1-fabric-1.21.1.jar")
        require(client_game["sha256"] == next(x["sha256"] for x in client_inputs["artifacts"] if x["path"].endswith("/drmd-6dof-1.1.19-world.1.jar")), "client game differs from tested inputs")
        sources = {}
        for name in ("CubicClientGate", "NativeCubeLifecycleGate"):
            relative = f"src/cubiccheck/java/com/terminaldetector/drmd/gate/{name}.java"
            value = subprocess.check_output(["git", "-C", str(repository), "show", f"{source_sha}:{relative}"])
            sources[relative] = hashlib.sha256(value).hexdigest()
            if name == "CubicClientGate":
                require(b"getBottomY()!=-16384" in value and b"getHeight()!=32768" in value, "client bounds assertion absent from tested source")
            else:
                require(b"2150" in value and b"Vertical unload test lost" in value, "retained lower cube assertion absent")
        return {"status": "HISTORICAL_EVIDENCE_REVERIFIED", "archives": [first_receipt, second_receipt, access_receipt],
                "newSaves": new_saves, "nativeLifecycle": lifecycle,
                "lifecycleWitness": witnesses(lifecycle_log, [r"CUBIC_LIFECYCLE_PASS ticks=1300.*change=UNAVAILABLE.*change=AVAILABLE.*change=UNAVAILABLE.*change=CLOSED"]),
                "clientSourceSha": source_sha, "clientGame": client_game, "testedObserverSources": sources,
                "clientChecks": client_result["checks"],
                "scope": "Isolated historical Fabric+BCC+DRMD new saves, native bounds and independent 16^3 unload; not an upstream-only or current-candidate runtime rerun"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream", type=Path, required=True)
    parser.add_argument("--checkpoints", type=Path)
    parser.add_argument("--historical-world", type=Path)
    parser.add_argument("--repository", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = {"upstream": upstream(args.upstream)}
    if args.checkpoints:
        require(args.historical_world is not None, "--historical-world required with --checkpoints")
        result["historical"] = historical(args.checkpoints, args.historical_world, args.repository)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({"status": "PASS", "receipt": file_receipt(args.output)}))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Verify a pinned task inventory and its recorded, scoped evidence; never promote tasks.

Only Python's standard library and local Git objects are used. The working tree may be
dirty or sparse: source and ledger checks read the explicitly pinned commit, not HEAD.
Passing this audit means metadata and the described bundle agree. It is not a new game
run, an independent visual review, performance acceptance, or release authorization.
"""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import io
import json
import math
from pathlib import Path, PurePosixPath
import re
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
import zlib


class AuditError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise AuditError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def sha(value, length=64):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{%d}" % length, value) is not None


def relative_path(value):
    require(isinstance(value, str) and bool(value), "empty or non-string relative path")
    p = PurePosixPath(value)
    require(not p.is_absolute() and "\\" not in value and ":" not in value
            and ".." not in p.parts and str(p) == value,
            "unsafe or noncanonical relative path: " + value)
    return value


class Repository:
    def __init__(self, path):
        self.path = Path(path).resolve()
        self._trees = {}
        self._blobs = {}

    def git(self, *arguments, input=None):
        result = subprocess.run(["git", "-C", str(self.path), *arguments], input=input,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        require(result.returncode == 0,
                "local Git read failed: " + " ".join(arguments[:3]) + ": "
                + result.stderr.decode(errors="replace").strip())
        return result.stdout

    def commit(self, source):
        require(sha(source, 40), "source must be a full lowercase Git SHA: " + str(source))
        self.git("cat-file", "-e", source + "^{commit}")
        return source

    def tree(self, source):
        self.commit(source)
        if source not in self._trees:
            tree = {}
            for entry in self.git("ls-tree", "-rz", "--full-tree", source).split(b"\0"):
                if not entry:
                    continue
                header, name = entry.split(b"\t", 1)
                mode, kind, oid = header.decode().split()
                path = name.decode()
                if kind == "blob":
                    tree[path] = (mode, oid)
            self._trees[source] = tree
        return self._trees[source]

    def _load(self, oids):
        wanted = sorted(set(oids) - self._blobs.keys())
        if not wanted:
            return
        data = self.git("cat-file", "--batch", input=("\n".join(wanted) + "\n").encode())
        offset = 0
        for expected in wanted:
            end = data.index(b"\n", offset)
            oid, kind, size = data[offset:end].decode().split()
            require(oid == expected and kind == "blob", "unexpected Git object in source read")
            start = end + 1
            size = int(size)
            require(data[start + size:start + size + 1] == b"\n", "truncated Git blob stream")
            self._blobs[oid] = data[start:start + size]
            offset = start + size + 1
        require(offset == len(data), "unexpected trailing Git blob stream")

    def read(self, source, path):
        path = relative_path(path)
        tree = self.tree(source)
        require(path in tree, "missing pinned source path: " + path)
        mode, oid = tree[path]
        require(mode != "120000", "source anchor cannot silently follow a symlink: " + path)
        self._load([oid])
        return self._blobs[oid]

    def source_inputs(self, source):
        tree = self.tree(source)
        paths = [p for p in tree if p.startswith("src/")
                 or p in {"build.gradle", "settings.gradle", "gradle.properties"}]
        self._load(tree[p][1] for p in paths)
        return {p: digest(self.read(source, p)) for p in paths}

    def delta(self, before, after):
        self.commit(before)
        self.commit(after)
        return self.git("diff", "--name-only", "-z", before, after).decode().strip("\0").split("\0") if before != after else []


def ledger_scalars(data):
    """Read only the flat id/stage/status scalar lines of this repository's task schema.

    Full task definitions come from the committed JSON companion. This deliberately
    rejects a changed scalar representation instead of pretending to parse general YAML.
    The complete YAML byte hash is checked independently before these fields are used.
    """
    text = data.decode()
    require(re.search(r"(?m)^schema: ungravity\.master\.tasks\.v8$", text), "unknown ledger schema")
    rows = []
    current = None
    for line in text.splitlines():
        if line.startswith("- id:"):
            require(re.fullmatch(r"- id: S\d{2}-T\d{2}", line), "unsupported task id scalar")
            current = {"id": line[6:]}
            rows.append(current)
        elif current is not None:
            for field in ("stage", "status"):
                if line.startswith("  " + field + ":"):
                    match = re.fullmatch(r"  " + field + r": ([A-Za-z0-9_]+)", line)
                    require(match is not None and field not in current, "unsupported or duplicate ledger " + field)
                    current[field] = match.group(1)
    require(all(set(r) == {"id", "stage", "status"} for r in rows), "incomplete flat task identity/status")
    return rows


IMPLEMENTATIONS = {"present", "partial", "not_located_in_snapshot", "design_only", "metadata_or_process"}
VERIFICATIONS = {"recorded_atomic_acceptance", "current_scoped_regression", "partial_recorded_evidence",
                 "validation_missing", "specific_evidence_not_located", "known_unresolved_regression"}
STATUSES = {"completed", "in_progress", "pending_reverification"}


def verify_inventory(matrix, repo):
    require(matrix.get("schema") == "ungravity.task-evidence-audit.v1", "unknown audit schema")
    require(matrix.get("release_accepted") is False, "task inventory cannot authorize a release")
    source = repo.commit(matrix.get("source_snapshot"))
    ledger = repo.read(source, matrix["ledger_path"])
    require(digest(ledger) == matrix.get("ledger_sha256"), "pinned ledger SHA-256 differs")
    pinned = ledger_scalars(ledger)
    definitions = json.loads(repo.read(source, matrix["task_definition_path"]))
    expected = definitions.get("tasks", [])
    require(definitions.get("count") == 99 and len(expected) == 99 and len(pinned) == 99,
            "expected exactly 99 committed task definitions/ledger rows")
    expected_by_id = {t["id"]: t for t in expected}
    pinned_by_id = {t["id"]: t for t in pinned}
    require(len(expected_by_id) == 99 and len(pinned_by_id) == 99, "duplicate committed task identity")
    tasks = matrix.get("tasks", [])
    require(len(tasks) == 99, "audit must contain exactly 99 tasks")
    ids = [t.get("id") for t in tasks]
    require(len(set(ids)) == 99 and set(ids) == set(expected_by_id) == set(pinned_by_id),
            "duplicate/missing/unrecognized audit task identity")
    owners = matrix.get("owners", {})
    overrides = matrix.get("owner_overrides", {})
    require(set(owners) == {"S%02d" % n for n in range(18)}, "all 18 stages need execution owners")
    require(all(isinstance(v, str) and bool(v.strip()) for v in [*owners.values(), *overrides.values()]),
            "execution owners cannot be empty")
    require(set(overrides) <= set(expected_by_id), "owner override names an unknown task")
    atomic_bytes = repo.read(source, matrix["atomic_index_path"])
    require(digest(atomic_bytes) == matrix.get("atomic_index_sha256"), "pinned atomic index SHA-256 differs")
    atomic_index = json.loads(atomic_bytes)
    anchor_count = 0
    for task in tasks:
        ident = task["id"]
        definition = expected_by_id[ident]
        actual = pinned_by_id[ident]
        for field in ("stage", "stage_title", "title", "gate"):
            require(task.get(field) == definition[field], ident + ": definition differs: " + field)
        require(task.get("stage") == actual["stage"], ident + ": ledger stage differs")
        require(task.get("ledger_status") in STATUSES and task["ledger_status"] == actual["status"],
                ident + ": ledger status differs from pinned commit")
        require(task.get("execution_owner") == overrides.get(ident, owners[task["stage"]]),
                ident + ": execution owner differs")
        require(task.get("implementation_assessment") in IMPLEMENTATIONS, ident + ": invalid implementation assessment")
        require(task.get("verification_assessment") in VERIFICATIONS, ident + ": invalid verification assessment")
        require(task.get("task_acceptance_changed") is False and bool(task.get("remaining_acceptance")),
                ident + ": audit must retain a scoped remaining-acceptance statement")
        if task["verification_assessment"] == "recorded_atomic_acceptance":
            require(task["ledger_status"] == "completed" and isinstance(task.get("recorded_atomic"), dict),
                    ident + ": recorded atomic acceptance needs existing completed ledger status")
            recorded = task["recorded_atomic"]
            entry = atomic_index.get(recorded.get("index_key"), {})
            sources = {entry.get("source")} | {r.get("source") for r in entry.get("runs", [])}
            require(sha(recorded.get("source"), 40) and recorded["source"] in sources,
                    ident + ": recorded atomic source differs from pinned index")
            repo.commit(recorded["source"])
        for category in ("source_anchors", "test_anchors", "report_anchors"):
            for anchor in task.get(category, []):
                require(sha(anchor.get("sha256")), ident + ": invalid anchor digest")
                require(digest(repo.read(source, anchor["path"])) == anchor["sha256"],
                        ident + ": anchor bytes differ: " + anchor["path"])
                anchor_count += 1
    summary = dict(tasks=99,
                   ledger_status=dict(Counter(t["ledger_status"] for t in tasks)),
                   implementation=dict(Counter(t["implementation_assessment"] for t in tasks)),
                   verification=dict(Counter(t["verification_assessment"] for t in tasks)))
    require(matrix.get("summary") == summary, "audit summary differs from its actual task rows")
    return dict(source=source, **summary, validated_anchors=anchor_count,
                ledger_transitions_performed=False, task_acceptance_inferred=False)


def verified_archive(path, expected_hash):
    require(sha(expected_hash), "invalid evidence archive digest")
    require(path.is_file(), "missing evidence archive: " + str(path))
    data = path.read_bytes()
    require(digest(data) == expected_hash, "evidence archive SHA-256 differs")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "duplicate ZIP member")
        for name in names:
            relative_path(name)
        require(archive.testzip() is None, "evidence archive CRC mismatch")
        require("MANIFEST.json" in names, "evidence archive manifest missing")
        manifest = json.loads(archive.read("MANIFEST.json"))
        files = manifest.get("files")
        require(isinstance(files, dict) and bool(files), "empty or invalid archive manifest")
        require(set(names) == set(files) | {"MANIFEST.json"}, "unmanifested or missing archive member")
        payload = {}
        for name, expected in files.items():
            require(sha(expected), "invalid member digest: " + name)
            value = archive.read(name)
            require(digest(value) == expected, "archive member digest differs: " + name)
            payload[name] = value
    return manifest, payload


def result_cases(data, label):
    try:
        root = ET.fromstring(data)
    except ET.ParseError as exc:
        raise AuditError(label + ": malformed XML") from exc
    require(not list(root.iter("failure")) and not list(root.iter("error"))
            and not list(root.iter("skipped")), label + ": failed/error/skipped test case")
    cases = list(root.iter("testcase"))
    require(bool(cases), label + ": no actual test cases")
    identities = [(c.get("classname"), c.get("name")) for c in cases]
    require(all(name for _, name in identities) and len(set(identities)) == len(cases),
            label + ": duplicate or unnamed test cases")
    for suite in root.iter("testsuite"):
        for key in ("failures", "errors", "skipped"):
            require(int(suite.get(key, "0")) == 0, label + ": nonzero suite " + key)
        if "tests" in suite.attrib:
            require(int(suite.get("tests")) == len(list(suite.iter("testcase"))),
                    label + ": declared/actual case count differs")
    return identities


def metadata(jar, label):
    with zipfile.ZipFile(io.BytesIO(jar)) as archive:
        require(archive.testzip() is None, label + ": JAR CRC failure")
        require(archive.namelist().count("fabric.mod.json") == 1, label + ": missing/duplicate Fabric metadata")
        return json.loads(archive.read("fabric.mod.json"))


SCENES = ["connected-off", "laser-fire", "rocket-fire", "fire-off-reversed", "bearing-detached"]


def png_dimensions(image, label):
    require(image[:8] == b"\x89PNG\r\n\x1a\n", "invalid PNG signature: " + label)
    offset = 8
    width = height = None
    idat = []
    ended = False
    while offset < len(image):
        require(offset + 12 <= len(image), "truncated PNG chunk: " + label)
        size = struct.unpack(">I", image[offset:offset + 4])[0]
        kind = image[offset + 4:offset + 8]
        require(offset + size + 12 <= len(image), "truncated PNG payload: " + label)
        value = image[offset + 8:offset + 8 + size]
        expected = struct.unpack(">I", image[offset + 8 + size:offset + 12 + size])[0]
        require(zlib.crc32(kind + value) & 0xffffffff == expected, "PNG chunk CRC differs: " + label)
        if offset == 8:
            require(kind == b"IHDR" and size == 13, "PNG must begin with IHDR: " + label)
            width, height = struct.unpack(">II", value[:8])
            require(width > 0 and height > 0, "empty PNG dimensions: " + label)
            depth, colour, compression, filtering, interlace = value[8:]
            require(depth == 8 and colour in (2, 6) and (compression, filtering, interlace) == (0, 0, 0),
                    "native PNG must be noninterlaced 8-bit RGB/RGBA: " + label)
            stride = 1 + width * (3 if colour == 2 else 4)
            expected_size = stride * height
            require(expected_size <= 64 * 1024 * 1024, "native PNG exceeds decoded frame budget: " + label)
        else:
            require(kind != b"IHDR", "duplicate PNG IHDR: " + label)
        if kind == b"IDAT":
            idat.append(value)
        offset += size + 12
        if kind == b"IEND":
            require(size == 0 and offset == len(image), "invalid PNG end/trailing bytes: " + label)
            ended = True
            break
    require(ended and idat, "PNG image data/end missing: " + label)
    try:
        decoder = zlib.decompressobj()
        pixels = decoder.decompress(b"".join(idat), expected_size + 1)
    except zlib.error as exc:
        raise AuditError("PNG image data cannot decompress: " + label) from exc
    require(decoder.eof and not decoder.unused_data and not decoder.unconsumed_tail
            and len(pixels) == expected_size, "PNG decoded row size differs: " + label)
    require(all(pixels[row * stride] <= 4 for row in range(height)), "PNG row filter is invalid: " + label)
    return width, height


def verify_native(marker, files, prefix, review):
    for field in ("actualUseAndSneakKeys", "ordinaryPhysicalGunOwner", "preservedSpinAfterRelease"):
        require(marker.get(field) is True, "native assertion missing: " + field)
    for field in ("fixtureSuppliedJointOrShots", "operatorFollowTeleport"):
        require(marker.get(field) is False, "native fixture bypass: " + field)
    require(marker.get("frames") == SCENES, "native scene order/coverage differs")
    for field, minimum in (("lasers", 3), ("rockets", 1), ("stoppedTicks", 16),
                           ("projectileDrawCalls", 1), ("projectedShotPixels", 3)):
        value = marker.get(field)
        require(isinstance(value, (int, float)) and not isinstance(value, bool)
                and math.isfinite(value) and value >= minimum, "native count too small: " + field)
    for field, maximum in (("maxMuzzleError", 1e-8), ("maxLaunchVelocityError", 1e-8), ("maxAnchorError", .03)):
        value = marker.get(field)
        require(isinstance(value, (int, float)) and not isinstance(value, bool)
                and math.isfinite(value) and 0 <= value < maximum, "native bound failed: " + field)
    require(review.get("inspectedAllFive") is True and set(review.get("files", {})) == set(SCENES),
            "recorded five-frame manual review missing")
    for scene in SCENES:
        name = prefix + scene + ".png"
        require(name in files, "missing native frame: " + scene)
        image = files[name]
        png_dimensions(image, scene)
        require(digest(image) == review["files"][scene], "recorded review/frame hash differs: " + scene)
    return dict(scenes=SCENES, marker_assertions_valid=True,
                recorded_review_hashes_valid=True, new_visual_review_performed=False)


def verify_bundle(proof, repo, evidence_root):
    full = repo.commit(proof["fullServerSource"])
    native = repo.commit(proof["nativeSource"])
    delta = [p for p in repo.delta(full, native) if p]
    require(all(p.startswith("src/cubiccheck/") for p in delta),
            "native source changed more than the dedicated observer: " + ", ".join(delta))
    relative = relative_path(proof["archive"])
    evidence_root = Path(evidence_root).resolve()
    path = (evidence_root / relative).resolve()
    require(path.is_relative_to(evidence_root), "evidence path escapes supplied root")
    manifest, files = verified_archive(path, proof["archiveSha256"])
    require(manifest.get("fullServerSource") == full and manifest.get("nativeSource") == native,
            "archive source identities differ from the pinned proof")
    def read(name):
        require(name in files, "required evidence member missing: " + name)
        return files[name]
    game = read(proof["game_jar"])
    observer = read(proof["observer_jar"])
    require(digest(game) == proof["gameSha256"] and digest(observer) == proof["observerSha256"],
            "game or observer artifact digest differs")
    game_mod = metadata(game, "game")
    observer_mod = metadata(observer, "observer")
    require(game_mod.get("id") == proof["expected_mod_id"] and game_mod.get("version") == proof["expected_version"],
            "game Fabric id/version differs")
    require(observer_mod.get("id") == "drmd_cubiccheck", "observer is not the separate cubiccheck mod")
    base = "work/mounted-mechanisms/"
    input_counts = {}
    for member, source in ((base + "current-build.json", full), (base + "native-final-build.json", native)):
        stamp = json.loads(read(member))
        committed_inputs = repo.source_inputs(source)
        require(stamp.get("inputs") == committed_inputs, member + ": built inputs differ from committed source")
        input_counts[source] = len(committed_inputs)
        require(stamp.get("jars", {}).get(proof["game_jar"]) == proof["gameSha256"],
                member + ": full/native game bytes differ")
        if source == native:
            require(stamp["jars"].get(proof["observer_jar"]) == proof["observerSha256"],
                    "native observer bytes differ from build stamp")
    # The original collector omitted .sha256 suffixes. The already-verified full build
    # stamp is the primary receipt; validate the redundant text receipt if supplied.
    text_receipt = base + "current-game.sha256"
    if text_receipt in files:
        require(files[text_receipt].decode().split() == [proof["gameSha256"], proof["game_jar"]],
                "redundant full-suite artifact receipt differs")
    inputs = json.loads(read(str(PurePosixPath(proof["native_marker"]).parent / "inputs.json")))
    require(inputs.get("sourceSha") == native and inputs.get("uncommittedDiff") == "",
            "native launch source is dirty or differs")
    for name, expected in ((proof["game_jar"], proof["gameSha256"]), (proof["observer_jar"], proof["observerSha256"])):
        records = [a for a in inputs.get("artifacts", []) if a.get("source") == name]
        require(len(records) == 1 and records[0].get("sha256") == expected
                and records[0].get("path") == "mods/" + PurePosixPath(name).name,
                "native classpath artifact receipt differs: " + name)
    unit = []
    for name in sorted(files):
        if name.startswith(proof["unit_xml_prefix"]) and name.endswith(".xml"):
            unit.extend(result_cases(files[name], name))
    require(len(unit) == proof["expected_unit_tests"] and len(set(unit)) == len(unit),
            "unit XML actual case coverage differs or is duplicated")
    game_cases = result_cases(read(proof["game_test_xml"]), "complete GameTests")
    require(len(game_cases) == proof["expected_game_tests"], "GameTest XML actual case coverage differs")
    full_manifest = json.loads(read(base + "current-full-manifest.json"))
    source_manifest = json.loads(repo.read(full, "src/gametest/resources/fabric.mod.json"))["entrypoints"]["fabric-gametest"]
    require(full_manifest.get("source") == full_manifest.get("built") == source_manifest
            and full_manifest.get("equal") is True and full_manifest.get("tests") == len(game_cases),
            "complete built GameTest manifest differs from committed source")
    marker = json.loads(read(proof["native_marker"]))
    native_result = verify_native(marker, files, proof["native_frames_prefix"], json.loads(read(proof["frame_review"])))
    return dict(archive=relative, archive_sha256=proof["archiveSha256"], payload_files=len(files),
                zip_members=len(files) + 1, full_server_source=full, native_source=native,
                game_sha256=proof["gameSha256"], observer_sha256=proof["observerSha256"],
                fabric_id=game_mod["id"], fabric_version=game_mod["version"],
                full_artifact_receipt=base + "current-build.json", redundant_text_receipt_present=text_receipt in files,
                unit_cases=len(unit), server_cases=len(game_cases), game_test_entrypoints=len(source_manifest),
                committed_build_input_counts=input_counts,
                observer_only_delta=delta, native=native_result,
                gameplay_tests_rerun=False, performance_accepted=False, release_accepted=False)


def audit(matrix, repo, evidence_root):
    inventory = verify_inventory(matrix, repo)
    bundle = verify_bundle(matrix["current_core_bundle"], repo, evidence_root)
    delta = [p for p in repo.delta(bundle["native_source"], inventory["source"]) if p]
    require(all(p.startswith("docs/") for p in delta),
            "audited snapshot differs from recorded runtime outside documentation: " + ", ".join(delta))
    bundle["snapshot_metadata_only_delta"] = delta
    return dict(schema="ungravity.task-evidence-audit-result.v1",
                verified_at=datetime.now(timezone.utc).isoformat(), inventory=inventory, recorded_bundle=bundle,
                scope="Independent local metadata/hash/source consistency audit only; no new gameplay or visual run, task transitions or release acceptance.")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path("."))
    parser.add_argument("--matrix", type=Path, default=Path("docs/v8/TASK_EVIDENCE_AUDIT.json"))
    parser.add_argument("--evidence-root", type=Path, help="Checkout holding the exact archived evidence; defaults to repo")
    parser.add_argument("--out", type=Path, default=Path("work/audit/task-evidence-result.json"))
    args = parser.parse_args(argv)
    try:
        matrix = json.loads(args.matrix.read_text())
        result = audit(matrix, Repository(args.repo), args.evidence_root or args.repo)
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(result, indent=2) + "\n")
    except (AuditError, OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile) as exc:
        print("AUDIT FAIL: " + str(exc), file=sys.stderr)
        return 1
    print("Verified %d task rows and %d payload hashes (%d ZIP members); recorded %d unit/%d GameTest cases and %d native scenes. No tasks promoted or gameplay rerun."
          % (result["inventory"]["tasks"], result["recorded_bundle"]["payload_files"], result["recorded_bundle"]["zip_members"],
             result["recorded_bundle"]["unit_cases"], result["recorded_bundle"]["server_cases"], len(result["recorded_bundle"]["native"]["scenes"])))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

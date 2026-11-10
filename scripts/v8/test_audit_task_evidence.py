"""Negative provenance fixtures only: these never run or certify Minecraft gameplay."""
import base64
import copy
import hashlib
import io
import json
from pathlib import Path
import subprocess
import struct
import tempfile
import unittest
import warnings
import zipfile
import zlib

import audit_task_evidence as check


PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAMklEQVR4nGNgGFTgYcXr/w8rXv+nyABsbPq5AAbc3PxGDcFiCMUBDHPJwBtClXQyuAAAqE8w0Xs6L/UAAAAASUVORK5CYII=")


def encoded(value):
    return json.dumps(value, sort_keys=True).encode()


def jar(mod_id, version):
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("fabric.mod.json", encoded(dict(schemaVersion=1, id=mod_id, version=version)))
    return out.getvalue()


class AuditFixture(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.git_root = self.root / "repository"
        self.git_root.mkdir()
        self.git("init", "--quiet")
        self.definitions = []
        ledger = ["schema: ungravity.master.tasks.v8", "count: 99", "tasks:"]
        sizes = [6, 6, 6, 5, 6, 6, 6, 5, 5, 6, 5, 6, 5, 6, 5, 5, 5, 5]
        for number, count in enumerate(sizes):
            stage = "S%02d" % number
            for i in range(1, count + 1):
                ident = stage + "-T%02d" % i
                self.definitions.append(dict(id=ident, stage=stage, stage_title="Fixture " + stage,
                                             title="Fixture " + ident, gate="G0"))
                ledger += ["- id: " + ident, "  stage: " + stage, "  status: pending_reverification"]
        self.ledger = ("\n".join(ledger) + "\n").encode()
        self.write("docs/v8/TASKS.yaml", self.ledger)
        self.write("docs/v8/reference/TASKS.json", encoded(dict(count=99, tasks=self.definitions)))
        self.atomic_index = encoded({"fixture_record": {"source": None}})
        self.write("docs/v8/EVIDENCE/index.json", self.atomic_index)
        self.write("src/main/java/Game.java", b"fixture main code")
        self.write("src/main/resources/tiny.png", PNG)
        self.write("src/cubiccheck/java/Observer.java", b"fixture observer v1")
        self.entrypoints = ["FixtureFirst", "FixtureSecond"]
        self.write("src/gametest/resources/fabric.mod.json", encoded(dict(entrypoints={"fabric-gametest": self.entrypoints})))
        for p in ("build.gradle", "settings.gradle", "gradle.properties"):
            self.write(p, b"fixture build input")
        self.full = self.commit("full fixture")
        self.write("src/cubiccheck/java/Observer.java", b"fixture observer v2")
        self.native = self.commit("observer-only fixture")
        self.repo = check.Repository(self.git_root)
        anchor = dict(path="src/main/java/Game.java", sha256=check.digest(b"fixture main code"))
        tasks = []
        for definition in self.definitions:
            tasks.append(dict(**definition, ledger_status="pending_reverification", execution_owner="fixture_owner",
                              implementation_assessment="partial", verification_assessment="validation_missing",
                              remaining_acceptance="Fixture is metadata only, never a gameplay run.", task_acceptance_changed=False,
                              source_anchors=[copy.deepcopy(anchor)], test_anchors=[], report_anchors=[]))
        self.matrix = dict(schema="ungravity.task-evidence-audit.v1", source_snapshot=self.native,
                           ledger_path="docs/v8/TASKS.yaml", ledger_sha256=check.digest(self.ledger),
                           task_definition_path="docs/v8/reference/TASKS.json", release_accepted=False,
                           atomic_index_path="docs/v8/EVIDENCE/index.json", atomic_index_sha256=check.digest(self.atomic_index),
                           owners={"S%02d" % n: "fixture_owner" for n in range(18)}, owner_overrides={}, tasks=tasks,
                           summary=dict(tasks=99, ledger_status={"pending_reverification": 99},
                                        implementation={"partial": 99}, verification={"validation_missing": 99}))
        self.proof = dict(fullServerSource=self.full, nativeSource=self.native, archive="evidence/core.zip",
                          game_jar="build/libs/game.jar", observer_jar="build/libs/observer.jar",
                          expected_mod_id="drmd", expected_version="fixture-version", expected_unit_tests=2, expected_game_tests=2,
                          unit_xml_prefix="build/test-results/test/TEST-", game_test_xml="work/mounted-mechanisms/current-full.xml",
                          native_marker="runtime/mounted-mechanisms-ok.json", native_frames_prefix="runtime/frames/",
                          frame_review="work/mounted-mechanisms/native-frame-review.json")
        game, observer = jar("drmd", "fixture-version"), jar("drmd_cubiccheck", "0.0.1")
        self.proof.update(gameSha256=check.digest(game), observerSha256=check.digest(observer))
        self.files = {self.proof["game_jar"]: game, self.proof["observer_jar"]: observer}
        for path, source in (("current-build.json", self.full), ("native-final-build.json", self.native)):
            jars = {self.proof["game_jar"]: self.proof["gameSha256"], self.proof["observer_jar"]: self.proof["observerSha256"]}
            if source == self.full:
                jars[self.proof["observer_jar"]] = "f" * 64  # Historical full-run observer is legitimately different.
            self.put("work/mounted-mechanisms/" + path, dict(inputs=self.repo.source_inputs(source), jars=jars))
        self.put("runtime/inputs.json", dict(sourceSha=self.native, uncommittedDiff="", artifacts=[
            dict(source=self.proof["game_jar"], path="mods/game.jar", sha256=self.proof["gameSha256"]),
            dict(source=self.proof["observer_jar"], path="mods/observer.jar", sha256=self.proof["observerSha256"])]))
        self.files[self.proof["unit_xml_prefix"] + "Fixture.xml"] = (
            b'<testsuite tests="2" failures="0" errors="0" skipped="0">'
            b'<testcase classname="FixtureUnit" name="first"/><testcase classname="FixtureUnit" name="second"/></testsuite>')
        self.files[self.proof["game_test_xml"]] = (
            b'<testsuite><testsuite><testcase classname="empty" name="fixture.first"/>'
            b'<testcase classname="empty" name="fixture.second"/></testsuite></testsuite>')
        self.put("work/mounted-mechanisms/current-full-manifest.json",
                 dict(source=self.entrypoints, built=self.entrypoints, equal=True, tests=2))
        self.marker = dict(actualUseAndSneakKeys=True, ordinaryPhysicalGunOwner=True, preservedSpinAfterRelease=True,
                           fixtureSuppliedJointOrShots=False, operatorFollowTeleport=False, frames=check.SCENES,
                           lasers=3, rockets=1, stoppedTicks=16, projectileDrawCalls=5, projectedShotPixels=12,
                           maxMuzzleError=0, maxLaunchVelocityError=0, maxAnchorError=.001)
        self.put(self.proof["native_marker"], self.marker)
        self.review = dict(inspectedAllFive=True, files={n: check.digest(PNG) for n in check.SCENES})
        self.put(self.proof["frame_review"], self.review)
        for scene in check.SCENES:
            self.files[self.proof["native_frames_prefix"] + scene + ".png"] = PNG
        self.seal()

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.git_root), *args], stderr=subprocess.STDOUT)

    def write(self, path, value):
        p = self.git_root / path
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(value)

    def commit(self, message):
        self.git("add", ".")
        self.git("-c", "user.name=AuditFixture", "-c", "user.email=audit@example.invalid",
                 "-c", "commit.gpgsign=false", "commit", "--quiet", "-m", message)
        return self.git("rev-parse", "HEAD").decode().strip()

    def put(self, path, value):
        self.files[path] = encoded(value)

    def seal(self, extra=None, duplicate=None, bad_member_hash=None):
        p = self.root / self.proof["archive"]
        p.parent.mkdir(parents=True, exist_ok=True)
        hashes = {k: check.digest(v) for k, v in self.files.items()}
        if bad_member_hash is not None:
            hashes[bad_member_hash] = "0" * 64
        manifest = dict(nativeSource=self.native, fullServerSource=self.full, files=hashes)
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(p, "w", zipfile.ZIP_DEFLATED) as z:
                for name, value in self.files.items():
                    z.writestr(name, value)
                z.writestr("MANIFEST.json", encoded(manifest))
                for name, value in (extra or {}).items():
                    z.writestr(name, value)
                if duplicate:
                    z.writestr(duplicate, self.files[duplicate])
        self.proof["archiveSha256"] = check.digest(p.read_bytes())
        self.matrix["current_core_bundle"] = self.proof

    def verify(self):
        return check.audit(self.matrix, self.repo, self.root)

    def test_valid_scoped_bundle_is_not_a_new_game_run_or_release(self):
        result = self.verify()
        self.assertEqual(result["inventory"]["tasks"], 99)
        self.assertEqual(result["recorded_bundle"]["unit_cases"], 2)
        self.assertFalse(result["recorded_bundle"]["gameplay_tests_rerun"])
        self.assertFalse(result["recorded_bundle"]["release_accepted"])
        self.assertFalse(result["recorded_bundle"]["redundant_text_receipt_present"])

    def test_dirty_worktree_and_live_ledger_cannot_replace_pinned_objects(self):
        self.write("docs/v8/TASKS.yaml", b"live incomplete edits")
        self.write("src/main/java/Game.java", b"live gameplay edit")
        self.repo = check.Repository(self.git_root)
        self.assertEqual(self.repo.read(self.native, "src/main/resources/tiny.png"), PNG)
        self.assertEqual(self.verify()["inventory"]["source"], self.native)

    def test_duplicate_and_missing_task_cannot_claim_99_rows(self):
        self.matrix["tasks"][-1] = copy.deepcopy(self.matrix["tasks"][0])
        with self.assertRaisesRegex(check.AuditError, "task identity"):
            self.verify()

    def test_owner_coverage_and_empty_owner_are_rejected(self):
        self.matrix["tasks"][0]["execution_owner"] = "unassigned"
        with self.assertRaisesRegex(check.AuditError, "execution owner differs"):
            self.verify()
        self.matrix["tasks"][0]["execution_owner"] = "fixture_owner"
        self.matrix["owners"]["S00"] = ""
        with self.assertRaisesRegex(check.AuditError, "owners cannot be empty"):
            self.verify()

    def test_status_promotion_release_and_forged_summary_are_rejected(self):
        self.matrix["tasks"][0]["ledger_status"] = "completed"
        with self.assertRaisesRegex(check.AuditError, "ledger status differs"):
            self.verify()
        self.matrix["tasks"][0]["ledger_status"] = "pending_reverification"
        self.matrix["release_accepted"] = True
        with self.assertRaisesRegex(check.AuditError, "cannot authorize a release"):
            self.verify()
        self.matrix["release_accepted"] = False
        self.matrix["summary"]["tasks"] = 100
        with self.assertRaisesRegex(check.AuditError, "summary differs"):
            self.verify()

    def test_changed_anchor_and_ledger_hash_are_rejected(self):
        original = self.matrix["tasks"][0]["source_anchors"][0]["sha256"]
        self.matrix["tasks"][0]["source_anchors"][0]["sha256"] = "0" * 64
        with self.assertRaisesRegex(check.AuditError, "anchor bytes differ"):
            self.verify()
        self.matrix["tasks"][0]["source_anchors"][0]["sha256"] = original
        self.matrix["ledger_sha256"] = "0" * 64
        with self.assertRaisesRegex(check.AuditError, "ledger SHA-256 differs"):
            self.verify()
        self.matrix["ledger_sha256"] = check.digest(self.ledger)
        self.matrix["atomic_index_sha256"] = "0" * 64
        with self.assertRaisesRegex(check.AuditError, "atomic index SHA-256 differs"):
            self.verify()

    def test_atomic_record_requires_exact_committed_index_source(self):
        ledger = self.ledger.replace(b"status: pending_reverification", b"status: completed", 1)
        self.write("docs/v8/TASKS.yaml", ledger)
        self.matrix["source_snapshot"] = self.commit("recorded atomic fixture ledger")
        self.matrix["ledger_sha256"] = check.digest(ledger)
        task = self.matrix["tasks"][0]
        task.update(ledger_status="completed", verification_assessment="recorded_atomic_acceptance",
                    recorded_atomic=dict(index_key="fixture_record", source=self.full))
        with self.assertRaisesRegex(check.AuditError, "atomic source differs"):
            self.verify()
        self.atomic_index = encoded({"fixture_record": {"runs": [{"source": self.full}]}})
        self.write("docs/v8/EVIDENCE/index.json", self.atomic_index)
        self.matrix["source_snapshot"] = self.commit("recorded atomic indexed source")
        self.matrix["atomic_index_sha256"] = check.digest(self.atomic_index)
        self.matrix["summary"]["ledger_status"] = dict(pending_reverification=98, completed=1)
        self.matrix["summary"]["verification"] = dict(validation_missing=98, recorded_atomic_acceptance=1)
        self.assertEqual(self.verify()["inventory"]["tasks"], 99)

    def test_native_gameplay_delta_cannot_inherit_old_full_suite(self):
        self.write("src/main/java/Game.java", b"different gameplay")
        self.proof["nativeSource"] = self.commit("gameplay changed")
        with self.assertRaisesRegex(check.AuditError, "more than the dedicated observer"):
            self.verify()

    def test_audited_snapshot_cannot_borrow_prior_production_acceptance(self):
        self.write("src/main/java/UnanchoredNewFeature.java", b"changed production code after runtime")
        self.matrix["source_snapshot"] = self.commit("post-runtime untested feature")
        with self.assertRaisesRegex(check.AuditError, "differs from recorded runtime outside documentation"):
            self.verify()

    def test_archive_digest_and_manifest_member_digest_are_independent(self):
        self.proof["archiveSha256"] = "0" * 64
        with self.assertRaisesRegex(check.AuditError, "archive SHA-256 differs"):
            self.verify()
        self.seal(bad_member_hash=self.proof["game_jar"])
        with self.assertRaisesRegex(check.AuditError, "member digest differs"):
            self.verify()

    def test_unmanifested_duplicate_and_unsafe_members_are_rejected(self):
        self.seal(extra={"forgotten.txt": b"unreported"})
        with self.assertRaisesRegex(check.AuditError, "unmanifested"):
            self.verify()
        self.seal(duplicate=self.proof["game_jar"])
        with self.assertRaisesRegex(check.AuditError, "duplicate ZIP"):
            self.verify()
        self.seal(extra={"../escape": b"unreported"})
        with self.assertRaisesRegex(check.AuditError, "unsafe"):
            self.verify()

    def test_committed_input_and_artifact_receipts_cannot_disagree(self):
        p = "work/mounted-mechanisms/native-final-build.json"
        original = self.files[p]
        stamp = json.loads(original)
        stamp["inputs"]["src/main/java/Game.java"] = "0" * 64
        self.put(p, stamp)
        self.seal()
        with self.assertRaisesRegex(check.AuditError, "built inputs differ"):
            self.verify()
        self.files[p] = original
        self.files["work/mounted-mechanisms/current-game.sha256"] = b"wrong redundant receipt"
        self.seal()
        with self.assertRaisesRegex(check.AuditError, "redundant full-suite artifact receipt differs"):
            self.verify()

    def test_actual_xml_cases_cannot_be_replaced_by_declared_totals(self):
        p = self.proof["unit_xml_prefix"] + "Fixture.xml"
        original = self.files[p]
        self.files[p] = original.replace(b'tests="2"', b'tests="758"')
        self.seal()
        with self.assertRaisesRegex(check.AuditError, "declared/actual case count differs"):
            self.verify()
        self.files[p] = original
        self.files[self.proof["unit_xml_prefix"] + "Duplicate.xml"] = original
        self.seal()
        with self.assertRaisesRegex(check.AuditError, "case coverage differs or is duplicated"):
            self.verify()

    def test_failed_or_skipped_game_test_cannot_count_as_pass(self):
        p = self.proof["game_test_xml"]
        self.files[p] = self.files[p].replace(b'name="fixture.first"/>', b'name="fixture.first"><skipped/></testcase>')
        self.seal()
        with self.assertRaisesRegex(check.AuditError, "failed/error/skipped"):
            self.verify()

    def test_focused_manifest_or_stale_launch_source_is_rejected(self):
        p = "work/mounted-mechanisms/current-full-manifest.json"
        original = self.files[p]
        manifest = json.loads(original)
        manifest["built"] = self.entrypoints[:1]
        self.put(p, manifest)
        self.seal()
        with self.assertRaisesRegex(check.AuditError, "complete built GameTest manifest differs"):
            self.verify()
        self.files[p] = original
        launch = json.loads(self.files["runtime/inputs.json"])
        launch["sourceSha"] = self.full
        self.put("runtime/inputs.json", launch)
        self.seal()
        with self.assertRaisesRegex(check.AuditError, "native launch source"):
            self.verify()

    def test_launch_receipt_requires_mods_classpath_destination(self):
        launch = json.loads(self.files["runtime/inputs.json"])
        launch["artifacts"][0]["path"] = "unused/game.jar"
        self.put("runtime/inputs.json", launch)
        self.seal()
        with self.assertRaisesRegex(check.AuditError, "classpath artifact receipt differs"):
            self.verify()

    def test_native_shortcuts_nonfinite_bounds_and_missing_frame_fail(self):
        original = self.files[self.proof["native_marker"]]
        for field, value in [("actualUseAndSneakKeys", False), ("operatorFollowTeleport", True),
                             ("fixtureSuppliedJointOrShots", True), ("maxAnchorError", float("nan")),
                             ("projectedShotPixels", 0), ("stoppedTicks", 0)]:
            with self.subTest(field=field):
                marker = json.loads(original)
                marker[field] = value
                self.put(self.proof["native_marker"], marker)
                self.seal()
                with self.assertRaises(check.AuditError):
                    self.verify()
        self.files[self.proof["native_marker"]] = original
        del self.files[self.proof["native_frames_prefix"] + check.SCENES[-1] + ".png"]
        self.seal()
        with self.assertRaisesRegex(check.AuditError, "missing native frame"):
            self.verify()

    def test_png_placeholder_or_crc_corruption_is_not_a_visual_frame(self):
        name = self.proof["native_frames_prefix"] + check.SCENES[0] + ".png"
        for image in (b"\x89PNG\r\n\x1a\nfixture", PNG[:-1] + bytes([PNG[-1] ^ 1])):
            with self.subTest(size=len(image)):
                self.files[name] = image
                review = copy.deepcopy(self.review)
                review["files"][check.SCENES[0]] = check.digest(image)
                self.put(self.proof["frame_review"], review)
                self.seal()
                with self.assertRaisesRegex(check.AuditError, "PNG"):
                    self.verify()

    def test_png_valid_chunk_crc_cannot_hide_bad_compressed_rows(self):
        def chunk(kind, value):
            return struct.pack(">I", len(value)) + kind + value + struct.pack(">I", zlib.crc32(kind + value) & 0xffffffff)
        header = chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
        for compressed in (b"not zlib", zlib.compress(b"short"), zlib.compress((b"\x05" + b"\0" * 64) * 16)):
            image = b"\x89PNG\r\n\x1a\n" + header + chunk(b"IDAT", compressed) + chunk(b"IEND", b"")
            with self.subTest(compressed_size=len(compressed)):
                with self.assertRaisesRegex(check.AuditError, "PNG"):
                    check.png_dimensions(image, "invalid compressed fixture")


if __name__ == "__main__":
    unittest.main()

"""Synthetic provenance/parser controls; none of these results are game benchmarks."""
import copy
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import collect_benchmark_context as check


def jar(path, mod_id="drmd", version="fixture", schema=1, **extra):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("fabric.mod.json", json.dumps(dict(schemaVersion=schema, id=mod_id, version=version, **extra)))
        archive.writestr("fixture-payload.txt", "Not executable gameplay code")


class ContextFixture(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        self.git("init", "--quiet")
        for name in ("src/main/Game.java", "build.gradle", "settings.gradle", "gradle.properties"):
            path = self.repo / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("synthetic committed input")
        self.git("add", ".")
        self.git("-c", "user.name=BenchmarkFixture", "-c", "user.email=benchmark@example.invalid",
                 "-c", "commit.gpgsign=false", "commit", "--quiet", "-m", "fixture only")
        self.source = self.git("rev-parse", "HEAD").strip().decode()
        self.game = self.root / "game.jar"
        jar(self.game)
        self.mods = self.root / "mods"
        self.mods.mkdir()
        (self.mods / "game.jar").write_bytes(self.game.read_bytes())
        jar(self.mods / "dependency.jar", "fixture_dependency", "2.0", jars=[{"file": "META-INF/jars/nested.jar"}])
        (self.mods / "unused.jar.disabled").write_text("explicitly ignored, not a staged JAR")
        self.observation = self.root / "observations.json"
        self.data = dict(schema=check.OBSERVATION_SCHEMA, run_id="synthetic-only", scenario="parser-control",
                         context={}, samples={})
        self.write_observation()

    def git(self, *args):
        return subprocess.run(["git", "-C", str(self.repo), *args], capture_output=True, check=True).stdout

    def write_observation(self):
        self.observation.write_text(json.dumps(self.data))

    def observe(self, field, value):
        self.data["context"][field] = dict(value=value, reason=None)

    def sample(self, name, values, unit="ms"):
        self.data["window"] = dict(value=dict(started_at="2026-11-11T00:00:00Z",
                                              ended_at="2026-11-11T00:00:30+00:00", warmup_seconds=10), reason=None)
        self.data["samples"][name] = dict(values=values, unit=unit, reason=None)

    def collect(self, stamp=None):
        self.write_observation()
        return check.collect(self.repo, self.source, self.game, self.mods, self.observation, stamp)

    def stamp(self):
        path = self.root / "stamp.json"
        self.build = dict(inputs=check.Repository(self.repo).source_inputs(self.source),
                          jars={"build/libs/game.jar": hashlib.sha256(self.game.read_bytes()).hexdigest()})
        path.write_text(json.dumps(self.build))
        return path

    def test_missing_observations_stay_null_and_staged_is_not_loaded(self):
        result = self.collect()
        self.assertEqual(result["source"]["revision"], self.source)
        self.assertEqual(result["collector"]["script_sha256"], hashlib.sha256(Path(check.__file__).read_bytes()).hexdigest())
        self.assertEqual(result["artifacts"]["game"]["sha256"], hashlib.sha256(self.game.read_bytes()).hexdigest())
        self.assertEqual(len(result["artifacts"]["staged_outer_mods"]), 2)
        self.assertEqual(result["artifacts"]["ignored_nonjar_entries"], ["unused.jar.disabled"])
        self.assertIsNone(result["artifacts"]["loaded_runtime_mods"]["value"])
        self.assertIsNone(result["source"]["jar_association"]["value"])
        for name in ("seed", "coords", "gpu_renderer", "heap_max_bytes", "cubes_loaded", "draw_calls"):
            self.assertIsNone(result["context"][name]["value"])
            self.assertTrue(result["context"][name]["reason"])
        for name in ("server_tick_ms", "heap_used_bytes", "cubes_loaded", "draw_calls", "client_fps"):
            self.assertIsNone(result["metrics"][name]["value"])
        self.assertFalse(result["gameplay_launched"])
        self.assertFalse(result["performance_accepted"])
        self.assertFalse(result["release_accepted"])

    def test_actual_observation_file_hash_and_pointer_follow_seed_zero(self):
        self.observe("seed", 0)
        self.observe("coords", [-1, 2200.5, 0])
        self.observe("hybrid_on", False)
        result = self.collect()
        self.assertEqual(result["context"]["seed"]["value"], 0)
        self.assertFalse(result["context"]["hybrid_on"]["value"])
        reference = result["context"]["seed"]["evidence"]
        self.assertEqual(reference["sha256"], hashlib.sha256(self.observation.read_bytes()).hexdigest())
        self.assertEqual(reference["pointer"], "/context/seed/value")

    def test_percentiles_use_raw_samples_and_fps_reciprocals_in_correct_order(self):
        self.sample("server_tick_ms", [9, 1, 4, 2])
        self.sample("frame_time_ms", [10, 20, 100])
        result = self.collect()
        self.assertEqual(result["metrics"]["server_tick_ms"]["value"]["p50"], 2)
        self.assertEqual(result["metrics"]["server_tick_ms"]["value"]["p95"], 9)
        self.assertEqual(result["metrics"]["client_fps"]["value"]["p50"], 50)
        self.assertEqual(result["metrics"]["client_fps"]["value"]["p95"], 100)
        self.assertNotEqual(result["metrics"]["client_fps"]["value"]["p95"],
                            1000 / result["metrics"]["frame_time_ms"]["value"]["p95"])
        self.assertFalse(result["sample_coverage_in_window_verified"])
        self.assertFalse(result["performance_accepted"])

    def test_build_stamp_matches_both_committed_inputs_and_game_bytes(self):
        result = self.collect(self.stamp())
        self.assertEqual(result["source"]["jar_association"]["value"], "recorded_build_inputs_and_game_digest_match")
        self.assertFalse(result["performance_accepted"])

    def test_wrong_source_and_artifact_receipts_are_not_associations(self):
        path = self.stamp()
        original = copy.deepcopy(self.build)
        self.build["inputs"]["src/main/Game.java"] = "0" * 64
        path.write_text(json.dumps(self.build))
        with self.assertRaisesRegex(check.ContextError, "inputs differ"):
            self.collect(path)
        self.build = original
        self.build["jars"]["build/libs/game.jar"] = "0" * 64
        path.write_text(json.dumps(self.build))
        with self.assertRaisesRegex(check.ContextError, "game digest differs"):
            self.collect(path)

    def test_runtime_identity_cannot_borrow_unrelated_source_or_jar(self):
        for field, value in (("runtime_source_sha", "0" * 40), ("runtime_game_sha256", "0" * 64)):
            with self.subTest(field=field):
                self.data["context"] = {field: dict(value=value)}
                with self.assertRaisesRegex(check.ContextError, "differs from collected provenance"):
                    self.collect()

    def test_dirty_source_is_reported_without_relabeling_committed_receipt(self):
        stamp = self.stamp()
        (self.repo / "src/main/Game.java").write_text("dirty local gameplay change")
        (self.repo / "src/main/New.java").write_text("untracked local gameplay change")
        result = self.collect(stamp)
        self.assertEqual(result["source"]["tracked_input_changes_from_head"], ["src/main/Game.java"])
        self.assertEqual(result["source"]["untracked_input_paths"], ["src/main/New.java"])
        self.assertEqual(result["source"]["revision"], self.source)

    def test_mismatched_and_duplicate_staged_games_fail(self):
        jar(self.mods / "game.jar", version="other untested game")
        with self.assertRaisesRegex(check.ContextError, "staged game JAR differs"):
            self.collect()
        (self.mods / "game.jar").write_bytes(self.game.read_bytes())
        (self.mods / "second-game.jar").write_bytes(self.game.read_bytes())
        with self.assertRaisesRegex(check.ContextError, "duplicate staged outer mod ID"):
            self.collect()

    def test_symlink_and_nested_mods_are_unsupported(self):
        link = self.mods / "linked.jar"
        link.symlink_to(self.game)
        with self.assertRaisesRegex(check.ContextError, "symlink mods"):
            self.collect()
        link.unlink()
        (self.mods / "nested").mkdir()
        with self.assertRaisesRegex(check.ContextError, "nested/symlink mods"):
            self.collect()

    def test_invalid_or_unresolved_fabric_metadata_fails(self):
        for kwargs in ({"schema": True}, {"schema": 2}, {"version": "${version}"}):
            with self.subTest(kwargs=kwargs):
                jar(self.mods / "dependency.jar", "fixture_dependency", **kwargs)
                with self.assertRaises(check.ContextError):
                    self.collect()

    def test_null_needs_reason_and_present_value_cannot_be_unavailable(self):
        for value in ({"value": None}, {"value": 1, "reason": "not observed"}):
            self.data["context"] = {"seed": value}
            with self.assertRaises(check.ContextError):
                self.collect()
        self.data["context"] = {"java_version": dict(value="UNKNOWN")}
        with self.assertRaises(check.ContextError):
            self.collect()

    def test_seed_coordinate_and_counter_types_are_strict(self):
        for field, value in (("seed", True), ("seed", 2**63), ("seed", "123"),
                             ("coords", [0, 1]), ("coords", [0, float("inf"), 1]),
                             ("cubes_loaded", -1), ("draw_calls", 3.5)):
            with self.subTest(field=field, value=value):
                self.data["context"] = {field: dict(value=value)}
                with self.assertRaises(check.ContextError):
                    self.collect()

    def test_unknown_schema_fields_and_fake_aggregate_are_not_samples(self):
        for mutate in (lambda d: d.update(schema="unknown"), lambda d: d.update(extra=True),
                       lambda d: d["context"].update(guessed_fps=dict(value=60)),
                       lambda d: d["samples"].update(frame_time_ms=dict(p95=20, unit="ms"))):
            with self.subTest(mutate=mutate):
                self.data = dict(schema=check.OBSERVATION_SCHEMA, run_id="synthetic-only", scenario="control", context={}, samples={})
                mutate(self.data)
                with self.assertRaises(check.ContextError):
                    self.collect()

    def test_units_empty_arrays_zero_negative_bool_and_nan_fail(self):
        for values, unit in (([2], "ns"), ([], "ms"), ([0], "ms"), ([-1], "ms"),
                             ([True], "ms"), ([float("nan")], "ms"), ([1e-320], "ms")):
            with self.subTest(values=values, unit=unit):
                self.sample("frame_time_ms", values, unit)
                with self.assertRaises(check.ContextError):
                    self.collect()

    def test_samples_require_actual_window_and_timezone(self):
        self.sample("server_tick_ms", [1, 2, 3])
        del self.data["window"]
        with self.assertRaisesRegex(check.ContextError, "sampling window"):
            self.collect()
        self.sample("server_tick_ms", [1, 2, 3])
        self.data["window"]["value"]["started_at"] = "2026-11-11T00:00:00"
        with self.assertRaisesRegex(check.ContextError, "timezones"):
            self.collect()
        self.data["window"]["value"]["started_at"] = "2026-11-12T00:00:00Z"
        with self.assertRaisesRegex(check.ContextError, "positive duration"):
            self.collect()

    def test_heap_counter_and_software_hardware_claims_are_consistent(self):
        self.observe("heap_max_bytes", 10)
        self.sample("heap_used_bytes", [5, 11], "bytes")
        with self.assertRaisesRegex(check.ContextError, "exceeds reported JVM maximum"):
            self.collect()
        self.data["samples"] = {}
        self.observe("gpu_renderer", "llvmpipe (LLVM 15.0.6)")
        self.observe("gpu_hardware", "physical")
        with self.assertRaisesRegex(check.ContextError, "software renderer cannot attest physical"):
            self.collect()
        self.observe("gpu_hardware", "software")
        self.assertEqual(self.collect()["context"]["gpu_hardware"]["value"], "software")

    def test_changed_mods_during_collection_fail(self):
        original = check.artifact
        def replace_previous(path):
            result = original(path)
            if path == self.mods / "game.jar":
                (self.mods / "dependency.jar").write_bytes(b"changed after initial read")
            return result
        with patch.object(check, "artifact", side_effect=replace_previous):
            with self.assertRaisesRegex(check.ContextError, "mods changed during collection"):
                self.collect()

    def test_duplicate_json_keys_are_rejected(self):
        self.observation.write_text('{"schema":"ungravity.benchmark-observations.v1","run_id":"a","run_id":"b"}')
        with self.assertRaisesRegex(check.ContextError, "duplicate JSON key"):
            check.observations(self.observation)

    def test_cli_keeps_original_outputs_and_template_has_no_fake_values(self):
        path = self.root / "template.json"
        with patch("sys.stdout", new_callable=io.StringIO):
            self.assertEqual(check.main(["template", "--out", str(path)]), 0)
        original = path.read_bytes()
        with patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(check.main(["template", "--out", str(path)]), 1)
        self.assertEqual(path.read_bytes(), original)
        data = json.loads(original)
        self.assertTrue(all(v["value"] is None and v["reason"] for v in data["context"].values()))
        self.assertTrue(all(v["values"] is None and v["reason"] for v in data["samples"].values()))
        with self.assertRaisesRegex(check.ContextError, "missing actual run_id"):
            check.observations(path)

    def test_failed_cli_collection_leaves_no_result_that_could_look_accepted(self):
        self.data["schema"] = "unsupported"
        self.write_observation()
        out = self.root / "failed-result.json"
        with patch("sys.stderr", new_callable=io.StringIO) as error:
            status = check.main(["collect", "--repo", str(self.repo), "--source", self.source,
                                 "--game-jar", str(self.game), "--mods-dir", str(self.mods),
                                 "--observations", str(self.observation), "--out", str(out)])
        self.assertEqual(status, 1)
        self.assertIn("CONTEXT FAIL", error.getvalue())
        self.assertFalse(out.exists())


if __name__ == "__main__":
    unittest.main()

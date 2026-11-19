#!/usr/bin/env python3
"""Revalidate original compatibility controls without launching Java or rewriting upstream files."""
import argparse
import json
from pathlib import Path
from verify_cubic_backend_evidence import BCC_SHA, checked_archive, file_receipt, require, witnesses


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoints", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    gate, gate_receipt = checked_archive(args.checkpoints / "gate0-server/evidence.zip")
    native, native_receipt = checked_archive(args.checkpoints / "cubic-lighting-1.1.18/native-evidence.zip")
    final, final_receipt = checked_archive(args.checkpoints / "world-access-1.1.19/native-evidence.zip")
    with gate, native, final:
        loom = gate.read("loom-first-failure.log").decode()
        unsupported = "build/cubic-client-sodium-gl32-unsupported/"
        inputs = json.loads(native.read(unsupported + "inputs.json"))
        negative_log = native.read("work/gate0/client-production-native-sodium-gl32.log").decode()
        negative_mods = [x for x in inputs["artifacts"] if x["path"].startswith("mods/")]
        require(any(x["sha256"] == BCC_SHA for x in negative_mods), "negative upstream identity differs")
        sodium = next(x for x in negative_mods if "/sodium-" in x["path"])
        require(sodium["sha256"] == "3d43c14985a4deb19c654aad3393b454cf57e1ebcbba86c4ae6a98dfc60eca2d", "negative Sodium identity differs")
        cases = []
        for mode, uses_sodium in (("mirror-gl32", False), ("baseline-sodium", True)):
            prefix = "build/cubic-client-final-release-" + mode + "/"
            receipt = json.loads(final.read(prefix + "inputs.json"))
            mods = [x for x in receipt["artifacts"] if x["path"].startswith("mods/")]
            require(any("/sodium-" in x["path"] for x in mods) == uses_sodium, "Sodium A/B contamination")
            require(receipt["sourceSha"] == "0f76a0789fe8cb62ff98c3e08d6232f222d72cb8", "accepted source changed")
            game = file_receipt(args.checkpoints / "world-access-1.1.19/drmd-6dof-1.1.19-world.1-fabric-1.21.1.jar")
            require(game["sha256"] == next(x["sha256"] for x in mods if x["path"].endswith("/drmd-6dof-1.1.19-world.1.jar")), "accepted main game differs")
            require(final.read("work/world-access/final-release-" + mode + ".exit").strip() == b"0", "accepted runtime did not exit0")
            outcome = json.loads(final.read(prefix + ("cubic-client-ok.json" if uses_sodium else "native-mirror-ok.json")))
            if uses_sodium:
                require(len(outcome["checks"]) == 14, "baseline checks missing")
            else:
                require(outcome["frames"] == 14 and len(outcome["scenes"]) == 7, "mirror controls missing")
            log = final.read("work/world-access/final-release-" + mode + ".log").decode()
            cases.append({"mode": mode, "status": "HISTORICAL_SUBSET_PASS", "sourceSha": receipt["sourceSha"],
                          "game": game, "mods": mods, "requestedMesaProfile": receipt["glOverride"],
                          "actualGraphicsLog": [s for s in log.splitlines() if any(k in s for k in ("OpenGL Version:", "OpenGL Renderer:", "OpenGL Vendor:"))],
                          "outcome": outcome, "actualExit": 0,
                          "scope": "software rendering, not physical Intel certification; absent actualGraphicsLog means only staged profile is recorded"})
        result = {"status": "PASS", "scope": "S02-T06 limitations record, not current full runtime or hardware acceptance",
                  "archives": [gate_receipt, native_receipt, final_receipt],
                  "loomMismatch": witnesses(loom, [r"Fabric Loom: 1\.10\.5", r"built with a newer version of Loom \(1\.17\.21\)"]),
                  "sodiumStrict32": {"status": "BLOCKED", "mods": negative_mods,
                                     "witnesses": witnesses(negative_log, [r"OpenGL Renderer: llvmpipe", r"OpenGL Version: 3\.2", r"GLSL 3\.30 is not supported", r"sodium: Sodium 0\.8\.13"])},
                  "acceptedHistoricalCases": cases,
                  "physicalIntelHD4400": "UNTESTED for this backend/current game; Mesa profile override is not hardware emulation",
                  "unverified": ["Full current native lighting/cold parity", "upstream droppedWrites semantics and busy callback flush", "BetterDistantHorizons/standard DistantHorizons co-installation", "old vanilla-world conversion"]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({"status": "PASS", "receipt": file_receipt(args.output)}))


if __name__ == "__main__":
    main()

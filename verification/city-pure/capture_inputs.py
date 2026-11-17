#!/usr/bin/env python3
"""Read-only input capture. This helper never launches Java, Gradle or Minecraft."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
MODELS = ("CityBlueprint", "CityBatchJournal", "CityAuthoringJob")
MODULES = (
    ("org.junit.jupiter", "junit-jupiter-api", "5.11.3"),
    ("org.junit.jupiter", "junit-jupiter-engine", "5.11.3"),
    ("org.junit.platform", "junit-platform-commons", "1.11.3"),
    ("org.junit.platform", "junit-platform-engine", "1.11.3"),
    ("org.junit.platform", "junit-platform-launcher", "1.11.3"),
    ("org.opentest4j", "opentest4j", "1.3.0"),
    ("org.apiguardian", "apiguardian-api", "1.1.2"),
)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def capture():
    files = []
    for model in MODELS:
        files.append(ROOT / f"src/main/java/com/terminaldetector/drmd/world/fixture/city/{model}.java")
        files.append(ROOT / f"src/test/java/com/terminaldetector/drmd/world/fixture/city/{model}Test.java")
    files.extend(ROOT / name for name in (
        "verification/city-pure/build.gradle", "verification/city-pure/settings.gradle",
        "verification/city-pure/capture_inputs.py", "gradlew", "gradle/wrapper/gradle-wrapper.properties",
        "gradle/wrapper/gradle-wrapper.jar"))
    cache = Path(os.environ.get("GRADLE_USER_HOME", "/workspace/.gradle"))
    jars = []
    for group, module, version in MODULES:
        found = list((cache / "caches/modules-2/files-2.1" / group / module / version).glob("*/*.jar"))
        if len(found) != 1:
            raise RuntimeError(f"expected one cached {module}:{version}, found {len(found)}")
        jars.append(found[0])
    java_home = Path(os.environ.get("JAVA_HOME", "/workspace/jdk21-complete"))
    git = lambda *args: subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()
    return {
        "capturedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "scope": "26 pure city controls only; no Minecraft/runtime/production build proof",
        "source": git("rev-parse", "HEAD"), "branch": git("branch", "--show-current"),
        "status": git("status", "--porcelain", "--untracked-files=all"),
        "files": {str(p.relative_to(ROOT)): digest(p) for p in files},
        "cachedJUnit": {str(p): digest(p) for p in jars},
        "jdk": {str(java_home / p): digest(java_home / p) for p in ("release", "bin/java", "bin/javac")},
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = capture()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({"source": result["source"], "files": len(result["files"]),
                      "cachedJUnit": len(result["cachedJUnit"]), "output": str(args.output)}))

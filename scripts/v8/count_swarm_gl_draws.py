#!/usr/bin/env python3
"""Count actual OpenGL draw submissions between marked Minecraft render returns."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import statistics
import subprocess


DRAW = r"(?:glDraw(?:Arrays|Elements|RangeElements|TransformFeedback|Pixels)|glMulti(?:DrawArrays|DrawElements|ModeDraw))\w*"
MARKER = r"glDebugMessageInsert\w*"
CALL = re.compile(rf"^\s*(\d+)\s+({DRAW}|{MARKER})\(")


def collect(apitrace, trace, actor_count):
    marks = {f'"DRMD_SWARM_{name}={actor_count}"': name for name in ("BEGIN", "FRAME", "END")}
    frame_calls = []
    first_call = None
    last_call = None
    current_draws = None

    command = [apitrace, "dump", f"--grep=^({DRAW}|{MARKER})$", "--color=never", trace]
    with subprocess.Popen(command, stdout=subprocess.PIPE, text=True) as dump:
        for line in dump.stdout:
            call = CALL.match(line)
            if call is None:
                continue
            index, name = call.groups()
            if not name.startswith("glDebugMessageInsert"):
                if current_draws is not None:
                    current_draws += 1
                continue

            label = next((label for marker, label in marks.items() if marker in line), None)
            if label == "BEGIN":
                if first_call is not None:
                    raise ValueError("Duplicate begin marker")
                first_call = int(index)
                current_draws = 0
            elif label == "FRAME":
                if current_draws is None:
                    raise ValueError("Frame marker outside the sample")
                frame_calls.append(current_draws)
                current_draws = 0
            elif label == "END":
                if current_draws is None:
                    raise ValueError("End marker without an active sample")
                last_call = int(index)
                current_draws = None

        if dump.wait() != 0:
            raise ValueError("apitrace dump failed")

    if first_call is None or last_call is None or len(frame_calls) < 2 or min(frame_calls) <= 0:
        raise ValueError("Missing complete measured render intervals")
    with trace.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    return {
        "actors": actor_count,
        "traceSha256": digest,
        "beginCall": first_call,
        "endCall": last_call,
        "drawCallsPerFrame": frame_calls,
        "frameCount": len(frame_calls),
        "min": min(frame_calls),
        "median": statistics.median(frame_calls),
        "max": max(frame_calls),
        "measurement": "Native GL API draw submissions, one count per indirect or multidraw invocation",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apitrace", type=Path)
    parser.add_argument("trace", type=Path)
    parser.add_argument("count", type=int, choices=(32, 128, 256, 512, 1024))
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = collect(args.apitrace, args.trace, args.count)
    with args.output.open("x") as stream:
        json.dump(result, stream, indent=2)
        stream.write("\n")


if __name__ == "__main__":
    main()

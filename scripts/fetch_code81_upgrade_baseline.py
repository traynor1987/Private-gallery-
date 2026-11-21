#!/usr/bin/env python3
"""Fetch and verify the public production Build 94 APK for Build 95 upgrade testing."""

import argparse
from pathlib import Path
import subprocess
import tempfile

from verify_code80_apk_identity import inspect

BASELINE_URL = (
    "https://github.com/traynor1987/Private-gallery-/releases/download/v1.7.48/"
    "private-gallery-release.apk"
)
BASELINE_SIZE = 151_959_239
BASELINE_SHA256 = "165e49a35bafc1134b08116daf3fe62cfcbd7e85df4c433c50a4a0fe14351125"


def fetch(destination: Path, build_tools: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".code81-baseline-", dir=destination.parent) as directory:
        staged = Path(directory) / "baseline.apk"
        subprocess.run([
            "curl", "--disable", "--fail", "--silent", "--show-error", "--location",
            "--proto", "=https", "--proto-redir", "=https", "--max-time", "180",
            "--max-filesize", str(BASELINE_SIZE), "--output", str(staged), BASELINE_URL,
        ], check=True)
        if staged.stat().st_size != BASELINE_SIZE:
            raise ValueError("Build 94 APK length does not match the production receipt")
        if inspect(staged, build_tools.resolve(), 94, "1.7.48") != BASELINE_SHA256:
            raise ValueError("Build 94 APK SHA-256 does not match production run #52")
        subprocess.run([str(build_tools.resolve() / "zipalign"), "-c", "-P", "16", "4", str(staged)],
                       check=True)
        staged.chmod(0o600)
        staged.replace(destination)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("destination", type=Path)
    parser.add_argument("build_tools", type=Path)
    args = parser.parse_args()
    try:
        fetch(args.destination, args.build_tools)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Build 94 baseline verification failed: {error}\n")
    print(f"Verified production run #52 Build 94 baseline SHA-256: {BASELINE_SHA256}")


if __name__ == "__main__":
    main()

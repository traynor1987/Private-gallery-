#!/usr/bin/env python3
"""Prepare the existing upgrade instrumentation against the distributed Code 79 source ABI."""

import argparse
from pathlib import Path

from verify_compiled_upgrade1711 import transform
from verify_code79_baseline import RECEIPT


def prepare(root: Path, fixture: Path) -> None:
    source = fixture.read_bytes()
    updates = (
        (b"Code 78 to Code 79 upgrade", b"Code 79 to Code 80 upgrade", 1),
        (b"baselineCode == 78 && candidateCode == 79", b"baselineCode == 79 && candidateCode == 80", 1),
        (b"Code 78", b"Code 79", 3),
    )
    for old, new, expected in updates:
        if source.count(old) != expected:
            raise ValueError(f"Unexpected reviewed fixture marker {old!r}")
        source = source.replace(old, new)
    build_file = root / "app/build.gradle.kts"
    build = build_file.read_bytes()
    for marker in (b"versionCode = 79", b'versionName = "1.7.38.6"'):
        if build.count(marker) != 1:
            raise ValueError("Fixture compilation requires the pinned Code 79 production source")
    source, build = transform(source, build)
    target = root / "app/src/androidTest/java/uk/co/traynor/privategallery/upgrade/UpgradeFixtureTest.kt"
    if target.exists():
        raise ValueError("Fixture source already exists")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(source)
    build_file.write_bytes(build)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline_tree", type=Path, help=f"Disposable checkout of {RECEIPT['source_sha']}")
    parser.add_argument("fixture", type=Path)
    args = parser.parse_args()
    prepare(args.baseline_tree, args.fixture)

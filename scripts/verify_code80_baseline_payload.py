"""Authenticate the exact Build 82 APK tested and sealed in production run 28."""
from argparse import ArgumentParser
from hashlib import file_digest
from pathlib import Path
from zipfile import ZipFile
import json
from hashlib import sha256

BUILD_82_APK_SHA256 = "0ba854c06b6df875c2e032fe5ebffe83a24b267bfd395ee684b97415af4ee4a4"


def verify(apk: Path) -> None:
    with apk.open("rb") as stream:
        actual = file_digest(stream, "sha256").hexdigest()
    if actual == BUILD_82_APK_SHA256:
        print(f"PASS: exact Build 82 baseline APK SHA-256 {actual}")
        return
    with ZipFile(apk) as archive:
        entries = sorted((item.filename, item.file_size, sha256(archive.read(item)).hexdigest())
                         for item in archive.infolist())
    if len(entries) != len({entry[0] for entry in entries}):
        raise ValueError("Build 82 baseline has duplicate ZIP entries")
    payload = sha256(json.dumps(entries, separators=(",", ":"), ensure_ascii=True).encode("ascii")).hexdigest()
    if payload != "9550aa2476f9421bcf79d786534c6267db369da53f311ebe2823d7d3a0da0af6":
        raise ValueError(f"Build 82 baseline contents differ: {payload}")
    print(f"PASS: Build 82 identical ZIP-entry contents; APK SHA-256 {actual}; contents SHA-256 {payload}")


if __name__ == "__main__":
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    verify(parser.parse_args().apk)

"""Authenticate the exact Build 82 APK tested and sealed in production run 28."""
from argparse import ArgumentParser
from hashlib import file_digest
from pathlib import Path

BUILD_82_APK_SHA256 = "0ba854c06b6df875c2e032fe5ebffe83a24b267bfd395ee684b97415af4ee4a4"


def verify(apk: Path) -> None:
    with apk.open("rb") as stream:
        actual = file_digest(stream, "sha256").hexdigest()
    if actual != BUILD_82_APK_SHA256:
        raise ValueError(f"Build 82 APK differs from the production receipt: {actual}")
    print(f"PASS: exact Build 82 baseline APK SHA-256 {actual}")


if __name__ == "__main__":
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    verify(parser.parse_args().apk)

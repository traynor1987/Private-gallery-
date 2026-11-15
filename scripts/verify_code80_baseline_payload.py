"""Validate the reconstructed Code 80 APK against the delivered package contents."""

from argparse import ArgumentParser
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile
import json

DELIVERED_APK = "2833899890a99cc12dfb36e2bf4add2e2d9ddf92af77d517411ee37a9a46ef66"
DELIVERED_PAYLOAD = "d009a306b89557fa94c17f94feae5c17571c2390902ab7bc8319cf9b847ac314"


def verify(apk: Path) -> None:
    archive_hash = sha256(apk.read_bytes()).hexdigest()
    with ZipFile(apk) as archive:
        entries = sorted(
            (entry.filename, entry.file_size, sha256(archive.read(entry)).hexdigest())
            for entry in archive.infolist()
        )
    if len(entries) != len({entry[0] for entry in entries}):
        raise ValueError("Code 80 baseline contains duplicate APK entries")
    payload = json.dumps(entries, separators=(",", ":"), ensure_ascii=True).encode("ascii")
    payload_hash = sha256(payload).hexdigest()
    if payload_hash != DELIVERED_PAYLOAD:
        raise ValueError(f"Code 80 baseline payload differed: APK {archive_hash}, payload {payload_hash}")
    match = "identical APK" if archive_hash == DELIVERED_APK else "identical ZIP-entry contents"
    print(f"PASS: Code 80 {match}; rebuilt APK SHA-256 {archive_hash}; payload SHA-256 {payload_hash}")


if __name__ == "__main__":
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    verify(parser.parse_args().apk)

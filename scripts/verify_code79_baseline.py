#!/usr/bin/env python3
"""Verify the archived Code 79 baseline for Code 80 upgrade preparation."""

import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile

ARCHIVE_SHA256 = "59d70a3c024965bd87f25959fecb98b81129983e366a4ebebc597d1767e9d439"
APK_SHA256 = "3bc7821ebff906b336aa0d8b6d26f73ed0c5f44ad4ba154d0f7eb51058da93d0"
APK_NAME = "NovaOS-1.7.38.6-code79-owner-review.apk"
EVIDENCE_NAME = "NovaOS-1.7.38.6-code79-evidence.zip"
RECEIPT = {
    "source_sha": "06f85bb410580dc86622767d707f422ab5fe7ad9",
    "version": "1.7.38.6",
    "version_code": 79,
    "package": "uk.co.traynor.privategallery",
    "apk_sha256": APK_SHA256,
    "original_certificate_sha256": "94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17",
    "original_key_code78_upgrade": "PASS",
    "restart": "PASS",
}


def verify(path: Path) -> str:
    with path.open("rb") as archive:
        if hashlib.file_digest(archive, "sha256").hexdigest() != ARCHIVE_SHA256:
            raise ValueError("Code 79 archive digest differs from the original Actions artifact")
        with zipfile.ZipFile(archive) as outer:
            if sorted(outer.namelist()) != sorted((APK_NAME, EVIDENCE_NAME)):
                raise ValueError("Unexpected Code 79 archive members")
            with outer.open(APK_NAME) as apk:
                if hashlib.file_digest(apk, "sha256").hexdigest() != APK_SHA256:
                    raise ValueError("Code 79 APK digest differs from the release receipt")
            with zipfile.ZipFile(io.BytesIO(outer.read(EVIDENCE_NAME))) as evidence:
                if evidence.namelist().count("release-summary.json") != 1:
                    raise ValueError("Expected one Code 79 release receipt")
                receipt = json.loads(evidence.read("release-summary.json"))
                if any(receipt.get(key) != value for key, value in RECEIPT.items()):
                    raise ValueError("Code 79 release receipt differs from the pinned identity")
    return APK_SHA256


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    args = parser.parse_args()
    try:
        checksum = verify(args.archive)
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        parser.exit(1, f"{error}\n")
    print(f"Verified archived Code 79 APK SHA-256 {checksum}")

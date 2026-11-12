#!/usr/bin/env python3
"""Extract the exact distributed original-key Code 78 baseline for upgrade testing."""

import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = "4a0260327bb9f70289d4fbc9743b80305a43b025e518f68a7f41a652102e3808"
ARCHIVE_BYTES = 62398288
APK_NAME = "NovaOS-1.7.38.5-code78-owner-review.apk"
EVIDENCE_NAME = "NovaOS-1.7.38.5-code78-evidence.zip"
SOURCE = "c6ffbb769476b37b0c5bc4939e0f765eff3eef2a"
CERTIFICATE = "94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17"


def extract_baseline(archive: Path, destination: Path) -> None:
    if archive.stat().st_size != ARCHIVE_BYTES:
        raise ValueError("Code 78 distribution length differs")
    data = archive.read_bytes()
    if hashlib.sha256(data).hexdigest() != ARCHIVE_SHA256:
        raise ValueError("Code 78 distribution checksum differs")
    with zipfile.ZipFile(io.BytesIO(data)) as distributed:
        if sorted(distributed.namelist()) != sorted((APK_NAME, EVIDENCE_NAME)):
            raise ValueError("Code 78 distribution members differ")
        apk = distributed.read(APK_NAME)
        with zipfile.ZipFile(io.BytesIO(distributed.read(EVIDENCE_NAME))) as evidence:
            if evidence.namelist().count("release-summary.json") != 1:
                raise ValueError("Code 78 release summary missing or duplicated")
            summary = json.loads(evidence.read("release-summary.json"))
    expected = {
        "source_sha": SOURCE, "version": "1.7.38.5", "version_code": 78,
        "package": "uk.co.traynor.privategallery",
        "apk_sha256": hashlib.sha256(apk).hexdigest(),
        "original_certificate_sha256": CERTIFICATE,
        "original_key_code77_upgrade": "PASS", "restart": "PASS",
    }
    if any(summary.get(key) != value for key, value in expected.items()):
        raise ValueError("Code 78 release summary does not match the archived APK")
    with destination.open("xb") as output:
        output.write(apk)


if __name__ == "__main__":
    extract_baseline(Path(sys.argv[1]), Path(sys.argv[2]))
    print("PASS: extracted exact archived Code 78 baseline; verify APK signature separately")

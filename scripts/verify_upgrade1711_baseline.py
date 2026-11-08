#!/usr/bin/env python3
"""Extract pinned distributed 1.7.30 visual foundation Code 65; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = 'd2b6c0a22bfc5cb663ef79bec6e929ec494fb338b106d950468df3e1d2b8c3f9'
ARCHIVE_BYTES = 61355596
APK_SHA256 = 'fa0dafcf314d06a5e6c5cf02a162cff7e62660ab7805dea925cec8ed6794fb7e'
APK_BYTES = 146646744
ARTIFACT_ID = 13589877482
RUN_ID = 42410507182
JOB_ID = 128517442213
ACTIVATION_SHA = '00aea5defcde28b7afe2c8b6715c5a3029e5f246'
APK_NAME = 'NovaOS-1.7.30-visual-foundation-code65-owner-review.apk'
SOURCE_SHA = 'a02cfcf6bb3bbae15f00935c2b2124ae7eb4e674'
EVIDENCE_NAME = 'NovaOS-1.7.30-visual-foundation-code65-evidence.zip'
EVIDENCE_BYTES = 113839
EVIDENCE_SHA256 = 'eec6cb249dc9b044c19d516cf2513110b19537c030412acc3091d3dca0389b8a'
CERTIFICATE_SHA256 = '94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validated_apk(archive):
    archive = Path(archive)
    require(archive.stat().st_size == ARCHIVE_BYTES, 'Historical archive length differs')
    require(hashlib.sha256(archive.read_bytes()).hexdigest() == ARCHIVE_SHA256, 'Historical archive checksum differs')
    with zipfile.ZipFile(archive) as zipped:
        names = zipped.namelist()
        require(len(names) == len(set(names)), 'Duplicate archive members')
        require(set(names) == {APK_NAME, EVIDENCE_NAME}, 'Historical archive must contain exactly the original owner APK and evidence ZIP')
        require(zipped.getinfo(APK_NAME).file_size == APK_BYTES, 'Historical APK length differs')
        apk = zipped.read(APK_NAME)
        require(hashlib.sha256(apk).hexdigest() == APK_SHA256, 'Original signed baseline APK checksum differs')
        require(zipped.getinfo(EVIDENCE_NAME).file_size == EVIDENCE_BYTES, 'Historical evidence length differs')
        evidence = zipped.read(EVIDENCE_NAME)
        require(hashlib.sha256(evidence).hexdigest() == EVIDENCE_SHA256, 'Historical evidence checksum differs')
    with zipfile.ZipFile(io.BytesIO(evidence)) as zipped:
        names = zipped.namelist()
        require(len(names) == len(set(names)), 'Duplicate evidence members')
        require('release-summary.json' in names, 'Missing original release-summary receipt')
        summary = json.loads(zipped.read('release-summary.json'))
        expected = dict(source_sha=SOURCE_SHA, version='1.7.30-visual-foundation', version_code=65,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code64_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.30 visual foundation Code 65 APK and nested original release-summary provenance')

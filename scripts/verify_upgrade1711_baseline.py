#!/usr/bin/env python3
"""Extract pinned distributed 1.7.33 Code 68; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = 'e7c3dc077aad85f1943bf514a10812c100b6e5b57b3d9465f67bf2247dfec310'
ARCHIVE_BYTES = 61583832
APK_SHA256 = '29d3d6611db41aaeba6fa70e0f985b9973bccab94cafc04177024f97edabd426'
APK_BYTES = 147320784
ARTIFACT_ID = 13624529570
RUN_ID = 42499421968
JOB_ID = 128788701943
ACTIVATION_SHA = 'ccd41da195dea80557bda67f328d522a49a733a4'
APK_NAME = 'NovaOS-1.7.33-code68-owner-review.apk'
SOURCE_SHA = '0de573ccd4b5f2ef0ee557ca8c90ebf7d2aba1d3'
EVIDENCE_NAME = 'NovaOS-1.7.33-code68-evidence.zip'
EVIDENCE_BYTES = 104007
EVIDENCE_SHA256 = 'd6643a89afd0336563aa58c349fe1aa2c42d3702274778accb86e0a40f25c371'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.33', version_code=68,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code67_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.33 Code 68 APK and nested original release-summary provenance')

#!/usr/bin/env python3
"""Extract pinned distributed 1.7.11.2 code58; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = '5279a40b6a1ca1242af9647cf04d98e9ede3f497ce9991b70fe9f8e6986f5dce'
ARCHIVE_BYTES = 60614078
APK_SHA256 = 'd28e0de4a3aa6082922e20940f73e9e5f80d4ae9487dac0761338e6550abc59b'
APK_BYTES = 144697004
ARTIFACT_ID = 13481647850
RUN_ID = 42171366749
JOB_ID = 127739753358
ACTIVATION_SHA = 'a7666cf9b44d8f193f252921e62ee0c9b33b0ecd'
APK_NAME = 'NovaOS-1.7.11.2-home2-code58-owner-acceptance.apk'
SOURCE_SHA = 'ce743678cca903f41a376e5227eb381f9fbb9574'
EVIDENCE_NAME = 'NovaOS-1.7.11.2-home2-code58-evidence.zip'
EVIDENCE_BYTES = 103867
EVIDENCE_SHA256 = 'bb455929217f870a1630b8ea7469d8c1990010c31fada2a433d61ffe6c2026f1'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.11.2', version_code=58,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code57_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.11.2 code58 APK and nested original release-summary provenance')

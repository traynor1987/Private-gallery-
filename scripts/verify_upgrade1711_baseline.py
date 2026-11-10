#!/usr/bin/env python3
"""Extract pinned distributed 1.7.38.1 Code 74; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = '74263fde9ea16a4534ae3812416ccc806930ee75a04770fff4c6bba04f76fae3'
ARCHIVE_BYTES = 62050324
APK_SHA256 = '790da979638cb3fe2bfc7fa0e7c5864eba719ef1a7f97387b154098c876428c4'
APK_BYTES = 148599328
ARTIFACT_ID = 13703329743
RUN_ID = 42681892764
JOB_ID = 129378324094
ACTIVATION_SHA = '6e65172ee448eb2299c58c2d90f6e7178eb65f6b'
APK_NAME = 'NovaOS-1.7.38.1-code74-owner-review.apk'
SOURCE_SHA = '90e84e9e477a551c9f8985daca230bbf543059a0'
EVIDENCE_NAME = 'NovaOS-1.7.38.1-code74-evidence.zip'
EVIDENCE_BYTES = 104960
EVIDENCE_SHA256 = 'd0e6f3d03bf750f779eb1e5425aafd1da320e033c8af20b67c42ba7a4f207e84'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.38.1', version_code=74,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code73_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.38.1 Code 74 APK and nested original release-summary provenance')

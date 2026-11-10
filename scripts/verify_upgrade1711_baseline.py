#!/usr/bin/env python3
"""Extract pinned distributed 1.7.38.2 Code 75; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = 'de0e60171b37fe7376dd17aa8a97766e37419214486cf663e6dadcea4b341d85'
ARCHIVE_BYTES = 62111887
APK_SHA256 = '46ac04ff785c4c64a47b7e0c0456d3346598c36b9e093f33fa135ef4d5c70744'
APK_BYTES = 148746784
ARTIFACT_ID = 13724654625
RUN_ID = 42731111514
JOB_ID = 129549066650
ACTIVATION_SHA = '7ae6265fc458222abe00706f2489a9e1859e6f66'
APK_NAME = 'NovaOS-1.7.38.2-code75-owner-review.apk'
SOURCE_SHA = 'dc4f0b19be8391b899364fb62d890ad322350e2e'
EVIDENCE_NAME = 'NovaOS-1.7.38.2-code75-evidence.zip'
EVIDENCE_BYTES = 104948
EVIDENCE_SHA256 = 'baf9884ac96dbdbf8b5df899eb5f14b3d7dd568b04f21a3edfb5424d2c61f292'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.38.2', version_code=75,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code74_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.38.2 Code 75 APK and nested original release-summary provenance')

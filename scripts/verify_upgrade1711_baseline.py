#!/usr/bin/env python3
"""Extract pinned distributed 1.7.11.4 code60; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = '1a941bac0c92f845db5599afe16831659a0b0fea4042f3c7311bbd9978290c2a'
ARCHIVE_BYTES = 60716615
APK_SHA256 = 'd21b9134409f24bd460368b36aea715de82b6e00d98a303f0bdd9c514b3f1f82'
APK_BYTES = 144942764
ARTIFACT_ID = 13517195318
RUN_ID = 42238614535
JOB_ID = 127962769022
ACTIVATION_SHA = '9b21d17c180a319fe93e2d06f18b057b5c9f964c'
APK_NAME = 'NovaOS-1.7.11.4-home2-code60-owner-acceptance.apk'
SOURCE_SHA = 'f2727d75d142c2a733c53861e1a0f74440e0b6b5'
EVIDENCE_NAME = 'NovaOS-1.7.11.4-home2-code60-evidence.zip'
EVIDENCE_BYTES = 109868
EVIDENCE_SHA256 = 'a62bf205e5578e7c57168096ff6275e495cabc1cca56ab18d2b111bbba37d59d'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.11.4', version_code=60,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code59_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.11.4 code60 APK and nested original release-summary provenance')

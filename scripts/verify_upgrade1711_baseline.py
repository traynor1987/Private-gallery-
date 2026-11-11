#!/usr/bin/env python3
"""Extract pinned distributed 1.7.38.4 Code 77; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = 'fc04c9f424b739cb2c43150188c1d50dce74f29a584bb207637b27558fa881c2'
ARCHIVE_BYTES = 62374262
APK_SHA256 = 'dcfd93b5aec41c0bc4f08a61a3c6aa70a3e3f75a6d72948f0c4635c64109a03e'
APK_BYTES = 149533924
ARTIFACT_ID = 13838225338
RUN_ID = 42979582754
JOB_ID = 130386576879
ACTIVATION_SHA = '13b55757d180ea3eba18bbc07b249d0ddf6ea159'
APK_NAME = 'NovaOS-1.7.38.4-code77-owner-review.apk'
SOURCE_SHA = '15cdfd9e612ffc68960d1374420fe05e425a9877'
EVIDENCE_NAME = 'NovaOS-1.7.38.4-code77-evidence.zip'
EVIDENCE_BYTES = 106803
EVIDENCE_SHA256 = '375297c2ca560bcdfc61b2ea3e9cac6203130b42303bb90f688dea9313d729c6'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.38.4', version_code=77,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code76_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.38.4 Code 77 APK and nested original release-summary provenance')

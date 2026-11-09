#!/usr/bin/env python3
"""Extract pinned distributed 1.7.34 Code 69; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = 'ec5786e7bef4218e02a51da9685153aaf86eb598c0320b13202ef3a2266c4d02'
ARCHIVE_BYTES = 61636821
APK_SHA256 = '6783a1f1ec637691b0d2cc57f4b6e8cd29113c72ba40627b394246f464dd7cc3'
APK_BYTES = 147451856
ARTIFACT_ID = 13634007704
RUN_ID = 42521650907
JOB_ID = 128856241192
ACTIVATION_SHA = '40c2e4f80129e84a6f6fe48723fd453372b3cbd6'
APK_NAME = 'NovaOS-1.7.34-code69-owner-review.apk'
SOURCE_SHA = 'cc8aaed81b8d6ed27c71c9923edbf51334466cb2'
EVIDENCE_NAME = 'NovaOS-1.7.34-code69-evidence.zip'
EVIDENCE_BYTES = 105722
EVIDENCE_SHA256 = '248d368e5cd277446b990c9d5066d70f6db56c894c8346797d86518211b23ee1'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.34', version_code=69,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code68_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.34 Code 69 APK and nested original release-summary provenance')

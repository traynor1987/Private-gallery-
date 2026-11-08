#!/usr/bin/env python3
"""Extract pinned distributed 1.7.32 Code 67; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = 'd61b2765b0dd7791d3be85a65dacbe825bed2d76f969d964c740710248de0ea5'
ARCHIVE_BYTES = 61466888
APK_SHA256 = '525c82c2baf0b4375300695c5b4f0650ea72501cdc65fff070bb2064b574ec3a'
APK_BYTES = 146960336
ARTIFACT_ID = 13619529445
RUN_ID = 42487245254
JOB_ID = 128751688786
ACTIVATION_SHA = 'aef32a7d3db3c03649935eedbda3568609caa4e8'
APK_NAME = 'NovaOS-1.7.32-code67-owner-review.apk'
SOURCE_SHA = '5ee1a5458bb5d62daeae2369ee9dde05952a0faa'
EVIDENCE_NAME = 'NovaOS-1.7.32-code67-evidence.zip'
EVIDENCE_BYTES = 103811
EVIDENCE_SHA256 = 'b7f246ab8e1134afff7c3a587130816677b88bbc1de4a1f40aca9a1d609f2c0e'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.32', version_code=67,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code66_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.32 Code 67 APK and nested original release-summary provenance')

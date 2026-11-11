#!/usr/bin/env python3
"""Extract pinned distributed 1.7.38.3 Code 76; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = '7529b9dd3ef07db0b959e29d657c046bde0efc11b3b79a27f9be19ab3aa98757'
ARCHIVE_BYTES = 62192742
APK_SHA256 = '7fd1e4211e6277a8a8bfeaa20ef8ee180a89a57b38e435bb30b5a69fbcada171'
APK_BYTES = 148976200
ARTIFACT_ID = 13767667010
RUN_ID = 42819991907
JOB_ID = 129842766718
ACTIVATION_SHA = '03933aba35a8649753e9441039cfbc6563ee5c36'
APK_NAME = 'NovaOS-1.7.38.3-code76-owner-review.apk'
SOURCE_SHA = 'c818c670baa4b980708dc2effb1ab2ab0331ade1'
EVIDENCE_NAME = 'NovaOS-1.7.38.3-code76-evidence.zip'
EVIDENCE_BYTES = 105266
EVIDENCE_SHA256 = 'af94332116f608ae6651200eac2c3d6cee4cb0ce00eee17e4e1e4ad3747d9d28'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.38.3', version_code=76,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code75_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.38.3 Code 76 APK and nested original release-summary provenance')

#!/usr/bin/env python3
"""Extract pinned distributed 1.7.36 Code 71; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = '80e29546cf48e8363974c435c401cdcb5f7f3ea2ded20189942d93ac68ad1b58'
ARCHIVE_BYTES = 61893704
APK_SHA256 = '7de326f185f0d58562dc164f551fdf2cb3700e121af782b325b743c727a92405'
APK_BYTES = 148140572
ARTIFACT_ID = 13672198205
RUN_ID = 42614242525
JOB_ID = 129150037881
ACTIVATION_SHA = '042436ed4ecb0d5e868951a94f05d9e94ac82126'
APK_NAME = 'NovaOS-1.7.36-code71-owner-review.apk'
SOURCE_SHA = 'dd23090dd72682a9f9d0d22e7de39e1778fffa71'
EVIDENCE_NAME = 'NovaOS-1.7.36-code71-evidence.zip'
EVIDENCE_BYTES = 109417
EVIDENCE_SHA256 = 'd353abaa347cc0f4cddf9f21fe88858f1f371a7320c16601bc790763e5f311b3'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.36', version_code=71,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code69_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.36 Code 71 APK and nested original release-summary provenance')

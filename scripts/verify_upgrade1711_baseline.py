#!/usr/bin/env python3
"""Extract pinned distributed 1.7.38 Code 73; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = '343eb524b742c3b0088bbd7c2163a57c1f7c3b79f4e1fdddccf3115838bb1a28'
ARCHIVE_BYTES = 62004633
APK_SHA256 = '7364527f56b1b1de54cfe43328db6444a685fee15b015efeec6a288fd81fd4b8'
APK_BYTES = 148517396
ARTIFACT_ID = 13698305559
RUN_ID = 42666431368
JOB_ID = 129331191485
ACTIVATION_SHA = 'ec4a6c1ac9b3a9eca76b28ee59ce4589669bb758'
APK_NAME = 'NovaOS-1.7.38-code73-owner-review.apk'
SOURCE_SHA = 'c0db80efc8d1342896984bd937abb01dc786a0f4'
EVIDENCE_NAME = 'NovaOS-1.7.38-code73-evidence.zip'
EVIDENCE_BYTES = 104890
EVIDENCE_SHA256 = 'c56fb060c953e1cdd35cf5d2eeee297475a52fa6fb18212ebe4f37d450cdf45d'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.38', version_code=73,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code72_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.38 Code 73 APK and nested original release-summary provenance')

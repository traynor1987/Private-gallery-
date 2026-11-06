#!/usr/bin/env python3
"""Extract pinned distributed 1.7.11.5 code61; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = '26d0f86d8ece1b3c22d4b41c32eddca2592e9722c3b7b93f7d420fd42aeaa678'
ARCHIVE_BYTES = 60738267
APK_SHA256 = '90083faaeb8ebaa896451d9dc721c4d26ee170135b3a4e1aad126038bcd7ef3e'
APK_BYTES = 144991916
ARTIFACT_ID = 13531400377
RUN_ID = 42269170059
JOB_ID = 128068998830
ACTIVATION_SHA = '4904c937d3773aa07b117022edbcafcbcf8c5d5a'
APK_NAME = 'NovaOS-1.7.11.5-home2-code61-owner-acceptance.apk'
SOURCE_SHA = '2da2895e387839ec37d0590dfb713dc1bec99853'
EVIDENCE_NAME = 'NovaOS-1.7.11.5-home2-code61-evidence.zip'
EVIDENCE_BYTES = 112168
EVIDENCE_SHA256 = '50fc64664a648aa78c049b9e6b55cb2cbaf4918029df208d0f4c6638a46f0948'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.11.5', version_code=61,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code60_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.11.5 code61 APK and nested original release-summary provenance')

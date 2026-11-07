#!/usr/bin/env python3
"""Extract pinned distributed 1.7.11.7 code63; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = 'ac871706d60359a2f08f12806bf146829b1d9e848d172d7e3edeb608507e8b6a'
ARCHIVE_BYTES = 60999212
APK_SHA256 = '84bbf1aab425c33a06d737614c6160f9daa0f5fc3dcdd74e7cb06e77441b5b6e'
APK_BYTES = 145696456
ARTIFACT_ID = 13580371185
RUN_ID = 42386518404
JOB_ID = 128444994749
ACTIVATION_SHA = '278bcc5b1ba7f75eac73f2a171711a7255c8dae9'
APK_NAME = 'NovaOS-1.7.11.7-home2-code63-owner-acceptance.apk'
SOURCE_SHA = '4738bcb12abb91ed289a78fcdcfaec7bc631cf0f'
EVIDENCE_NAME = 'NovaOS-1.7.11.7-home2-code63-evidence.zip'
EVIDENCE_BYTES = 112346
EVIDENCE_SHA256 = '234e0839daac9becd08e6fa1e112616a194015fa8ab828b38f365e68c2738f17'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.11.7', version_code=63,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code62_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.11.7 code63 APK and nested original release-summary provenance')

#!/usr/bin/env python3
"""Extract pinned distributed 1.7.11.6 code62; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = '8fd0c13009c170dd1dbbff4178e527258d9a0c3c038f31f20dbd8793597bf766'
ARCHIVE_BYTES = 60941459
APK_SHA256 = 'd49cd333fa1834bfd844147babc323bd6e4b557ecd183d8fb9d50e4e084ac09e'
APK_BYTES = 145516232
ARTIFACT_ID = 13573884565
RUN_ID = 42372575268
JOB_ID = 128403584014
ACTIVATION_SHA = '9643ba5f7ff62b498f9414a6b92e4ffe66d3bd9d'
APK_NAME = 'NovaOS-1.7.11.6-home2-code62-owner-acceptance.apk'
SOURCE_SHA = '35d061c2c94297f7e063cc0b47037409f3642f9e'
EVIDENCE_NAME = 'NovaOS-1.7.11.6-home2-code62-evidence.zip'
EVIDENCE_BYTES = 111114
EVIDENCE_SHA256 = '2dc55e2810cc7e7cbc34a2b5b5aa86840bf223e1511cc949238037f5d935e058'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.11.6', version_code=62,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code61_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.11.6 code62 APK and nested original release-summary provenance')

#!/usr/bin/env python3
"""Extract pinned distributed 1.7.11 code56; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = 'aa42e6a06abd80710767cebe1b4fe58380fe68361222a7dfd2856dc7883221e6'
APK_SHA256 = '2efbe7a29a08a2677b04052eb268e908bb89f5a03fc23677b1791a1c11ea2141'
APK_BYTES = 144418414
ARTIFACT_ID = 13469068696
RUN_ID = 42143438856
ACTIVATION_SHA = '8803742e02d0e58e639687ff30b4b473109c0d47'
APK_NAME = 'NovaOS-1.7.11-home2-code56-owner-acceptance.apk'
SOURCE_SHA = '0e7f7c07608a6b439a4471e613531a758028c849'
EVIDENCE_NAME = 'NovaOS-1.7.11-home2-code56-evidence.zip'
EVIDENCE_BYTES = 100239
EVIDENCE_SHA256 = '26e7c7810b6c9e6343612ba7ff75db8e7dbb3a225df78c1f72c51c536c7236d1'
CERTIFICATE_SHA256 = '94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validated_apk(archive):
    archive = Path(archive)
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.11', version_code=56,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code54_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.11 code56 APK and nested original release-summary provenance')

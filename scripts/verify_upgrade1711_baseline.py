#!/usr/bin/env python3
"""Extract pinned distributed 1.7.30 visual foundation Code 64; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = '46aed1b6c8b0019bc60df1e6d8de422467aecb8a8bb5f1c4e9c63e8ae0a9cbcf'
ARCHIVE_BYTES = 61126104
APK_SHA256 = 'bd1fda10b71c2099cdafd5989e8a20bb20496be1d358bf958a44316241c69d69'
APK_BYTES = 146024148
ARTIFACT_ID = 13584728173
RUN_ID = 42398677707
JOB_ID = 128481534136
ACTIVATION_SHA = '826ab9a48bcd20ff6dd764bf1c5caa770d45f676'
APK_NAME = 'NovaOS-1.7.30-visual-foundation-code64-owner-review.apk'
SOURCE_SHA = '2fe2e6b4f959704524fe4f7a3f44dcd770a5cdfb'
EVIDENCE_NAME = 'NovaOS-1.7.30-visual-foundation-code64-evidence.zip'
EVIDENCE_BYTES = 112461
EVIDENCE_SHA256 = '04066afa6c8604be2cad6a6afe173093dce46ebc149ccd4f3bb117cd4fc1ca08'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.30-visual-foundation', version_code=64,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code63_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.30 visual foundation Code 64 APK and nested original release-summary provenance')

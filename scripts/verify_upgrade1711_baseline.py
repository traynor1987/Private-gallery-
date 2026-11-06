#!/usr/bin/env python3
"""Extract pinned distributed 1.7.11.1 code57; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = '1a3051db04518097e2e793f2e02595c2f6df5e2ec76e6a532de0f54dadce9903'
ARCHIVE_BYTES = 60512367
APK_SHA256 = 'c499ac46b393d1eaac6e625b172415a4e226c5d13452f4fcdfb9391755d01c76'
APK_BYTES = 144500338
ARTIFACT_ID = 13477498130
RUN_ID = 42161566466
ACTIVATION_SHA = '79c9edb79f9d57b43144ff1bc3d61633478418c4'
APK_NAME = 'NovaOS-1.7.11.1-home2-code57-owner-acceptance.apk'
SOURCE_SHA = '3af2289e444771fd87188743879bec3361ea6346'
EVIDENCE_NAME = 'NovaOS-1.7.11.1-home2-code57-evidence.zip'
EVIDENCE_BYTES = 103746
EVIDENCE_SHA256 = '4c206eaad1db0fb6d8c1f0f1add302094c1554b5f19aaa6560cf6c9a8e73c73c'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.11.1', version_code=57,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code56_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.11.1 code57 APK and nested original release-summary provenance')

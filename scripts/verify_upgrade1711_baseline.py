#!/usr/bin/env python3
"""Extract pinned distributed 1.7.37 Code 72; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = '9c10e0c750d7b312991418f80060dea002aac734f760c962e079016c07a319fe'
ARCHIVE_BYTES = 61906537
APK_SHA256 = '7b7fd42d49ce5b465ca6096ce5065757577923a42d42bf389849ef5374f7d49e'
APK_BYTES = 148206108
ARTIFACT_ID = 13687062546
RUN_ID = 42641862359
JOB_ID = 129244771808
ACTIVATION_SHA = '6da77e092b211c7941e530ad997288ffb281af87'
APK_NAME = 'NovaOS-1.7.37-code72-owner-review.apk'
SOURCE_SHA = '7422343e73b13569a6a18501ecfe077bcca55c3d'
EVIDENCE_NAME = 'NovaOS-1.7.37-code72-evidence.zip'
EVIDENCE_BYTES = 104518
EVIDENCE_SHA256 = 'd54afe7a209edb6d63315331b0052601fc775695884e80bb955418149b9a404c'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.37', version_code=72,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code71_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.37 Code 72 APK and nested original release-summary provenance')

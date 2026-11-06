#!/usr/bin/env python3
"""Extract pinned distributed 1.7.11.3 code59; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = 'c4ffbed29381c36f349d43e1a52c46acd778687d3cc5899e4a9d48bb64afbc6e'
ARCHIVE_BYTES = 60654499
APK_SHA256 = '6db4f5853c8bbcdc379c0887002620d4f75cbd3c86dc98c2e46707b833d94796'
APK_BYTES = 144778924
ARTIFACT_ID = 13502360091
RUN_ID = 42214765335
JOB_ID = 127881954559
ACTIVATION_SHA = 'd62d8fc3c1fb921db52c63a1364de3edad102c13'
APK_NAME = 'NovaOS-1.7.11.3-home2-code59-owner-acceptance.apk'
SOURCE_SHA = 'ab659cf3578d08cdc0deb643f887e561abb14c63'
EVIDENCE_NAME = 'NovaOS-1.7.11.3-home2-code59-evidence.zip'
EVIDENCE_BYTES = 108169
EVIDENCE_SHA256 = '06ce826e58021da23aac3c66a83a111008038b040c53ee6540b082cabda47c64'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.11.3', version_code=59,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code58_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.11.3 code59 APK and nested original release-summary provenance')

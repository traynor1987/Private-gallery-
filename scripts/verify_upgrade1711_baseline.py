#!/usr/bin/env python3
"""Extract pinned distributed 1.7.31 Sanctuary Code 66; never rebuild/substitute an application baseline."""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = '6e8df741fa512e3a26a483698fc1fe84e4b2cb8775875ba7047d63861a850d33'
ARCHIVE_BYTES = 61464051
APK_SHA256 = '35370fdbf2e896e8e2dcf667b21991c08c4eaaaf1cfc7c35cc992c8514b4ef66'
APK_BYTES = 146943952
ARTIFACT_ID = 13599317033
RUN_ID = 42434691690
JOB_ID = 128590919516
ACTIVATION_SHA = '5b428ca85727d8e3e5e5060e8280cb6f1a78e684'
APK_NAME = 'NovaOS-1.7.31-sanctuary-code66-owner-review.apk'
SOURCE_SHA = 'de641d42df1ddae6ba6a59a23f0bc44af85ae1f9'
EVIDENCE_NAME = 'NovaOS-1.7.31-sanctuary-code66-evidence.zip'
EVIDENCE_BYTES = 103755
EVIDENCE_SHA256 = '34f0895691f769b5ab8e366d3a593e5e8983587ca7476a04be1327381df830bb'
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
        expected = dict(source_sha=SOURCE_SHA, version='1.7.31', version_code=66,
                        package='uk.co.traynor.privategallery', apk_sha256=APK_SHA256,
                        original_certificate_sha256=CERTIFICATE_SHA256,
                        original_key_code65_upgrade='PASS', restart='PASS')
        require(all(summary.get(key) == value for key, value in expected.items()), 'Historical application source/version/certificate provenance differs')
    return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.31 Sanctuary Code 66 APK and nested original release-summary provenance')

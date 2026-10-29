#!/usr/bin/env python3
"""Extract the pinned distributed 1.7.7 APK; never rebuild/substitute an application baseline."""
import hashlib
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = '16873a3b24abcb311d21ca11e93e1af1707be42946a817770e4b9d629e790869'
APK_SHA256 = '696e80071497501d8da6cd8dd3e2c9728a63b0d4abc9e88c4ab46338fef97a2e'
SOURCE_SHA = '25aae6d59f79c455e47acc73a5c99f9c8f15242a'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validated_apk(archive):
    archive = Path(archive)
    require(hashlib.sha256(archive.read_bytes()).hexdigest() == ARCHIVE_SHA256, 'Historical archive checksum differs')
    with zipfile.ZipFile(archive) as zipped:
        names = zipped.namelist()
        require(len(names) == len(set(names)), 'Duplicate archive members')
        apks = [name for name in names if name.lower().endswith('.apk')]
        require(len(apks) == 1, 'Historical archive must contain exactly one APK')
        sources = [name for name in names if name == 'production-evidence/source-sha.txt' or name.endswith('/production-evidence/source-sha.txt')]
        require(len(sources) == 1 and zipped.read(sources[0]).decode().strip() == SOURCE_SHA, 'Historical application source differs')
        apk = zipped.read(apks[0])
        require(hashlib.sha256(apk).hexdigest() == APK_SHA256, 'Original signed baseline APK checksum differs')
        return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    require(not destination.exists(), 'Baseline destination already exists')
    destination.write_bytes(apk)
    print('PASS: exact archived original signed 1.7.7 APK and frozen application source')

#!/usr/bin/env python3
"""Extract the pinned distributed 1.7.8 code48 APK; never rebuild/substitute an application baseline."""
import hashlib
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = 'dac16fa3c5ef0a5ba5fc8d0c8b6640c0e293e3c0407cf1379f3c9717942af416'
APK_SHA256 = 'c9f43de796f0af15b95df54aabba686a11b4b6b6bb4eb08cf00169cccf7b7ec5'
SOURCE_SHA = '41b6d065a3fbef2f711198c9c778f7100e620c8f'


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
    print('PASS: exact archived original signed 1.7.8 code48 APK and frozen application source')

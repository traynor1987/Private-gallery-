#!/usr/bin/env python3
"""Extract the pinned distributed 1.7.9 code51 APK; never rebuild/substitute an application baseline."""
import hashlib
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = 'a7ad5e4d6c243e195a5d35db1f4a12711de1842bd6260aa318203e53ac691cb7'
APK_SHA256 = '9f99f0e8df4dbdc6219aa147525866f0ace699b1b4ae9bab1f6b647ac4d7a178'
APK_BYTES = 137719190
SOURCE_SHA = '19e3275ef17746e780d64e6a684d94bab2cabdc3'


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
        require(zipped.getinfo(apks[0]).file_size == APK_BYTES, 'Historical APK length differs')
        apk = zipped.read(apks[0])
        require(hashlib.sha256(apk).hexdigest() == APK_SHA256, 'Original signed baseline APK checksum differs')
        return apk


if __name__ == '__main__':
    apk = validated_apk(sys.argv[1])
    destination = Path(sys.argv[2])
    with destination.open('xb') as output:
        output.write(apk)
    print('PASS: exact archived original signed 1.7.9 code51 APK and frozen application source')

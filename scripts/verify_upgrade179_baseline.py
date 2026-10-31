#!/usr/bin/env python3
"""Extract the pinned distributed 1.7.8 code50 APK; never rebuild/substitute an application baseline."""
import hashlib
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = '9c6bf3c0b8f8032457330b7a3394305069a1ba96b4a4db16decfa387e0af3e0c'
APK_SHA256 = 'df958e447ce1c5a9bc9b515d16de03c0d11a961ae6efb030b305b2dfe733ccf3'
APK_BYTES = 105601622
SOURCE_SHA = '4820e176dcb60a3aa2f1bfd7b36e2de19fafee11'


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
    print('PASS: exact archived original signed 1.7.8 code50 APK and frozen application source')

#!/usr/bin/env python3
"""Extract the pinned distributed 1.7.10 code52 APK; never rebuild/substitute an application baseline."""
import hashlib
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = 'b7c124eebf67aeb9d5fa5143146652250556ea184c161b38544cdce7653d0a29'
APK_SHA256 = '0b22f736055738f4e3f552e4939cae1ea767cbce58a4908d04666786e0c37ba2'
APK_BYTES = 137915802
SOURCE_SHA = 'f301ee73320df2e366b7cd258dae5105296237a7'


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
    print('PASS: exact archived original signed 1.7.10 code52 APK and frozen application source')

#!/usr/bin/env python3
"""Extract the pinned distributed 1.7.10.2 code54 APK; never rebuild/substitute an application baseline."""
import hashlib
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = '7e141abd9ba9c1e9e440dd0e08bf6cb6bfd869808f0d5bec226ff43fcb858574'
APK_SHA256 = '3e9497d87c074d6d49bfd42a2b44cc5f2cd46ec0b7476d35d030e75b9cd35ce2'
APK_BYTES = 138323890
SOURCE_SHA = '9efd19540a1d75efa1b30f4e23c9cbb096f93aff'


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
    print('PASS: exact archived original signed 1.7.10.2 code54 APK and frozen application source')

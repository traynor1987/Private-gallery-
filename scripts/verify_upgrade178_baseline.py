#!/usr/bin/env python3
"""Extract the pinned distributed 1.7.8 code49 APK; never rebuild/substitute an application baseline."""
import hashlib
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = 'd43de492bd9795b76035f298759f82ab2c8c1211f642fca997e8a97f7bdba2f2'
APK_SHA256 = '99a3dad8fb8dcaf4870e02a5ad645697415528892a1c6b3f7da32cd65ae25feb'
SOURCE_SHA = '60f089d6e003fd13f8b7649feb6f44167e5028a2'


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
    print('PASS: exact archived original signed 1.7.8 code49 APK and frozen application source')

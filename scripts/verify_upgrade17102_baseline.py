#!/usr/bin/env python3
"""Extract the pinned distributed 1.7.10.1 code53 APK; never rebuild/substitute an application baseline."""
import hashlib
from pathlib import Path
import sys
import zipfile

ARCHIVE_SHA256 = 'fcc70278aca63b71e5321fdbd1f2e16235ff5fec7f3a3cb27b731280ec5e1707'
APK_SHA256 = '8a13404b42720e5962478345fad964cdcc923a3115b632beb412a2813547a3a3'
APK_BYTES = 138241966
SOURCE_SHA = 'f923d62de7696e731c2ec7809d772081ab0e1204'


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
    print('PASS: exact archived original signed 1.7.10.1 code53 APK and frozen application source')

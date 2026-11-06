#!/usr/bin/env python3
"""Require already-signed instrumentation to contain precisely the reviewed compiled payload.

Cryptographic original-certificate verification is performed separately by apksigner.
Only standard JAR signature entries may differ; application resources/classes/manifest may not.
"""
import argparse
from pathlib import Path
import re
import zipfile

SIGNATURE = re.compile(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))', re.I)

def payload(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate instrumentation APK entries')
        return {name: archive.read(name) for name in names if not SIGNATURE.fullmatch(name)}

def verify(compiled, signed):
    original, candidate = payload(compiled), payload(signed)
    if not original or original != candidate:
        raise ValueError('Signed instrumentation differs from reviewed compiled payload')
    return len(original)

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('compiled',type=Path);parser.add_argument('signed',type=Path)
    args=parser.parse_args()
    print('PASS: original compiled instrumentation payload retained;',verify(args.compiled,args.signed),'entries; no signing claim')

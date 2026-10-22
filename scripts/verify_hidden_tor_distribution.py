#!/usr/bin/env python3
"""Inventory the resolved pinned Tor artifact and packaged native libraries; not a reproducibility claim."""
import hashlib
import json
import pathlib
import sys
import zipfile

apk = pathlib.Path(sys.argv[1])
output = pathlib.Path(sys.argv[2])
cache = pathlib.Path.home() / '.gradle/caches/modules-2/files-2.1/info.guardianproject/tor-android/0.4.9.14'
aars = list(cache.glob('*/*.aar'))
assert len(aars) == 1, f'Expected one resolved pinned Tor AAR, found {len(aars)}'
artifact = aars[0]
with zipfile.ZipFile(artifact) as source, zipfile.ZipFile(apk) as package:
    native = {n: hashlib.sha256(source.read(n)).hexdigest() for n in source.namelist() if n.startswith('jni/') and n.endswith('/libtor.so')}
    assert {'jni/arm64-v8a/libtor.so', 'jni/armeabi-v7a/libtor.so', 'jni/x86_64/libtor.so'} <= native.keys(), 'Missing required Tor ABI'
    packaged = {}
    for path, digest in native.items():
        target = path.replace('jni/', 'lib/', 1)
        # AGP can strip native debug symbols; inventory both source and output rather than asserting byte equality.
        assert target in package.namelist(), f'Missing packaged native library {target}'
        packaged[target] = hashlib.sha256(package.read(target)).hexdigest()
    assert not any('privoxy' in n.lower() or 'iptables' in n.lower() for n in package.namelist()), 'Unexpected historical proxy/firewall binary'
    notices = package.read('assets/third_party_notices.txt').decode()
    for component in ['Native Tor dependency: tor', 'Native Tor dependency: openssl', 'Native Tor dependency: libevent', 'Native Tor dependency: zlib', 'Native Tor dependency: zstd', 'jtorctl0.4.5.7']:
        assert component in notices, f'Missing notice: {component}'
    metadata = source.read('META-INF/com/android/build/gradle/aar-metadata.properties').decode()
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps({'coordinate': 'info.guardianproject:tor-android:0.4.9.14', 'source_commit': '0340ba1609cb73e663e202a316d083803e399db4', 'publisher_repository_commit': '5fdb8dbcfe98d1b7e027e2f99bc086709870b005', 'aar_sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(), 'aar_metadata': metadata, 'source_native_sha256': native, 'apk_native_sha256': packaged, 'limitations': 'Inventory and pinned publisher/source references only; not independently reproduced native binaries or live network verification.'}, indent=2) + '\n')
print('PASS: pinned Tor AAR/native ABI inventory and bundled component notices')

#!/usr/bin/env python3
"""Reject accidentally tracked/packaged production weights; never downloads any data."""
import pathlib, subprocess, sys, zipfile
weight_suffixes = {'.safetensors', '.gguf', '.onnx', '.ckpt', '.pt', '.pth', '.mnn'}
tracked = subprocess.check_output(['git', 'ls-files', '-z']).decode().split('\0')
for name in filter(None, tracked):
    p = pathlib.Path(name)
    assert p.suffix.lower() not in weight_suffixes, f'Model weights must not be tracked: {name}'
    if p.is_file():
        assert p.stat().st_size < 25_000_000, f'Unexpected large tracked file: {name}'
        with p.open('rb') as f:
            assert not f.read(80).startswith(b'version https://git-lfs.github.com/spec'), f'No model/data LFS pointers: {name}'
if len(sys.argv) > 1:
    apk = pathlib.Path(sys.argv[1])
    with zipfile.ZipFile(apk) as z:
        for f in z.infolist():
            assert pathlib.Path(f.filename).suffix.lower() not in weight_suffixes, f'Model in APK: {f.filename}'
        assert not any(f.filename.endswith('/libprivate_gallery_ai.so') for f in z.infolist()), 'Retired native AI runtime is still shipped'
    print(f'APK_BYTES={apk.stat().st_size}')
print('PASS: retired local AI runtime and model weights are absent')

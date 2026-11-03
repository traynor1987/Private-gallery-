#!/usr/bin/env python3
"""Verify every pinned Go distribution file, allowing only the pinned patch."""
import hashlib
import pathlib
import subprocess
import sys
import tarfile
import tempfile
archive, toolchain, patch = map(pathlib.Path, sys.argv[1:])
patched = set()
for line in patch.read_text().splitlines():
    if line.startswith('+++ b/'):
        patched.add(line[6:])
with tempfile.TemporaryDirectory() as directory, tarfile.open(archive) as tar:
    expected = pathlib.Path(directory)
    for name in patched:
        target = expected / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(tar.extractfile('go/' + name).read())
    subprocess.run(['patch', '--silent', '-p1', '-d', str(expected), '-i', str(patch)], check=True)
    expected_files = set()
    for member in tar:
        if not member.isfile():
            continue
        relative = member.name.removeprefix('go/')
        expected_files.add(relative)
        target = toolchain / relative
        if not target.is_file():
            sys.exit('Pinned Go distribution file missing: ' + relative)
        original = (expected / relative).read_bytes() if relative in patched else tar.extractfile(member).read()
        if hashlib.sha256(target.read_bytes()).digest() != hashlib.sha256(original).digest():
            sys.exit('Pinned Go distribution mismatch: ' + relative)

    actual_files = {str(path.relative_to(toolchain)) for path in toolchain.rglob('*') if path.is_file()}
    extra = actual_files - expected_files
    if extra:
        sys.exit('Unexpected Go distribution files: ' + ', '.join(sorted(extra)[:10]))

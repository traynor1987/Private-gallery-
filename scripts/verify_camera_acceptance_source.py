#!/usr/bin/env python3
"""One-candidate test-evidence reuse gate; never substitutes for upgrade/signature checks."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

SOURCE = '2dbf38daa91d6cb56af8839794a0dd7199a90190'
APP_TREE = '184629ab55055524c461af9a63c9d23336af2b45'
FIXTURE = '5e0f2df53c0b81d17fc6e7582280722469a2a016'
RUN = 39947347679
ARTIFACT = 12500079211
DIGEST = '900d2e5e4bd3da0028e529e104cedb892766d5cece8ba9efc6902116d1aeeded'
SUITES = {
    'jvm': ('test-results/testDebugUnitTest/', 333),
    'android_preflight': ('acceptance-diagnostics/hidden-browser-preflight/', 69),
    'android_full': ('outputs/androidTest-results/connected/debug/', 292),
}
ALLOWED = {
    'V1_7_RECOVERY.md', 'scripts/upgrade/UpgradeFixtureTest.kt',
    '.github/workflows/camera-acceptance.yml',
    'scripts/verify_camera_acceptance_source.py',
    'scripts/tests/test_camera_acceptance_source.py',
}

def check_metadata(metadata):
    run = metadata.get('workflow_run', {})
    if (metadata.get('id') != ARTIFACT or metadata.get('digest') != 'sha256:' + DIGEST
            or run.get('id') != RUN or run.get('head_sha') != SOURCE
            or run.get('repository_id') != 1374324372 or metadata.get('expired', False)):
        raise ValueError('Prior test artifact provenance mismatch')

def check_paths(paths):
    for path in paths:
        if path not in ALLOWED and not path.startswith('docs/release17/'):
            raise ValueError('Untested source change: ' + path)

def count_suites(archive):
    counts = {}
    for group, (prefix, required) in SUITES.items():
        cases = []
        for name in archive.namelist():
            if name.startswith(prefix) and Path(name).name.startswith('TEST-') and name.endswith('.xml'):
                cases.extend(ET.fromstring(archive.read(name)).iter('testcase'))
        failures = sum(c.find('failure') is not None or c.find('error') is not None for c in cases)
        skipped = sum(c.find('skipped') is not None for c in cases)
        if len(cases) != required or failures or skipped:
            raise ValueError(f'{group}: expected {required} passed, got {len(cases)}, {failures} failed, {skipped} skipped')
        counts[group] = {'tests': len(cases), 'failures': failures, 'skipped': skipped}
    return counts

def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()

def main():
    if (os.environ.get('GITHUB_ACTIONS') != 'true'
            or os.environ.get('GITHUB_REPOSITORY') != 'traynor1987/Private-gallery-'
            or os.environ.get('GITHUB_REF') != 'refs/heads/release/1.7-encrypted-camera'
            or os.environ.get('GITHUB_EVENT_NAME') != 'push'):
        raise ValueError('Acceptance gate requires the authorised repository branch push')
    metadata = json.loads(Path(sys.argv[1]).read_text())
    check_metadata(metadata)
    digest = hashlib.sha256()
    with open(sys.argv[2], 'rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    if digest.hexdigest() != DIGEST:
        raise ValueError('Downloaded artifact bytes differ from pinned SHA-256')
    if git('rev-parse', SOURCE + ':app') != APP_TREE or git('rev-parse', 'HEAD:app') != APP_TREE:
        raise ValueError('Application is not identical to the passing test source')
    if git('rev-parse', 'HEAD:scripts/upgrade/UpgradeFixtureTest.kt') != FIXTURE:
        raise ValueError('Upgrade fixture differs from reviewed version-only correction')
    changed = git('diff', '--name-only', SOURCE, 'HEAD').splitlines()
    check_paths(changed)
    with zipfile.ZipFile(sys.argv[2]) as archive:
        counts = count_suites(archive)
    evidence = {
        'candidate_source': git('rev-parse', 'HEAD'), 'tested_source': SOURCE,
        'app_tree': APP_TREE, 'reviewed_upgrade_fixture_blob': FIXTURE,
        'test_run': RUN, 'test_run_attempt': 2, 'artifact': ARTIFACT,
        'artifact_sha256': DIGEST, 'suites': counts, 'changed_paths': changed,
        'scope': 'Reused passing tests for identical application/build source. Fresh upgrades and signing remain mandatory.',
        'failed_later_runs': 'CI657 attempts 1 and 2 each failed one repeated external Tor test; neither is represented as passing.',
    }
    output = Path('app/build/production-evidence/test-provenance.json')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(evidence, indent=2) + '\n')
    print(json.dumps(evidence, indent=2))

if __name__ == '__main__':
    main()

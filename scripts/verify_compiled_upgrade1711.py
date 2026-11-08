#!/usr/bin/env python3
"""Bind the pre-signer baseline-ABI instrumentation to reviewed source controls."""
import argparse
import hashlib
import json
from pathlib import Path
import re

BASELINE = 'a02cfcf6bb3bbae15f00935c2b2124ae7eb4e674'
TARGET = b'uk.co.traynor.privategallery.hiddenprototype'
BUILD_TARGET = b'      applicationIdSuffix = ".hiddenprototype"'

def sha(raw): return hashlib.sha256(raw).hexdigest()
def require(condition, message):
    if not condition: raise ValueError(message)
def transform(fixture, build):
    require(fixture.count(TARGET) == 1, 'Unexpected fixture package transformation')
    require(build.count(BUILD_TARGET) == 1, 'Unexpected baseline build package transformation')
    return fixture.replace(TARGET, b'uk.co.traynor.privategallery'), build.replace(BUILD_TARGET, b'')
def validate(proof, source, fixture, compiler, apk=None, original_build=None):
    require(re.fullmatch('[a-f0-9]{40}', source) is not None and source != '0'*40, 'Unfrozen candidate source')
    require(proof.get('schema') == 1 and proof.get('decision') == 'COMPILED', 'Missing compilation receipt')
    require(proof.get('baseline_source_sha') == BASELINE and proof.get('candidate_source_sha') == source, 'Wrong compilation source')
    require(proof.get('fixture_sha256') == sha(fixture) and fixture.count(TARGET) == 1, 'Unreviewed compiled fixture')
    require(proof.get('transformed_fixture_sha256') == sha(fixture.replace(TARGET, b'uk.co.traynor.privategallery')), 'Wrong fixture transformation')
    require(proof.get('compiler_sha256') == sha(compiler), 'Unreviewed compiler')
    require(proof.get('transformations') == ['remove_debug_applicationIdSuffix', 'fixture_target_production_package'], 'Unreviewed source transformation')
    for field in ('baseline_build_sha256', 'transformed_build_sha256', 'instrumentation_apk_sha256', 'instrumentation_package_sha256', 'instrumentation_manifest_sha256'):
        require(re.fullmatch('[a-f0-9]{64}', proof.get(field, '')) is not None and proof[field] != '0'*64, 'Missing compiled hash '+field)
    require(type(proof.get('instrumentation_apk_bytes')) is int and proof['instrumentation_apk_bytes'] > 0, 'Empty instrumentation')
    if original_build is not None:
        _, transformed = transform(fixture, original_build)
        require(proof['baseline_build_sha256'] == sha(original_build) and proof['transformed_build_sha256'] == sha(transformed), 'Baseline build transformation differs')
    if apk is not None:
        require(proof['instrumentation_apk_sha256'] == sha(apk) and proof['instrumentation_apk_bytes'] == len(apk), 'Compiled APK changed before resigning')
    return proof

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--directory', type=Path, required=True);p.add_argument('--source', required=True)
    p.add_argument('--fixture', type=Path, required=True);p.add_argument('--compiler', type=Path, required=True)
    args=p.parse_args();root=args.directory
    for name in ('compilation.json','instrumentation.apk','baseline-build.gradle.kts','instrumentation-package.txt','instrumentation-manifest.txt'):
        require((root/name).is_file() and not (root/name).is_symlink(), 'Missing original compilation input '+name)
    proof=json.loads((root/'compilation.json').read_text())
    package=(root/'instrumentation-package.txt').read_bytes()
    require(proof.get('instrumentation_package_sha256')==sha(package),'Instrumentation package receipt changed')
    require(any(line.startswith("package: name='uk.co.traynor.privategallery.test'") for line in package.decode().splitlines()),'Wrong instrumentation package')
    manifest=(root/'instrumentation-manifest.txt').read_bytes()
    require(proof.get('instrumentation_manifest_sha256')==sha(manifest),'Instrumentation manifest receipt changed')
    text=manifest.decode()
    require(len(re.findall(r'^\s*E: instrumentation\b',text,re.M))==1,'Wrong instrumentation count')
    require(len(re.findall(r'A: android:targetPackage\([^)]*\)="uk\.co\.traynor\.privategallery"',text))==1,'Wrong instrumentation target')
    require(len(re.findall(r'A: android:name\([^)]*\)="androidx\.test\.runner\.AndroidJUnitRunner"',text))==1,'Wrong instrumentation runner')
    validate(proof,args.source,args.fixture.read_bytes(),args.compiler.read_bytes(),
             (root/'instrumentation.apk').read_bytes(),(root/'baseline-build.gradle.kts').read_bytes())
    print('PASS: reviewed baseline-compatible instrumentation compilation; no signing or upgrade claim')
if __name__=='__main__':main()

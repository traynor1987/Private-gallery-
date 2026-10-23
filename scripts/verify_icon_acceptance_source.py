#!/usr/bin/env python3
"""Reuse full 1.7.1 tests only for an allowlisted launcher-resource-only delta.

Fresh launcher rendering, debug upgrade and original-key production upgrade are
mandatory in the caller. This gate never treats a failed/unfinished run as green.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

SOURCE='9413feb435c323ae4860084ef0935f0d685dcd06'
RUN=40019880030
REPO='traynor1987/Private-gallery-'
ALLOWED={
 'V1_7_1_RECOVERY.md', '.github/workflows/launcher-acceptance.yml', '.github/workflows/android.yml',
 'scripts/export_launcher_icon.py', 'scripts/check_launcher_branding.py',
 'scripts/verify_icon_acceptance_source.py', 'scripts/tests/test_icon_acceptance_source.py',
 'app/src/androidTest/java/uk/co/traynor/privategallery/ui/LauncherBrandingTest.kt',
 'app/src/main/AndroidManifest.xml', 'app/src/main/res/values/colors.xml',
 'app/src/main/res/drawable/ic_private_gallery_foreground.xml',
 'app/src/main/res/drawable/ic_private_gallery_monochrome.xml',
 'app/src/main/res/mipmap-anydpi/ic_launcher.xml',
}

def check_paths(paths):
    for p in paths:
        if (p in ALLOWED or p.startswith('docs/branding/') or
            re.fullmatch(r'app/src/main/res/(?:drawable-(?:mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi)/private_gallery_launcher_artwork.png|mipmap-(?:mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi)/ic_launcher(?:_round)?.png|mipmap-anydpi-v(?:26|33)/ic_launcher_round.xml)',p)):
            continue
        raise ValueError('Untested application/build/test change: '+p)

def verify_run(run,metadata):
    if (run.get('id')!=RUN or run.get('head_sha')!=SOURCE or run.get('conclusion')!='success'
        or run.get('status')!='completed' or run.get('event')!='push'
        or run.get('head_branch')!='release/1.7.1-professional-ui'
        or run.get('path')!='.github/workflows/android.yml'
        or run.get('repository',{}).get('full_name')!=REPO):
        raise ValueError('Full 1.7.1 run must be the exact successful source')
    if (metadata.get('workflow_run',{}).get('id')!=RUN or
        metadata.get('workflow_run',{}).get('head_sha')!=SOURCE or
        metadata.get('name')!='private-gallery-debug-and-instrumentation-diagnostics' or
        metadata.get('expired') or not re.fullmatch('sha256:[0-9a-f]{64}',metadata.get('digest',''))):
        raise ValueError('Test artifact provenance mismatch')

def check_suite(archive,prefix,expected):
    cases=[]
    for n in archive.namelist():
        if n.startswith(prefix) and Path(n).name.startswith('TEST-') and n.endswith('.xml'):
            cases.extend(ET.fromstring(archive.read(n)).iter('testcase'))
    if len(cases)!=expected or any(c.find(t) is not None for c in cases for t in ('failure','error','skipped')):
        raise ValueError(f'{prefix}: requires {expected} passed, zero failures/errors/skips')
    return expected

def git(*args): return subprocess.check_output(['git',*args],text=True).strip()

def main():
    assert os.environ.get('GITHUB_REPOSITORY')==REPO
    assert os.environ.get('GITHUB_REF')=='refs/heads/release/1.7.1-professional-ui'
    assert os.environ.get('GITHUB_EVENT_NAME')=='push'
    run=json.loads(Path(sys.argv[1]).read_text()); meta=json.loads(Path(sys.argv[2]).read_text())
    verify_run(run,meta)
    digest=hashlib.file_digest(open(sys.argv[3],'rb'),'sha256').hexdigest()
    if 'sha256:'+digest!=meta['digest']: raise ValueError('Test artifact ZIP digest mismatch')
    git('merge-base','--is-ancestor',SOURCE,'HEAD')
    paths=git('diff','--name-only',SOURCE,'HEAD').splitlines(); check_paths(paths)
    # Manifest and shared colours must equal the reviewed icon commit exactly;
    # an allowlisted filename is not permission to alter components or other colours.
    for path in ('app/src/main/AndroidManifest.xml','app/src/main/res/values/colors.xml'):
        if git('rev-parse','HEAD:'+path)!=git('rev-parse','25fd7afadb641128f57789f7cfd9f360b84e99af:'+path):
            raise ValueError('Icon manifest/colour delta exceeded reviewed commit')
    # Full CI may skip only this new focused harness. All original jobs/steps
    # and every application/build/existing-test trigger remain byte-identical.
    ignored=['.github/workflows/launcher-acceptance.yml','scripts/verify_icon_acceptance_source.py','scripts/tests/test_icon_acceptance_source.py','scripts/check_launcher_branding.py','app/src/androidTest/java/uk/co/traynor/privategallery/ui/LauncherBrandingTest.kt']
    block='    paths-ignore:\n'+''.join("      - '"+v+"'\n" for v in ignored)
    original=git('show',SOURCE+':.github/workflows/android.yml')
    current=git('show','HEAD:.github/workflows/android.yml')
    if current!=original.replace('  pull_request:',block+'  pull_request:',1):
        raise ValueError('Full CI changed beyond focused-harness path exclusions')
    with zipfile.ZipFile(sys.argv[3]) as z:
        suites={prefix:check_suite(z,prefix,count) for prefix,count in (
          ('test-results/testDebugUnitTest/',333),
          ('acceptance-diagnostics/hidden-browser-preflight/',70),
          ('acceptance-diagnostics/professional-ui/results/',31),
          ('outputs/androidTest-results/connected/debug/',316))}
        for name in ('full-regression-source-sha.txt','professional-ui/source-sha.txt'):
            if z.read('acceptance-diagnostics/'+name).decode().strip()!=SOURCE:
                raise ValueError('Archived full/focused source mismatch')
    evidence={'candidate_source':git('rev-parse','HEAD'),'tested_application_source':SOURCE,
      'full_run':RUN,'artifact':meta['id'],'artifact_sha256':digest,'passed_suites':suites,
      'changed_paths':paths,'scope':'Only launcher assets/round reference differ in application; fresh launcher rendering and both synthetic upgrades required.'}
    output=Path('app/build/production-evidence/test-provenance.json'); output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(evidence,indent=2)+'\n');print(output.read_text())

if __name__=='__main__': main()

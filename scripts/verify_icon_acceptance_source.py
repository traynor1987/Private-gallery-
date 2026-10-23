#!/usr/bin/env python3
"""Reuse passing 1.7.1 cases only for an allowlisted launcher-resource-only delta.

Fresh launcher rendering, debug upgrade and original-key production upgrade are
mandatory in the caller. The failed full run remains FAILED; its sole Tor case must pass a strict fresh rerun.
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
ARTIFACT=12532217693
DIGEST='15fad51ca11679a342dc2c1aa07ed4a50fce9c8d9abe5d474db286a48c400f70'
TOR_CLASS='uk.co.traynor.privategallery.core.browser.hidden.HiddenTorBootstrapTest'
TOR_METHOD='packagedTorBootstrapsInPrivateProcessAndStopsWhenReleased'
REPO='traynor1987/Private-gallery-'
ALLOWED={
 'V1_7_1_RECOVERY.md', 'docs/release171/REVIEW.md', '.github/workflows/launcher-acceptance.yml', '.github/workflows/android.yml',
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
    if (run.get('id')!=RUN or run.get('head_sha')!=SOURCE or run.get('conclusion')!='failure'
        or run.get('status')!='completed' or run.get('event')!='push'
        or run.get('head_branch')!='release/1.7.1-professional-ui'
        or run.get('path')!='.github/workflows/android.yml'
        or run.get('repository',{}).get('full_name')!=REPO):
        raise ValueError('Expected exact archived failed 1.7.1 run/source')
    if (metadata.get('id')!=ARTIFACT or metadata.get('digest')!='sha256:'+DIGEST or metadata.get('workflow_run',{}).get('id')!=RUN or
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

def check_full_cases(cases):
    failed=[c for c in cases if c.find('failure') is not None]
    if (len(cases)!=316 or len(failed)!=1 or
        any(c.find(t) is not None for c in cases for t in ('error','skipped'))):
        raise ValueError('Archived full suite must have exactly 315 passes and one known Tor failure')
    c=failed[0]
    if (c.get('classname')!=TOR_CLASS or c.get('name')!=TOR_METHOD or
        'onion-probe must be an actual upstream response, not synthetic success expected null, but was:<transport>' not in ''.join(c.find('failure').itertext())):
        raise ValueError('Unexpected full-suite failure; release blocked')
    return {'passed':315,'failed':1,'failed_case':TOR_CLASS+'#'+TOR_METHOD}

def check_fresh_cases(cases):
    expected={(TOR_CLASS,TOR_METHOD),('uk.co.traynor.privategallery.ui.LauncherBrandingTest','packagedAdaptiveRoundAndThemedIconsRender')}
    if (len(cases)!=2 or {(c.get('classname'),c.get('name')) for c in cases}!=expected or
        any(c.find(t) is not None for c in cases for t in ('failure','error','skipped'))):
        raise ValueError('Fresh strict Tor rerun and launcher rendering must both pass')
    return 2

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
          ('acceptance-diagnostics/professional-ui/results/',31))}
        cases=[c for n in z.namelist() if n.startswith('outputs/androidTest-results/connected/debug/') and Path(n).name.startswith('TEST-') and n.endswith('.xml') for c in ET.fromstring(z.read(n)).iter('testcase')]
        full=check_full_cases(cases)
        for name in ('professional-ui/source-sha.txt',):
            if z.read('acceptance-diagnostics/'+name).decode().strip()!=SOURCE:
                raise ValueError('Archived full/focused source mismatch')
    fresh=[c for p in Path('app/build/outputs/androidTest-results/connected/debug').rglob('TEST-*.xml') for c in ET.parse(p).getroot().iter('testcase')]
    check_fresh_cases(fresh)
    if Path('app/build/production-evidence/launcher/source-sha.txt').read_text().strip()!=git('rev-parse','HEAD'):
        raise ValueError('Fresh launcher/Tor source mismatch')
    evidence={'candidate_source':git('rev-parse','HEAD'),'tested_application_source':SOURCE,
      'full_run':RUN,'artifact':meta['id'],'artifact_sha256':digest,'passed_suites':suites,'archived_full_run_status':'FAILED','archived_full_results':full,'fresh_launcher_and_strict_tor_passed':2,
      'changed_paths':paths,'scope':'315 full regression cases passed; the sole failed Tor case passed on a strict isolated rerun. Full run #666 remains FAILED. Only launcher assets/round reference differ in application. Fresh upgrades required. Isolated Tor success does not exclude suite-order or external network variability.'}
    output=Path('app/build/production-evidence/test-provenance.json'); output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(evidence,indent=2)+'\n');print(output.read_text())

if __name__=='__main__': main()

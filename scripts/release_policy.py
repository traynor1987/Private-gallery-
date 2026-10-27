#!/usr/bin/env python3
"""Risk classification and exact-candidate, independently reviewed patch admission.

Historical failure remains failure. This verifier never edits tests or their outcomes.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SCOPE_PATH = 'docs/release1761/risk-based/approved-scope.json'
APPROVAL_PATH = 'docs/release1761/risk-based/implementation-approval.json'
PINNED_PATHS = {'app','gradle','build.gradle.kts','gradle.properties','settings.gradle.kts','gradlew','gradlew.bat','scripts/upgrade','scripts/release_configuration.sh','scripts/verify_synthetic_upgrade.sh','scripts/verify_permanent_signer.sh','scripts/verify_production_candidate.sh','scripts/verify_production_vpn_path.sh'}

REVIEWED_PATHS = {
    'scripts/release_policy.py','scripts/run_patch1761.sh','scripts/verify_patch_production1761.py',
    'scripts/tests/test_risk_release_policy.py','scripts/tests/test_patch1761_evidence.py',
    '.github/workflows/fast-development.yml','.github/workflows/patch1761.yml',
    '.github/workflows/acceptance1761.yml','.github/workflows/periodic-security.yml',
    'docs/release-policy/POLICY.md','docs/release1761/risk-based/SECURITY_REVIEW.md',
}

REVIEW_BASE = '4c080ffc9bd6483bc6c706fa277b528e023b8c23'
DOCUMENT_PATHS = {
    'V1_7_6_1_RECOVERY.md','docs/release1761/FINAL_HANDOFF.md',
    'docs/superpowers/plans/2026-10-27-risk-based-release-policy.md',
}

def verify_changed_paths(paths):
    allowed = REVIEWED_PATHS | DOCUMENT_PATHS | {SCOPE_PATH,APPROVAL_PATH,'.patch1761-candidate'}
    documentation_suffixes = {'.md','.json','.xml','.txt','.properties'}
    unknown = [p for p in paths if p not in allowed and not (
        p.startswith('docs/release1761/risk-based/') and Path(p).suffix in documentation_suffixes)]
    if unknown:
        raise ValueError('Unreviewed changed input; full lane/renewed review required: '+', '.join(sorted(unknown)))
    return sorted(paths)

def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def classify(paths, version):
    parts = version.split('.')
    if not all(p.isdigit() for p in parts):
        raise ValueError('Unrecognised release version')
    category = 'major' if len(parts)==2 and parts[1]=='0' else 'patch' if len(parts)==4 else 'feature'
    critical = [p for p in paths if not (p.startswith('docs/') or p.endswith('.md'))]
    return {'category':category,'lane':'patch_review_required' if category=='patch' and not critical else 'full','changed_paths':sorted(paths),'reason':'An exact independent impact approval is required for patch admission; unknown/critical impact defaults to full.'}

def native_cases(directory):
    result = {}
    reports = sorted(Path(directory).rglob('TEST-*.xml'))
    if not reports:
        raise ValueError('Missing native XML')
    for report in reports:
        root = ET.parse(report).getroot()
        cases = list(root.iter('testcase'))
        if 'tests' in root.attrib and int(root.get('tests')) != len(cases):
            raise ValueError('Native declared count mismatch')
        for case in cases:
            name = case.get('classname','')+'#'+case.get('name','')
            if name in result:
                raise ValueError('Duplicate native case: '+name)
            result[name] = case
    return result

def verify_native(directory, required):
    cases = native_cases(directory)
    if set(cases) != set(required) or len(cases) != len(required):
        raise ValueError('Missing/substituted fresh method')
    if any(case.find(outcome) is not None for case in cases.values() for outcome in ('failure','error','skipped')):
        raise ValueError('Non-passing fresh method')
    return sorted(cases)

def verify_scope(scope, root=ROOT):
    root = Path(root)
    if scope.get('decision') != 'GO_FOR_PATCH_CRITERIA_ONLY' or scope.get('review_id') != 'PG-1761-RISK-20261027':
        raise ValueError('Independent patch criteria approval absent')
    if scope.get('release') != '1.7.6.1' or scope.get('version_code') != 42 or scope.get('supported_api') != 37:
        raise ValueError('Wrong reviewed release/platform')
    if set(scope['frozen_inputs']) != PINNED_PATHS:
        raise ValueError('Protected input omitted from review')
    for path, expected in scope['frozen_inputs'].items():
        actual = subprocess.check_output(['git','rev-parse','HEAD:'+path],cwd=root,text=True).strip()
        if actual != expected:
            raise ValueError('Protected input changed; renewed review/full lane required: '+path)
    xml = root / scope['full_xml_path']
    if digest(xml) != scope['full_xml_sha256']:
        raise ValueError('Historical native evidence digest mismatch')
    cases = {c.get('classname')+'#'+c.get('name'):c for c in ET.parse(xml).getroot().iter('testcase')}
    failed = sorted(n for n,c in cases.items() if c.find('failure') is not None or c.find('error') is not None)
    passed = sorted(n for n,c in cases.items() if not any(c.find(x) is not None for x in ('failure','error','skipped')))
    if len(cases)!=436 or len(passed)!=435 or failed!=scope['historical_failed'] or len(failed)!=1 or scope['historical_full_verdict']!='FAILED':
        raise ValueError('Historical failure must remain FAILED435/436')
    required = scope['required_methods']
    selected = sorted(n for n,c in cases.items() if c.get('classname') in scope['selectors'] or n in scope['selectors'])
    if len(required)!=152 or len(set(required))!=152 or sorted(required)!=selected or not set(required)<=set(passed):
        raise ValueError('Fresh reviewed152-method scope changed')
    return {'review_id':scope['review_id'],'fresh_method_count':152,'historical_passed_methods':passed,'historical_failed_methods':failed,'historical_full_verdict':'FAILED','intentionally_not_executed_in_patch':sorted(set(cases)-set(required))}

def load_approved(root=ROOT):
    root = Path(root)
    approval = json.loads((root/APPROVAL_PATH).read_text())
    if approval.get('decision')!='GO_FOR_ONE_PATCH_ADMISSION' or approval.get('reviewer')!='/root/risk_policy_security_review':
        raise ValueError('Actual implementation independent approval absent')
    if digest(root/SCOPE_PATH)!=approval['scope_sha256']:
        raise ValueError('Reviewed scope content changed')
    if set(approval.get('reviewed_files',{})) != REVIEWED_PATHS:
        raise ValueError('Incomplete independent implementation review')
    for path, expected in approval['reviewed_files'].items():
        if digest(root/path)!=expected:
            raise ValueError('Reviewed policy implementation changed: '+path)
    changed = subprocess.check_output(['git','diff','--name-only','--no-renames',REVIEW_BASE,'HEAD'],cwd=root,text=True).splitlines()
    verify_changed_paths(changed)
    scope = json.loads((root/SCOPE_PATH).read_text())
    return scope, verify_scope(scope,root)

def verify_evidence(evidence, source, apk, root=ROOT):
    from verify_release175_evidence import count_reports
    from verify_android17_media_environment import validate
    root = Path(root); evidence = Path(evidence)
    scope, result = load_approved(root)
    for name,wanted in [('source-sha.txt',source),('sdk.txt','37'),('android-release.txt','17'),('command-exit-status.txt','0')]:
        if (evidence/name).read_text().strip()!=wanted:
            raise ValueError('Fresh patch evidence mismatch: '+name)
    if (evidence/'apk-sha256.txt').read_text().split()[0] != digest(apk):
        raise ValueError('Actual tested APK mismatch')
    for phase in ('before','after'):
        validate(json.loads((evidence/('media-'+phase+'.json')).read_text()),source)
    executed = verify_native(evidence/'native',scope['required_methods'])
    count_reports(root/'app/build/test-results/testDebugUnitTest',354)
    for phase in ('seed','verify'):
        if 'OK (1 test)' not in (evidence/('browser-process-'+phase+'.txt')).read_text():
            raise ValueError('Missing real process isolation fixture')
    for phase in ('seed','verify','after-restart'):
        if 'OK (1 test)' not in (root/'app/build/acceptance-diagnostics'/('upgrade-'+phase+'.txt')).read_text():
            raise ValueError('Missing debug upgrade/restart: '+phase)
    if 'PASS:' not in (root/'app/build/acceptance-diagnostics/upgrade-result.txt').read_text():
        raise ValueError('Debug upgrade not verified')
    if (root/'app/build/acceptance-diagnostics/upgrade-candidate-source-sha.txt').read_text().strip()!=source:
        raise ValueError('Debug upgrade source mismatch')
    result.update({'source_sha':source,'tested_debug_apk_sha256':digest(apk),'fresh_executed_methods':executed,'fresh_result':'PASS','debug_upgrade_restart':'PASS','production_signing':'NOT_YET_VERIFIED','owner_acceptance':'NOT_PERFORMED'})
    return result

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action',choices=['verify','selectors','evidence','classify'])
    parser.add_argument('--output')
    parser.add_argument('--source')
    parser.add_argument('--apk')
    parser.add_argument('--evidence')
    parser.add_argument('--baseline',default='HEAD~1')
    parser.add_argument('--version',default='1.7.6.1')
    args = parser.parse_args()
    if args.action=='classify':
        paths=subprocess.check_output(['git','diff','--name-only',args.baseline,'HEAD'],cwd=ROOT,text=True).splitlines()
        result=classify(paths,args.version)
    elif args.action=='evidence':
        result=verify_evidence(args.evidence,args.source,args.apk)
    else:
        scope,result=load_approved()
        if args.action=='selectors':
            print(','.join(scope['selectors']));return
    text=json.dumps(result,indent=2)+'\n'
    if args.output:
        out=Path(args.output);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(text)
    else:print(text,end='')

if __name__=='__main__':
    main()

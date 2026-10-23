#!/usr/bin/env python3
"""Fully passing pinned tests for an identical application, never a failed-test waiver."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
SOURCE='392c4e91e72274c3f191fc7382e241e23bea266d'
APP_TREE='c72c089db7aed6a3e2807e7dfc840dd92a129996'
RUN=40027924620
ARTIFACT=12535771695
DIGEST='71c6815d290c642c2589119d1e5ee87d8dd615db89c70320841fec0483537352'
REPO='traynor1987/Private-gallery-'
ALLOWED={'V1_7_1_RECOVERY.md','docs/release171/REVIEW.md','.github/workflows/android.yml','.github/workflows/launcher-acceptance.yml','scripts/verify_icon_acceptance_source.py','scripts/tests/test_icon_acceptance_source.py','scripts/complete_android_regression.sh','scripts/tests/test_emulator_script_lines.py'}

def check_paths(paths):
 for path in paths:
  if path not in ALLOWED:raise ValueError('Untested source change: '+path)

def verify_run(run,meta):
 # The run failed AFTER all tests passed, at the independently verified split-if
 # shell error. Its failed status is retained; no failed testcase is permitted.
 if (run.get('id')!=RUN or run.get('head_sha')!=SOURCE or run.get('status')!='completed' or run.get('conclusion')!='failure' or run.get('event')!='push' or run.get('head_branch')!='release/1.7.1-professional-ui' or run.get('path')!='.github/workflows/android.yml' or run.get('repository',{}).get('full_name')!=REPO):raise ValueError('Archived run/source mismatch')
 if (meta.get('id')!=ARTIFACT or meta.get('digest')!='sha256:'+DIGEST or meta.get('expired') or meta.get('workflow_run',{}).get('id')!=RUN or meta.get('workflow_run',{}).get('head_sha')!=SOURCE):raise ValueError('Pinned artifact provenance mismatch')

def check_suite(z,prefix,count):
 cases=[c for n in z.namelist() if n.startswith(prefix) and Path(n).name.startswith('TEST-') and n.endswith('.xml') for c in ET.fromstring(z.read(n)).iter('testcase')]
 if len(cases)!=count or any(c.find(t) is not None for c in cases for t in ('failure','error','skipped')):raise ValueError(f'{prefix}: requires exactly {count} passes, no failures/errors/skips')
 return count

def git(*args):return subprocess.check_output(['git',*args],text=True).strip()
def main():
 assert os.environ.get('GITHUB_REPOSITORY')==REPO and os.environ.get('GITHUB_REF')=='refs/heads/release/1.7.1-professional-ui' and os.environ.get('GITHUB_EVENT_NAME')=='push'
 run=json.loads(Path(sys.argv[1]).read_text());meta=json.loads(Path(sys.argv[2]).read_text());verify_run(run,meta)
 with open(sys.argv[3],'rb') as file:digest=hashlib.file_digest(file,'sha256').hexdigest()
 if digest!=DIGEST:raise ValueError('Pinned ZIP SHA256 mismatch')
 git('merge-base','--is-ancestor',SOURCE,'HEAD')
 if git('rev-parse',SOURCE+':app')!=APP_TREE or git('rev-parse','HEAD:app')!=APP_TREE:raise ValueError('Application/build/test/resources differ from fully passing tests')
 paths=git('diff','--name-only',SOURCE,'HEAD').splitlines();check_paths(paths)
 with zipfile.ZipFile(sys.argv[3]) as z:
  counts={prefix:check_suite(z,prefix,count) for prefix,count in [('test-results/testDebugUnitTest/',333),('acceptance-diagnostics/professional-ui/results/',31),('acceptance-diagnostics/hidden-browser-preflight/',70),('outputs/androidTest-results/connected/debug/',317)]}
  if z.read('acceptance-diagnostics/professional-ui/source-sha.txt').decode().strip()!=SOURCE:raise ValueError('Focused source provenance mismatch')
 evidence={'candidate_source':git('rev-parse','HEAD'),'fully_tested_application_source':SOURCE,'identical_app_tree':APP_TREE,'test_run':RUN,'archived_workflow_status':'FAILED after 317/317 tests passed: emulator action split multiline if command','artifact':ARTIFACT,'artifact_sha256':DIGEST,'passed_suites':counts,'changed_paths':paths,'scope':'All tests passed for identical application/build/test/resources. Fresh launcher test, debug upgrade, original-key release signing and production upgrade/restart remain mandatory.'}
 out=Path('app/build/production-evidence/test-provenance.json');out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(evidence,indent=2)+'\n');print(out.read_text())
if __name__=='__main__':main()

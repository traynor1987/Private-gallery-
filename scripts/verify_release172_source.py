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
SOURCE='c9f7e0712614d7af1d301d74ad6abe9d1a912225'
APP_TREE='e33ae81c3ebde2736583c18ef39ff69d69470927'
RUN=40042516847
ARTIFACT=12541501894
DIGEST='d08f40fc7434bbc7b4cbe778a38c5642a4d1c264c4d2c4c929e753dc18fe8b9a'
REPO='traynor1987/Private-gallery-'
ALLOWED={'V1_7_2_RECOVERY.md','docs/release172/REVIEW.md','.github/workflows/android.yml','.github/workflows/release172-acceptance.yml','scripts/verify_release172_source.py','scripts/tests/test_release172_source.py','scripts/tests/test_emulator_script_lines.py','scripts/upgrade/UpgradeFixtureTest.kt'}
FIXTURE='scripts/upgrade/UpgradeFixtureTest.kt'
OLD_GUARD='(baselineCode == 33 && candidateCode == 34))'
NEW_GUARD='(baselineCode == 33 && candidateCode == 34) || (baselineCode == 34 && candidateCode == 35))'
def check_fixture(original,current):
 if original.count(OLD_GUARD)!=1 or current!=original.replace(OLD_GUARD,NEW_GUARD):raise ValueError('Upgrade fixture may only add the approved code34 to35 pair')


def check_paths(paths):
 for path in paths:
  if path not in ALLOWED:raise ValueError('Untested source change: '+path)

def verify_run(run,meta):
 # The run failed AFTER all tests passed, at the upgrade fixture version-pair guard.
 # Fresh corrected upgrades remain mandatory; no failed app testcase is permitted.
 if (run.get('id')!=RUN or run.get('head_sha')!=SOURCE or run.get('status')!='completed' or run.get('conclusion')!='failure' or run.get('event')!='push' or run.get('head_branch')!='release/1.7.2-camera-ai-refinements' or run.get('path')!='.github/workflows/android.yml' or run.get('repository',{}).get('full_name')!=REPO):raise ValueError('Archived run/source mismatch')
 if (meta.get('id')!=ARTIFACT or meta.get('digest')!='sha256:'+DIGEST or meta.get('expired') or meta.get('workflow_run',{}).get('id')!=RUN or meta.get('workflow_run',{}).get('head_sha')!=SOURCE):raise ValueError('Pinned artifact provenance mismatch')

def check_suite(z,prefix,count):
 cases=[c for n in z.namelist() if n.startswith(prefix) and Path(n).name.startswith('TEST-') and n.endswith('.xml') for c in ET.fromstring(z.read(n)).iter('testcase')]
 if len(cases)!=count or any(c.find(t) is not None for c in cases for t in ('failure','error','skipped')):raise ValueError(f'{prefix}: requires exactly {count} passes, no failures/errors/skips')
 return count

def git(*args):return subprocess.check_output(['git',*args],text=True).strip()
def main():
 assert os.environ.get('GITHUB_REPOSITORY')==REPO and os.environ.get('GITHUB_REF')=='refs/heads/release/1.7.2-camera-ai-refinements' and os.environ.get('GITHUB_EVENT_NAME')=='push'
 run=json.loads(Path(sys.argv[1]).read_text());meta=json.loads(Path(sys.argv[2]).read_text());verify_run(run,meta)
 with open(sys.argv[3],'rb') as file:digest=hashlib.file_digest(file,'sha256').hexdigest()
 if digest!=DIGEST:raise ValueError('Pinned ZIP SHA256 mismatch')
 git('merge-base','--is-ancestor',SOURCE,'HEAD')
 if git('rev-parse',SOURCE+':app')!=APP_TREE or git('rev-parse','HEAD:app')!=APP_TREE:raise ValueError('Application/build/test/resources differ from fully passing tests')
 paths=git('diff','--name-only',SOURCE,'HEAD').splitlines();check_paths(paths)
 check_fixture(git('show',SOURCE+':'+FIXTURE),git('show','HEAD:'+FIXTURE))
 with zipfile.ZipFile(sys.argv[3]) as z:
  counts={prefix:check_suite(z,prefix,count) for prefix,count in [('test-results/testDebugUnitTest/',336),('acceptance-diagnostics/professional-ui/results/',44),('acceptance-diagnostics/hidden-browser-preflight/',73),('outputs/androidTest-results/connected/debug/',329)]}
  for name in ('process-seed.txt','process-verify.txt','camera-process-seed.txt','camera-process-verify.txt'):
   if 'OK (1 test)' not in z.read('acceptance-diagnostics/hidden-browser-preflight/'+name).decode():raise ValueError('Independent process restart proof missing: '+name)
  for entry in ('acceptance-diagnostics/professional-ui/source-sha.txt','acceptance-diagnostics/full-regression-source-sha.txt'):
   if z.read(entry).decode().strip()!=SOURCE:raise ValueError('Test source provenance mismatch: '+entry)
 evidence={'candidate_source':git('rev-parse','HEAD'),'fully_tested_application_source':SOURCE,'identical_app_tree':APP_TREE,'test_run':RUN,'archived_workflow_status':'FAILED after all329 app tests passed: upgrade fixture rejected approved34 to35 pair','artifact':ARTIFACT,'artifact_sha256':DIGEST,'passed_suites':counts,'changed_paths':paths,'scope':'All tests passed for identical application/build/test/resources. Fresh launcher test, corrected debug upgrade, original-key release signing and corrected production upgrade/restart remain mandatory.'}
 out=Path('app/build/production-evidence/test-provenance.json');out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(evidence,indent=2)+'\n');print(out.read_text())
if __name__=='__main__':main()

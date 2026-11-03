#!/usr/bin/env python3
"""Pinned18-case evidence reuse; every non-allowlisted Git blob must be unchanged."""
import argparse
import contextlib
import hashlib
import io
import json
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from verify_hidden_vpn_fixture1762 import verify_fixture

BASELINE_SOURCE='535186021598bf64fd56f2132ab4cb763332bf17'
BASELINE_RUN=41682334113
BASELINE_ARTIFACT=13253433530
BASELINE_SHA256='87f9a4a7b829346a0ad93a89c1996016868f9d193d1b8828b4673074a09094b6'
ALLOW={
 'app/src/androidTest/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenContinuityFixtureClient.kt',
 'app/src/androidTest/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenBrowserContinuity17103Test.kt',
 'app/src/androidTest/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenMainRuntimeContinuity17103Test.kt',
 'scripts/hidden_continuity_fixture17103.py',
 'scripts/tests/test_hidden_continuity_fixture17103.py',
 'scripts/tests/test_hidden_continuity_transition_fixture17103.py',
 'scripts/tests/test_hidden_continuity_document17103.py',
 'scripts/tests/test_hidden_continuity_reuse17103.py',
 'scripts/tests/test_nova17103_runtime_gate.py',
 'scripts/reuse_hidden_continuity17103.py',
 'scripts/run_nova17102.sh',
 'scripts/verify_nova17103_runtime.py',
 '.github/workflows/fast-development.yml',
 'V1_7_10_RECOVERY.md',
 'docs/release17103/STATUS.md',
 'docs/release17103/FAILED_RUN_DIAGNOSIS.md',
}
MANIFEST='nova17102/reuse/compatibility.json'
BASELINE_ZIP='nova17102/reuse/baseline.zip'

def sha(data):return hashlib.sha256(data).hexdigest()
def git(repo,*args):return subprocess.check_output(['git','-C',str(repo),*args])
def allowed(path):return path in ALLOW
def tree(repo,source):
 entries={}
 for entry in git(repo,'ls-tree','-r','-z','--full-tree',source).split(b'\0'):
  if entry:
   metadata,path=entry.split(b'\t',1);entries[path.decode()]=metadata.decode()
 return entries

def compatibility(repo,source):
 if not isinstance(source,str) or not re.fullmatch('[a-f0-9]{40}',source):raise ValueError('Invalid candidate source')
 if git(repo,'rev-parse',source+'^{commit}').decode().strip()!=source:raise ValueError('Candidate is not exact commit')
 if subprocess.run(['git','-C',str(repo),'merge-base','--is-ancestor',BASELINE_SOURCE,source],capture_output=True).returncode:raise ValueError('Baseline is not candidate ancestor')
 before,after=tree(repo,BASELINE_SOURCE),tree(repo,source)
 changed=sorted(path for path in before.keys()|after.keys() if before.get(path)!=after.get(path))
 forbidden=[path for path in changed if not allowed(path)]
 if forbidden:raise ValueError('Reuse incompatible: protected paths changed: '+', '.join(forbidden))
 protected={path:value for path,value in before.items() if not allowed(path)}
 if protected!={path:value for path,value in after.items() if not allowed(path)}:raise ValueError('Protected tree differs')
 return dict(protected_tree_sha256=sha(json.dumps(protected,sort_keys=True,separators=(',',':')).encode()),
             changed=[dict(path=path,baseline=before.get(path),candidate=after.get(path)) for path in changed])

def identities(matrix):
 result=[]
 for cls,methods in matrix.items():
  if not isinstance(cls,str) or not isinstance(methods,list) or any(not isinstance(method,str) for method in methods):raise ValueError('Invalid case matrix')
  result.extend((cls,method) for method in methods)
 if len(result)!=len(set(result)):raise ValueError('Duplicate selected case')
 return set(result)
def matrices(repo,source):
 def read(commit,path):return identities(json.loads(git(repo,'show',commit+':'+path)))
 ui=read(BASELINE_SOURCE,'docs/release17103/continuity-cases.json')
 routes=read(BASELINE_SOURCE,'docs/release17102/route-cases.json')
 fresh=read(BASELINE_SOURCE,'docs/release17103/continuity-route-cases.json')-routes
 if len(ui)!=15 or len(routes)!=3 or len(fresh)!=8 or ui&routes or fresh&(ui|routes):raise ValueError('Pinned case partition differs')
 expected=ui|routes|fresh
 if read(source,'docs/release17103/continuity-release-cases.json')!=expected:raise ValueError('Candidate26-case aggregate matrix differs')
 if read(source,'docs/release17103/continuity-cases.json')!=ui or read(source,'docs/release17103/continuity-route-cases.json')!=routes|fresh:raise ValueError('Candidate phase matrices differ')
 return ui,routes,fresh

def baseline(raw,ui,routes):
 if sha(raw)!=BASELINE_SHA256:raise ValueError('Pinned baseline archive SHA256 differs')
 derived={}
 with zipfile.ZipFile(io.BytesIO(raw)) as z:
  names=z.namelist()
  if len(names)!=len(set(names)):raise ValueError('Duplicate baseline archive member')
  def read(name):
   if name not in names:raise ValueError('Missing baseline proof '+name)
   return z.read(name)
  if read('nova17102/source-sha.txt').decode().strip()!=BASELINE_SOURCE:raise ValueError('Baseline source differs')
  for phase,expected in [('ui',ui),('base-route',routes)]:
   prefix='nova17102/'+phase+'/outputs/androidTest-results/'
   xmls=[name for name in names if name.startswith(prefix) and name.endswith('.xml')]
   seen=[]
   for name in xmls:
    data=read(name)
    for case in ET.fromstring(data).iter('testcase'):
     if any(case.find(tag) is not None for tag in ('failure','error','skipped')):raise ValueError('Nonpassing reused case')
     seen.append((case.get('classname'),case.get('name')))
    derived['nova17102/reused/'+name.removeprefix('nova17102/')]=data
   if len(seen)!=len(expected) or set(seen)!=expected:raise ValueError('Missing/extra/duplicate original reused cases '+phase)
  for name in ['nova17102/ui-accounting.txt','nova17102/baseline-route-accounting.txt','nova17102/tor-route-result.txt','nova17102/baseline-vpn/result.txt','hidden-vpn1762/result.txt']:
   data=read(name)
   if not data.decode().startswith('PASS:'):raise ValueError('Missing passing baseline proof '+name)
   derived[name]=data
  for name in ['nova17102/ui-original-exit-status.txt','nova17102/baseline-route-original-exit-status.txt']:
   if read(name).decode().strip()!='0':raise ValueError('Baseline original process exit was nonzero')
   derived[name]=read(name)
  snapshot='nova17102/baseline-vpn/'
  for name in names:
   if name.startswith(snapshot) and not name.endswith('/'):
    if '..' in Path(name).parts:raise ValueError('Unsafe snapshot path')
    derived[name]=read(name)
  # Reexecute every original VPN/body/revocation/packet assertion on its exact snapshot.
  with tempfile.TemporaryDirectory() as folder:
   root=Path(folder)
   for name,data in derived.items():
    if name.startswith(snapshot):
     target=root/name.removeprefix(snapshot);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
   with contextlib.redirect_stdout(io.StringIO()):verify_fixture(root)
   if (root/'result.txt').read_bytes()!=read(snapshot+'result.txt'):raise ValueError('Original route verdict differs on revalidation')
  for filename in ('native-tor1762-result.json','vpn-native-revocation1762-result.json','vpn-network-loss1762-result.json'):
   originals=[name for name in names if name.startswith('nova17102/base-route/outputs/connected_android_test_additional_output/') and name.endswith('/'+filename)]
   if len(originals)!=1 or read(originals[0])!=read(snapshot+filename):raise ValueError('Original native proof copy differs '+filename)
  tor=json.loads(read(snapshot+'native-tor1762-result.json'))
  if tor.get('mode')!='TOR_ONLY' or tor.get('javascript') is not False or any(tor.get(key) is not True for key in ('form','multipart','download_verified','discard_v4','discard_v6','discard_dns_udp','session_revoked','tor_process_death')):raise ValueError('Missing original native Tor proof')
  if not read('nova17102/tor-route-result.txt').decode().startswith('PASS: isolated packaged-Tor native forms/uploads/verified GET media'):raise ValueError('Wrong original Tor validator proof')
 return derived

def plan(raw,repo,source):
 compatible=compatibility(repo,source)
 ui,routes,fresh=matrices(repo,source)
 derived=baseline(raw,ui,routes)
 manifest=dict(schema=1,mode='18_reused_8_fresh',source=source,
  baseline=dict(source=BASELINE_SOURCE,run=BASELINE_RUN,artifact=BASELINE_ARTIFACT,archive_sha256=BASELINE_SHA256),
  reused=[list(pair) for pair in sorted(ui|routes)],fresh=[list(pair) for pair in sorted(fresh)],compatibility=compatible,
  derived_sha256={name:sha(data) for name,data in sorted(derived.items())},
  tor_evidence='Pinned original passing Android case, native receipt and generated Tor-validator PASS; missing original onion events are not reexecuted')
 return manifest,derived

def verify_reuse(raw,manifest,read,repo,source):
 expected,derived=plan(raw,repo,source)
 if manifest!=expected:raise ValueError('Reuse compatibility manifest differs from frozen source/baseline')
 for name,data in derived.items():
  if read(name)!=data:raise ValueError('Derived original evidence differs '+name)
 return expected

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--baseline',type=Path,required=True);parser.add_argument('--repo',type=Path,default=Path('.'));parser.add_argument('--source',required=True);parser.add_argument('--prepare',type=Path,required=True);args=parser.parse_args()
 raw=args.baseline.read_bytes();manifest,derived=plan(raw,args.repo,args.source)
 for name,data in derived.items():
  target=args.prepare/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
 for name,data in [(BASELINE_ZIP,raw),(MANIFEST,(json.dumps(manifest,sort_keys=True,indent=2)+'\n').encode())]:
  target=args.prepare/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
 print('PASS:18 original passing cases reused under exact protected-tree compatibility;8 continuity cases still require fresh execution')
if __name__=='__main__':main()

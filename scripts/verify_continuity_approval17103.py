#!/usr/bin/env python3
"""Trusted-owner review binding, not cryptographic proof of reviewer identity.

The controller copies a fresh independent review verbatim. Its authoritative JSON
record, canonical report headers and runtime/control hashes must agree. No signer
is available to this step. Activation may alter only the explicit review/receipt
allowlist; application, workflows, verifiers and imported helpers stay frozen.
"""
import argparse,hashlib,json,re,subprocess
from pathlib import Path
MARKER='.acceptance17103-candidate'
APPROVAL='docs/release17103/CONTINUITY_APPROVAL.json'
REVIEW='docs/release17103/CONTINUITY_SECURITY_REVIEW.md'
ALLOWED={MARKER,APPROVAL,REVIEW,'V1_7_10_RECOVERY.md','docs/release17103/STATUS.md','docs/release17103/COORDINATED_IMPLEMENTATION.md','docs/release17103/RUNTIME_EVIDENCE.json'}
def require(value,message):
 if not value:raise ValueError(message)
def strict_json(raw):
 def pairs(items):
  result={}
  for key,value in items:
   require(key not in result,'Duplicate decision field '+key);result[key]=value
  return result
 return json.loads(raw,object_pairs_hook=pairs)
def sha(raw):return hashlib.sha256(raw).hexdigest()
def validate_record(marker,approval,raw_approval,review,fixture,matrix):
 require(type(marker)is dict and set(marker)=={'source','test_run','review_sha256','approval_sha256','fixture_sha256','matrix_sha256','artifact_sha256'},'Invalid marker schema')
 require(type(approval)is dict and set(approval)=={'schema','decision','source','reviewer','test_run','review_sha256','fixture_sha256','matrix_sha256','artifact_sha256','scope','unresolved_critical','unresolved_important'},'Invalid authoritative approval schema')
 require(type(approval['schema'])is int and approval['schema']==1,'Invalid approval schema version')
 require(approval['decision']=='APPROVED','Independent decision is not approved')
 require(approval['reviewer']=='/root/continuity_security_review','Wrong independent reviewer role')
 require(approval['scope']=='VPN_ONLY_CONTINUITY_WITH_EXISTING_TOR_RETIREMENT','Unreviewed release scope')
 for field in ['unresolved_critical','unresolved_important']:
  require(type(approval[field])is int and approval[field]==0,'Unresolved mandatory review findings')
 require(type(marker['test_run'])is int and 0<marker['test_run']<2**53,'Invalid runtime run')
 require(type(marker['source'])is str and re.fullmatch('[a-f0-9]{40}',marker['source']) is not None and marker['source']!='0'*40,'Invalid frozen source')
 for field in ['source','test_run','review_sha256','fixture_sha256','matrix_sha256','artifact_sha256']:require(approval[field]==marker[field],'Approval/marker disagreement: '+field)
 for field in ['review_sha256','approval_sha256','fixture_sha256','matrix_sha256','artifact_sha256']:
  require(type(marker[field])is str and re.fullmatch('[a-f0-9]{64}',marker[field]) is not None and marker[field]!='0'*64,'Invalid bound digest '+field)
 require(strict_json(raw_approval)==approval,'Parsed approval differs from bound bytes')
 require(sha(raw_approval)==marker['approval_sha256'],'Approval document differs')
 require(sha(review)==marker['review_sha256'],'Independent report differs')
 require(sha(fixture)==marker['fixture_sha256'] and sha(matrix)==marker['matrix_sha256'],'Fixture/matrix differs')
 text=review.decode('utf-8')
 for name,value in [('Source',approval['source']),('Decision','APPROVED'),('Reviewer',approval['reviewer'])]:
  headers=re.findall(r'^'+name+r': ([^\r\n]+)$',text,re.M)
  require(headers==[value],'Missing/conflicting canonical report header '+name)
 return approval

def validate_activation_delta(root,source,head):
 root=Path(root)
 result=subprocess.run(['git','-C',str(root),'merge-base','--is-ancestor',source,head],capture_output=True)
 require(result.returncode==0,'Frozen source is not an ancestor of activation')
 raw=subprocess.check_output(['git','-C',str(root),'diff','--name-only','-z',source,head,'--'])
 changed={path.decode('utf-8')for path in raw.split(b'\0')if path}
 require(changed<=ALLOWED,'Unreviewed activation changes: '+str(sorted(changed-ALLOWED)))
 return changed

def main():
 p=argparse.ArgumentParser();p.add_argument('--root',type=Path,required=True);p.add_argument('--head',required=True);a=p.parse_args();root=a.root
 require(re.fullmatch('[a-f0-9]{40}',a.head) is not None,'Invalid activation SHA')
 marker=strict_json((root/MARKER).read_bytes());raw=(root/APPROVAL).read_bytes();approval=strict_json(raw)
 validate_record(marker,approval,raw,(root/REVIEW).read_bytes(),(root/'docs/release17103/UpgradeFixtureTest.kt').read_bytes(),(root/'docs/release17103/continuity-release-cases.json').read_bytes())
 validate_activation_delta(root,marker['source'],a.head)
 print('PASS: exact authoritative independent review, runtime/fixture/matrix hashes and frozen activation controls; no signing/upgrade claim')
if __name__=='__main__':main()

import hashlib,json,tempfile,subprocess,unittest
from pathlib import Path
from scripts.verify_continuity_approval17103 import strict_json,validate_record,validate_activation_delta
class ApprovalGate(unittest.TestCase):
 def inputs(self):
  h=lambda b:hashlib.sha256(b).hexdigest();review=('Source: '+'1'*40+'\nDecision: APPROVED\nReviewer: /root/continuity_security_review\n\nIndependent fixture review').encode();fixture=b'fixture';matrix=b'matrix'
  approval=dict(schema=1,decision='APPROVED',source='1'*40,reviewer='/root/continuity_security_review',test_run=123,review_sha256=h(review),fixture_sha256=h(fixture),matrix_sha256=h(matrix),artifact_sha256='5'*64,scope='VPN_ONLY_CONTINUITY_WITH_EXISTING_TOR_RETIREMENT',unresolved_critical=0,unresolved_important=0)
  raw=json.dumps(approval).encode();marker=dict(source='1'*40,test_run=123,review_sha256=h(review),approval_sha256=h(raw),fixture_sha256=h(fixture),matrix_sha256=h(matrix),artifact_sha256='5'*64)
  return marker,approval,raw,review,fixture,matrix
 def test_rejected_structured_decision_never_inherits_quoted_markdown_approval(self):
  marker,approval,raw,review,fixture,matrix=self.inputs();review=b'Decision: REJECTED\nDo not use the quoted example Decision: APPROVED';marker['review_sha256']=hashlib.sha256(review).hexdigest();approval['review_sha256']=marker['review_sha256'];approval['decision']='REJECTED';raw=json.dumps(approval).encode();marker['approval_sha256']=hashlib.sha256(raw).hexdigest()
  with self.assertRaises(ValueError):validate_record(marker,approval,raw,review,fixture,matrix)
 def test_duplicate_and_conflicting_fields_rejected(self):
  for raw in ['{"decision":"REJECTED","decision":"APPROVED"}','{"source":"old","source":"new"}']:
   with self.assertRaises(ValueError):strict_json(raw)
 def test_bound_authoritative_record_and_each_field(self):
  marker,approval,raw,review,fixture,matrix=self.inputs();validate_record(marker,approval,raw,review,fixture,matrix)
  for key in approval:
   bad=approval.copy();bad[key]=None;other=json.dumps(bad).encode();m=marker|dict(approval_sha256=hashlib.sha256(other).hexdigest())
   with self.subTest(key=key),self.assertRaises(ValueError):validate_record(m,bad,other,review,fixture,matrix)
  with self.assertRaises(ValueError):validate_record(marker,approval,raw,review+b'changed',fixture,matrix)
 def test_workflow_or_verifier_drift_denies_unchanged_approval(self):
  with tempfile.TemporaryDirectory()as folder:
   root=Path(folder)
   def git(*args):return subprocess.check_output(['git','-C',str(root),'-c','user.name=Gate fixture','-c','user.email=gate@fixture.invalid',*args],text=True,stderr=subprocess.DEVNULL).strip()
   git('init');(root/'scripts').mkdir();(root/'scripts/verify_nova17103_runtime.py').write_text('reviewed');git('add','.');git('commit','-m','source');source=git('rev-parse','HEAD')
   (root/'.acceptance17103-candidate').write_text('{}');git('add','.');git('commit','-m','activation');validate_activation_delta(root,source,git('rev-parse','HEAD'))
   for name in ['scripts/verify_nova17103_runtime.py','.github/workflows/acceptance1762.yml']:
    p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text('changed');git('add','.');git('commit','-m','drift')
    with self.assertRaises(ValueError):validate_activation_delta(root,source,git('rev-parse','HEAD'))
 def test_report_status_cannot_conflict_with_json_or_quote_approval(self):
  marker,approval,raw,review,fixture,matrix=self.inputs()
  for decision in ['Decision: REJECTED\nDiscussion quotes Decision: APPROVED','Decision: REJECTED\nDecision: APPROVED']:
   report=('Source: '+'1'*40+'\n'+decision+'\nReviewer: /root/continuity_security_review\n').encode();m=marker|dict(review_sha256=hashlib.sha256(report).hexdigest());a=approval|dict(review_sha256=m['review_sha256']);r=json.dumps(a).encode();m['approval_sha256']=hashlib.sha256(r).hexdigest()
   with self.assertRaises(ValueError):validate_record(m,a,r,report,fixture,matrix)

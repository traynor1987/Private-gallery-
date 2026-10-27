"""Fail closed on narrowed scope, changed inputs and misrepresented historical evidence."""
import copy,importlib.util,json,sys,tempfile,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import release_policy as P
ROOT=Path(__file__).resolve().parents[2]
class RiskPolicyTest(unittest.TestCase):
 def setUp(self):self.scope=json.loads((ROOT/P.SCOPE_PATH).read_text())
 def verify(self,s=None):return P.verify_scope(s or self.scope,ROOT)
 def test_exact_reviewed_candidate_and152_methods(self):self.assertEqual(152,self.verify()['fresh_method_count'])
 def test_patch_version_never_waives_network_risk(self):self.assertEqual('full',P.classify(['app/src/main/java/x/HiddenBrowserRuntime.kt'],'1.7.6.2')['lane'])
 def test_unknown_path_is_full(self):self.assertEqual('full',P.classify(['new-runtime/thing.kt'],'1.7.6.2')['lane'])
 def test_major_always_full(self):self.assertEqual('full',P.classify(['README.md'],'2.0')['lane'])
 def test_feature_is_comprehensive_by_default(self):self.assertEqual('full',P.classify(['app/src/main/foo.kt'],'1.7.7')['lane'])
 def test_low_risk_patch_still_requires_review(self):self.assertEqual('patch_review_required',P.classify(['README.md'],'1.7.6.2')['lane'])
 def test_changed_app_pin_rejected(self):
  s=copy.deepcopy(self.scope);s['frozen_inputs']['app']='0'*40
  with self.assertRaises(ValueError):self.verify(s)
 def test_missing_required_case_rejected(self):
  s=copy.deepcopy(self.scope);s['required_methods'].pop()
  with self.assertRaises(ValueError):self.verify(s)
 def test_failed_historical_case_cannot_be_carried_as_pass(self):
  s=copy.deepcopy(self.scope);s['historical_failed']=[]
  with self.assertRaises(ValueError):self.verify(s)
 def test_historical_full_cannot_be_renamed_pass(self):
  s=copy.deepcopy(self.scope);s['historical_full_verdict']='PASS'
  with self.assertRaises(ValueError):self.verify(s)
 def test_duplicate_scope_case_rejected(self):
  s=copy.deepcopy(self.scope);s['required_methods'][-1]=s['required_methods'][0]
  with self.assertRaises(ValueError):self.verify(s)
 def test_bad_raw_evidence_digest_rejected(self):
  s=copy.deepcopy(self.scope);s['full_xml_sha256']='f'*64
  with self.assertRaises(ValueError):self.verify(s)
 def test_unknown_scope_decision_rejected(self):
  s=copy.deepcopy(self.scope);s['decision']='SELF_APPROVED'
  with self.assertRaises(ValueError):self.verify(s)
 def test_empty_or_incomplete_implementation_review_rejected(self):
  for reviewed in ({},{'scripts/release_policy.py':'placeholder'}):
   with tempfile.TemporaryDirectory() as f:
    root=Path(f);scopepath=root/P.SCOPE_PATH;scopepath.parent.mkdir(parents=True);scopepath.write_text(json.dumps(self.scope))
    hashes={}
    for path in reviewed:
     p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text('synthetic');hashes[path]=P.digest(p)
    approval={'decision':'GO_FOR_ONE_PATCH_ADMISSION','reviewer':'/root/risk_policy_security_review','scope_sha256':P.digest(scopepath),'reviewed_files':hashes}
    (root/P.APPROVAL_PATH).write_text(json.dumps(approval))
    from unittest.mock import patch
    with patch.object(P,'verify_scope',return_value={}):
     with self.assertRaises(ValueError):P.load_approved(root)
 def test_unreviewed_helpers_and_workflows_force_full(self):
  self.assertTrue(hasattr(P,'verify_changed_paths'),'Patch admission must enforce unknown-change escalation')
  for path in ['scripts/verify_android17_sdk.py','scripts/check_hidden_browser.sh','.github/workflows/unknown.yml','deleted-security-helper.sh','docs/release1761/risk-based/runtime.py']:
   with self.assertRaises(ValueError):P.verify_changed_paths([path])
 def test_only_reviewed_policy_marker_and_documentation_allowed(self):
  self.assertTrue(hasattr(P,'verify_changed_paths'))
  P.verify_changed_paths(['scripts/release_policy.py','.patch1761-candidate','docs/release1761/risk-based/HANDOFF.md'])
 def test_fresh_failure_skip_substitution_duplicate_rejected(self):
  for mutation in ('failure','skipped','substitute','duplicate'):
   with tempfile.TemporaryDirectory() as f:
    p=Path(f);import xml.etree.ElementTree as E
    r=E.Element('testsuite')
    for name in self.scope['required_methods']:
     cls,method=name.split('#');E.SubElement(r,'testcase',classname=cls,name=method)
    cs=list(r)
    if mutation in ('failure','skipped'):E.SubElement(cs[0],mutation)
    elif mutation=='substitute':cs[0].set('name','unrelated')
    else:cs[-1].set('classname',cs[0].get('classname'));cs[-1].set('name',cs[0].get('name'))
    E.ElementTree(r).write(p/'TEST-native.xml')
    with self.assertRaises(ValueError):P.verify_native(p,self.scope['required_methods'])
if __name__=='__main__':unittest.main()

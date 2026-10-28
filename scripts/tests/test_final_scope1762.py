import copy,importlib.util,unittest
from pathlib import Path
class FinalScope1762Test(unittest.TestCase):
 def fixture(self):
  spec=importlib.util.spec_from_file_location('final_scope1762',Path('scripts/verify_final_scope1762.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
  return m,dict(source_sha='a'*40,version='1.7.6.2',version_code=44,package='uk.co.traynor.privategallery',certificate_sha256=m.CERTIFICATE,scope={n:'PASS' for n in m.REQUIRED},focused_run_id=123,independent_review=dict(source_sha='a'*40,decision='APPROVED_FOR_SIGNING',critical_open=0,important_open=0,report_sha256='b'*64),physical_fold_acceptance='OWNER_REQUIRED',physical_samsung_acceptance='OWNER_REQUIRED')
 def test_exact_reviewed_source_and_identity_required(self):
  m,p=self.fixture();m.validate(p,'a'*40,123)
  for key,value in [('source_sha','c'*40),('version_code',43),('package','different'),('focused_run_id',124),('certificate_sha256','0'*64)]:
   with self.assertRaises(ValueError):m.validate({**p,key:value},'a'*40,123)
 def test_missing_deferred_unknown_scope_rejected(self):
  m,p=self.fixture()
  for value in ('DEFERRED','PENDING','FAIL',True):
   bad=copy.deepcopy(p);bad['scope']['interactive_vpn']=value
   with self.assertRaises(ValueError):m.validate(bad,'a'*40,123)
  bad=copy.deepcopy(p);del bad['scope']['interactive_vpn']
  with self.assertRaises(ValueError):m.validate(bad,'a'*40,123)
  bad=copy.deepcopy(p);bad['scope']['unknown']='PASS'
  with self.assertRaises(ValueError):m.validate(bad,'a'*40,123)
 def test_open_findings_and_other_reviewed_source_block_signer(self):
  m,p=self.fixture()
  for change in ({'source_sha':'c'*40},{'critical_open':1},{'important_open':1},{'decision':'PENDING'}):
   bad=copy.deepcopy(p);bad['independent_review'].update(change)
   with self.assertRaises(ValueError):m.validate(bad,'a'*40,123)
 def test_ci_cannot_claim_physical_acceptance(self):
  m,p=self.fixture()
  for key in ('physical_fold_acceptance','physical_samsung_acceptance'):
   with self.assertRaises(ValueError):m.validate({**p,key:'PASS'},'a'*40,123)
if __name__=='__main__':unittest.main()

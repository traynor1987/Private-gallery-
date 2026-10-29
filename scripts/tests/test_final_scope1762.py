import copy,importlib.util,unittest
from pathlib import Path
class FinalScope1762Test(unittest.TestCase):
 def fixture(self):
  spec=importlib.util.spec_from_file_location('final_scope1762',Path('scripts/verify_final_scope1762.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
  return m,dict(source_sha='a'*40,version='1.7.6.2',version_code=45,package='uk.co.traynor.privategallery',certificate_sha256=m.CERTIFICATE,scope={n:'PASS' for n in m.REQUIRED},focused_run_id=123,independent_review=dict(source_sha='a'*40,decision='APPROVED_FOR_SIGNING',critical_open=0,important_open=0,report_sha256='b'*64),physical_fold_acceptance='OWNER_REQUIRED',physical_samsung_acceptance='OWNER_REQUIRED')
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

class ProductionActivationTest(unittest.TestCase):
 def checker(self):
  spec=importlib.util.spec_from_file_location('final_scope_activation1762',Path('scripts/verify_final_scope1762.py'));module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module.validate_activation
 def test_both_real_modes_must_be_enabled(self):
  validate=self.checker()
  validate('const val INTERACTIVE_VERIFIED = true\nconst val TOR_NATIVE_VERIFIED = true\n')
  for text in ('const val INTERACTIVE_VERIFIED = true\nconst val TOR_NATIVE_VERIFIED = false',
               'const val INTERACTIVE_VERIFIED = false\n// const val INTERACTIVE_VERIFIED = true\nconst val TOR_NATIVE_VERIFIED = true',
               'const val INTERACTIVE_VERIFIED = true\n/* const val TOR_NATIVE_VERIFIED = true */'):
   with self.assertRaises(ValueError):validate(text)

if __name__=='__main__':unittest.main()

class ReviewedCaptureRevalidationTest(unittest.TestCase):
 def proof(self):
  return dict(source_sha='97e94452ceb9c5262521d7552f0c25eeb07374eb',run_id=40848723808,artifact_id=12887270723,artifact_digest='sha256:b01b11d5ba2bbfeadfa097957b1ca5b7ab2313c502c1a622617a1abf7457a2fc',conclusion='failure',original_exit_status=1,verified_build_steps=True,evidence_validation='PASS',validated_case_count=13,reviewed_validator_sha256='a9359061347d0dd79bf527fe724dcbceb4e7f998332e19570c922f33eb33e3c0')
 def test_only_exact_revalidated_capture_is_accepted(self):
  import importlib.util
  spec=importlib.util.spec_from_file_location('scope45',Path('scripts/verify_final_scope1762.py'))
  module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
  good=self.proof();module.validate_focused(good,good['source_sha'])
  for field,value in [('source_sha','0'*40),('run_id',1),('artifact_id',1),('artifact_digest','sha256:'+'0'*64),('original_exit_status',0),('verified_build_steps',False),('evidence_validation','PENDING'),('validated_case_count',12),('reviewed_validator_sha256','0'*64)]:
   bad={**good,field:value}
   with self.assertRaises(ValueError,msg=field):module.validate_focused(bad,good['source_sha'])

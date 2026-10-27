import copy,hashlib,json,sys,tempfile,unittest,xml.etree.ElementTree as E
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import release_policy as P
import verify_patch_production1761 as V
ROOT=Path(__file__).resolve().parents[2]
SCOPE=json.loads((ROOT/P.SCOPE_PATH).read_text())
class PatchEvidenceTest(unittest.TestCase):
 def fixture(self,root):
  evidence=root/'evidence';evidence.mkdir();apk=root/'debug.apk';apk.write_bytes(b'synthetic-debug')
  for name,value in [('source-sha.txt','candidate'),('sdk.txt','37'),('android-release.txt','17'),('command-exit-status.txt','0'),('apk-sha256.txt',hashlib.sha256(apk.read_bytes()).hexdigest())]: (evidence/name).write_text(value)
  media={'sdk':'37','qemu':'1','selinux':'Enforcing','avcdec':'','source_sha':'candidate'}
  for phase in ('before','after'):(evidence/('media-'+phase+'.json')).write_text(json.dumps(media))
  (evidence/'native').mkdir();r=E.Element('testsuite')
  for name in SCOPE['required_methods']:
   cls,method=name.split('#');E.SubElement(r,'testcase',classname=cls,name=method)
  E.ElementTree(r).write(evidence/'native/TEST-native.xml')
  jvm=root/'app/build/test-results/testDebugUnitTest';jvm.mkdir(parents=True);r=E.Element('testsuite',tests='354')
  for i in range(354):E.SubElement(r,'testcase',classname='synthetic',name=str(i))
  E.ElementTree(r).write(jvm/'TEST-unit.xml')
  for phase in ('seed','verify'):(evidence/('browser-process-'+phase+'.txt')).write_text('OK (1 test)')
  diag=root/'app/build/acceptance-diagnostics';diag.mkdir(parents=True)
  for phase in ('seed','verify','after-restart'):(diag/('upgrade-'+phase+'.txt')).write_text('OK (1 test)')
  (diag/'upgrade-result.txt').write_text('PASS: synthetic');(diag/'upgrade-candidate-source-sha.txt').write_text('candidate')
  return evidence,apk
 def check(self,mutate=None):
  with tempfile.TemporaryDirectory() as f:
   root=Path(f);e,apk=self.fixture(root)
   if mutate:mutate(root,e,apk)
   with patch.object(P,'load_approved',return_value=(SCOPE,{'historical_full_verdict':'FAILED'})):
    return P.verify_evidence(e,'candidate',apk,root)
 def test_exact_fresh_upgrade_records_pass(self):self.assertEqual('PASS',self.check()['fresh_result'])
 def test_wrong_apk_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,e,a:a.write_bytes(b'other'))
 def test_wrong_source_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,e,a:(e/'source-sha.txt').write_text('old'))
 def test_missing_process_verification_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,e,a:(e/'browser-process-verify.txt').write_text('FAILED'))
 def test_upgrade_restart_failure_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,e,a:(r/'app/build/acceptance-diagnostics/upgrade-after-restart.txt').write_text('FAILURES!!!'))
 def test_permissive_after_rejected(self):
  def mutate(r,e,a):
   p=e/'media-after.json';d=json.loads(p.read_text());d['selinux']='Permissive';p.write_text(json.dumps(d))
  with self.assertRaises(ValueError):self.check(mutate)
 def test_patch_workflow_has_no_live_or_full_suite_invocation(self):
  s=(ROOT/'.github/workflows/patch1761.yml').read_text()
  for bad in ('ci_onion_probe.py --start','run_release1761_platform.sh','run_hidden_browser_preflight.sh','run_android_instrumentation_with_diagnostics.sh'):
   self.assertNotIn(bad,s)
  for required in ('run_patch1761.sh','release_policy.py evidence','verify_synthetic_upgrade.sh','verify_patch_production1761.py','verify_permanent_signer.sh'):
   self.assertIn(required,s)
 def test_fast_lane_has_no_emulator_action(self):self.assertNotIn('android-emulator-runner',(ROOT/'.github/workflows/fast-development.yml').read_text())
 def test_full_lane_retains_original_tests_and_monthly_dispatch(self):
  full=(ROOT/'.github/workflows/acceptance1761.yml').read_text();self.assertIn('HiddenRuntimeFailureTest',full);self.assertIn('run_release1761_platform.sh',full);self.assertIn('workflow_dispatch:',full)
  self.assertIn("cron: '17 4 1 * *'",(ROOT/'.github/workflows/periodic-security.yml').read_text())
class ProductionPatchEvidenceTest(unittest.TestCase):
 def check(self,mutation=None):
  with tempfile.TemporaryDirectory() as folder:
   root=Path(folder);apk=root/'candidate.apk';apk.write_bytes(b'synthetic-production')
   summary={'fresh_result':'PASS','source_sha':'candidate','debug_upgrade_restart':'PASS','fresh_executed_methods':SCOPE['required_methods'],'historical_full_verdict':'FAILED'}
   (root/'patch-summary.json').write_text(json.dumps(summary))
   (root/'upgrade-candidate-source-sha.txt').write_text('candidate');(root/'upgrade-baseline-source-sha.txt').write_text(SCOPE['baseline_source'])
   (root/'upgrade-candidate-sha256.txt').write_text(hashlib.sha256(apk.read_bytes()).hexdigest())
   for phase in ('seed','verify','after-restart'):(root/('upgrade-'+phase+'.txt')).write_text('OK (1 test)')
   (root/'upgrade-result.txt').write_text('PASS: synthetic')
   for phase,code,version,minimum in [('baseline',41,'1.7.6',36),('candidate',42,'1.7.6.1',37)]:
    (root/('upgrade-'+phase+'-signature.txt')).write_text('Signer #1 certificate SHA-256 digest: '+V.CERT)
    (root/('upgrade-'+phase+'-package.txt')).write_text("package: name='uk.co.traynor.privategallery' versionCode='"+str(code)+"' versionName='"+version+"'\nsdkVersion:'"+str(minimum)+"'\ntargetSdkVersion:'"+str(minimum)+"'")
   if mutation:mutation(root,apk)
   with patch.object(V,'load_approved',return_value=(SCOPE,{})):return V.verify(root,'candidate',apk)
 def test_production_receipts_match_candidate(self):self.assertEqual('PASS',self.check()['original_key_upgrade_restart'])
 def test_replacement_certificate_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,a:(r/'upgrade-candidate-signature.txt').write_text('wrong'))
 def test_unupgraded_apk_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,a:a.write_bytes(b'other'))
 def test_missing_original_key_restart_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,a:(r/'upgrade-after-restart.txt').write_text('NOT EXECUTED'))
 def test_wrong_package_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,a:(r/'upgrade-candidate-package.txt').write_text("package: name='wrong' versionCode='42'"))
 def test_history_cannot_be_reclassified_passed(self):
  def change(r,a):
   p=r/'patch-summary.json';d=json.loads(p.read_text());d['historical_full_verdict']='PASS';p.write_text(json.dumps(d))
  with self.assertRaises(ValueError):self.check(change)
if __name__=='__main__':unittest.main()

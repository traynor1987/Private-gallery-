import pathlib,sys,unittest,shutil,json
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import verify_release1761_platforms as candidate
import verify_release1761_focused as focus
import test_release1751_platform_evidence as baseline
class Platform1761EvidenceTest(unittest.TestCase):
 def setUp(self):
  self.fixture=baseline.PlatformEvidenceTest();self.fixture.setUp();self.addCleanup(self.fixture.doCleanups)
  self.root=self.fixture.root/'platforms';self.root.mkdir();self.apk=self.fixture.apk
  (self.fixture.root/'api37').rename(self.root/'api37')
  shutil.rmtree(self.fixture.root/'api36')
  self.fixture.report(self.fixture.root/'test-results/testDebugUnitTest',354)
  self.changed=self.fixture.root/'acceptance-diagnostics/changed1761/api37'
  self.fixture.write(self.changed/'source-sha.txt','tested-source');self.fixture.write(self.changed/'android-release.txt','17');self.fixture.write(self.changed/'sdk.txt','37')
  self.fixture.write(self.changed/'results/logcat-synthetic-HiddenTorBootstrapTest-case.txt','phase=subscribed\nreaderAlive=false\n')
  self.fixture.write(self.fixture.root/'acceptance-diagnostics/android17-sdk.json',json.dumps({'api_level':37,'preview':False,'metadata_sha256':'a'*64}))
  self.report(self.root/'api37/diagnostics/professional-ui/results',201,[])
  self.report(self.root/'api37/full-results',436,[])
  self.report(self.root/'api37/diagnostics/hidden-browser-preflight',76,candidate.SECURITY_REQUIRED)
  self.report(self.changed/'results',101,candidate.SECURITY_REQUIRED)
 def report(self,path,count,names):
  self.fixture.report(path,count-len(names))
  cases=''.join('<testcase classname="'+v.split('#')[0]+'" name="'+v.split('#')[1]+'"/>' for v in names)
  self.fixture.write(path/'TEST-1761.xml','<testsuite>'+cases+'</testsuite>')
 def named(self):
  self.report(self.root/'api37/full-results',436,candidate.REQUIRED+candidate.SECURITY_REQUIRED)
  self.report(self.root/'api37/diagnostics/professional-ui/results',201,candidate.REQUIRED)
 def test_focused_gate_accepts_before_full_results_exist(self):
  self.assertTrue(focus.verify(self.changed,'tested-source').startswith('PASS:'))
 def test_focused_gate_rejects_lingering_observer_before_full(self):
  self.fixture.write(self.changed/'results/logcat-synthetic-HiddenTorBootstrapTest-case.txt','phase=subscribed readerAlive=true')
  with self.assertRaises(ValueError):focus.verify(self.changed,'tested-source')
 def test_focused_gate_rejects_substituted_runtime_control(self):
  p=self.changed/'results/TEST-1761.xml';p.write_text(p.read_text().replace('stalledConnectTimeoutDisconnectsRuntimeAndClosesSocket','unrelated'))
  with self.assertRaises(ValueError):focus.verify(self.changed,'tested-source')
 def test_counts_without_named_regressions_rejected(self):
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
 def test_only_exact_android17_and_all_names_admitted(self):
  self.named();r=candidate.verify(self.root,'tested-source',self.apk);self.assertEqual(354,r['jvm']);self.assertEqual({'api37'},set(r['platforms']))
 def test_missing_one_new_name_is_rejected(self):
  self.named();p=self.root/'api37/full-results/TEST-1761.xml';p.write_text(p.read_text().replace('draggingAcrossVisibleThumbnailsSelectsStableRangeWithoutReadingPayloads','unrelated'))
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
 def test_missing_jvm_case_rejected(self):
  self.named();self.fixture.report(self.fixture.root/'test-results/testDebugUnitTest',353)
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
 def test_missing_full_case_rejected(self):
  self.named();self.fixture.report(self.root/'api37/full-results',436-len(candidate.REQUIRED)-len(candidate.SECURITY_REQUIRED)-1)
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
 def test_changed_gate_wrong_source_rejected(self):
  self.named();self.fixture.write(self.changed/'source-sha.txt','old-source')
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
 def test_changed_gate_missing_case_rejected(self):
  self.named();self.fixture.report(self.changed/'results',101-len(candidate.SECURITY_REQUIRED)-1)
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
 def test_changed_gate_substituted_tor_case_rejected(self):
  self.named();p=self.changed/'results/TEST-1761.xml';p.write_text(p.read_text().replace('packagedTorBootstrapsInPrivateProcessAndStopsWhenReleased','unrelated'))
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
 def test_runtime_control_cannot_be_substituted(self):
  self.named();p=self.root/'api37/diagnostics/hidden-browser-preflight/TEST-1761.xml';p.write_text(p.read_text().replace('stalledConnectTimeoutDisconnectsRuntimeAndClosesSocket','unrelated'))
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
 def test_android16_cannot_substitute_for_android17(self):
  self.named();self.fixture.write(self.root/'api37/sdk.txt','36')
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
 def test_observer_must_terminate(self):
  self.named();self.fixture.write(self.changed/'results/logcat-synthetic-HiddenTorBootstrapTest-case.txt','phase=subscribed readerAlive=true')
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
 def test_wrong_installed_sdk_cannot_satisfy_gate(self):
  self.named();self.fixture.write(self.fixture.root/'acceptance-diagnostics/android17-sdk.json',json.dumps({'api_level':36,'preview':False,'metadata_sha256':'a'*64}))
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
if __name__=='__main__':unittest.main()

import pathlib,sys,unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import verify_release1761_platforms as candidate
import test_release1751_platform_evidence as baseline
class Platform1761EvidenceTest(unittest.TestCase):
 def setUp(self):
  self.fixture=baseline.PlatformEvidenceTest();self.fixture.setUp();self.addCleanup(self.fixture.doCleanups)
  self.root=self.fixture.root/'platforms';self.root.mkdir();self.apk=self.fixture.apk
  for sdk in ('api36','api37'):(self.fixture.root/sdk).rename(self.root/sdk)
  self.fixture.report(self.fixture.root/'test-results/testDebugUnitTest',354)
  for sdk in ('api36','api37'):
   self.fixture.report(self.root/sdk/'diagnostics/professional-ui/results',201)
   self.fixture.report(self.root/sdk/'full-results',434)
   self.fixture.report(self.fixture.root/'acceptance-diagnostics/changed1761'/sdk/'results',98)
   self.fixture.write(self.fixture.root/'acceptance-diagnostics/changed1761'/sdk/'source-sha.txt','tested-source')
 def named(self):
  for sdk in ('api36','api37'):
   for reports,count in (('full-results',434),('diagnostics/professional-ui/results',201)):
    self.fixture.report(self.root/sdk/reports,count-len(candidate.REQUIRED))
    cases=''.join('<testcase classname="'+v.split('#')[0]+'" name="'+v.split('#')[1]+'"/>' for v in candidate.REQUIRED)
    self.fixture.write(self.root/sdk/reports/'TEST-1761.xml','<testsuite>'+cases+'</testsuite>')
 def test_counts_without_named_regressions_rejected(self):
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
 def test_all_exact_platforms_and_names_admitted(self):
  self.named();self.assertEqual(354,candidate.verify(self.root,'tested-source',self.apk)['jvm'])
 def test_missing_one_new_name_is_rejected(self):
  self.named();p=self.root/'api37/full-results/TEST-1761.xml';p.write_text(p.read_text().replace('draggingAcrossVisibleThumbnailsSelectsStableRangeWithoutReadingPayloads','unrelated'))
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
 def test_missing_jvm_case_rejected(self):
  self.named();self.fixture.report(self.fixture.root/'test-results/testDebugUnitTest',353)
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
 def test_missing_full_case_rejected(self):
  self.named();self.fixture.report(self.root/'api37/full-results',434-len(candidate.REQUIRED)-1)
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
 def test_changed_gate_wrong_source_rejected(self):
  self.named();self.fixture.write(self.fixture.root/'acceptance-diagnostics/changed1761/api37/source-sha.txt','old-source')
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
 def test_changed_gate_missing_case_rejected(self):
  self.named();self.fixture.report(self.fixture.root/'acceptance-diagnostics/changed1761/api37/results',97)
  with self.assertRaises(ValueError):candidate.verify(self.root,'tested-source',self.apk)
if __name__=='__main__':unittest.main()

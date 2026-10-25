import pathlib,sys,unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import verify_release1752_platforms as candidate
import test_release1751_platform_evidence as baseline
class Platform1752EvidenceTest(unittest.TestCase):
 def setUp(self):
  self.fixture=baseline.PlatformEvidenceTest()
  self.fixture.setUp();self.addCleanup(self.fixture.doCleanups)
  self.root=self.fixture.root;self.apk=self.fixture.apk
  for sdk in ('api36','api37'):
   self.fixture.report(self.root/sdk/'diagnostics/professional-ui/results',134)
   self.fixture.report(self.root/sdk/'full-results',401)
 def add_named(self):
  for sdk in ('api36','api37'):
   for reports in ('full-results','diagnostics/professional-ui/results'):
    self.fixture.report(self.root/sdk/reports,(134 if reports.startswith('diagnostics') else 401)-len(candidate.REQUIRED))
    path=self.root/sdk/reports/'TEST-1752.xml'
    cases=''.join('<testcase classname="'+value.split('#')[0]+'" name="'+value.split('#')[1]+'"/>' for value in candidate.REQUIRED)
    self.fixture.write(path,'<testsuite>'+cases+'</testsuite>')
 def test_counts_cannot_substitute_for_new_regressions(self):
  with self.assertRaisesRegex(ValueError,'Required UI classes absent'):candidate.verify(self.root,'tested-source',self.apk)
 def test_both_named_focused_and_full_evidence_admitted(self):
  self.add_named()
  self.assertIn('named1752_regressions',candidate.verify(self.root,'tested-source',self.apk))
 def test_missing_one_platform_new_result_is_rejected(self):
  self.add_named()
  path=self.root/'api37/full-results/TEST-1752.xml'
  path.write_text(path.read_text().replace(candidate.REQUIRED[0].split('#')[1],'unrelatedCase'))
  with self.assertRaisesRegex(ValueError,'Required UI classes absent'):candidate.verify(self.root,'tested-source',self.apk)
if __name__=='__main__':unittest.main()

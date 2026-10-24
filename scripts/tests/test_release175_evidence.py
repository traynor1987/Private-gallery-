import importlib.util,pathlib,tempfile,unittest
SPEC=importlib.util.spec_from_file_location('evidence',pathlib.Path(__file__).resolve().parents[1]/'verify_release175_evidence.py');M=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(M)
class EvidenceTest(unittest.TestCase):
 def check(self,outcome='',expected=1):
  with tempfile.TemporaryDirectory() as directory:
   p=pathlib.Path(directory)/'TEST-fixture.xml';p.write_text('<testsuite tests="1"><testcase classname="fixture" name="case">'+outcome+'</testcase></testsuite>')
   return M.count_reports(directory,expected)
 def test_valid_exact_count(self):self.assertEqual(1,self.check())
 def test_failure_rejected(self):
  with self.assertRaises(ValueError):self.check('<failure/>')
 def test_skip_rejected(self):
  with self.assertRaises(ValueError):self.check('<skipped/>')
 def test_wrong_count_rejected(self):
  with self.assertRaises(ValueError):self.check(expected=2)
if __name__=='__main__':unittest.main()

import importlib.util, pathlib, tempfile, unittest
SPEC=importlib.util.spec_from_file_location('evidence',pathlib.Path(__file__).resolve().parents[1]/'verify_professional_ui_results.py')
M=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(M)
class MethodEvidenceTest(unittest.TestCase):
 def check(self,cases):
  with tempfile.TemporaryDirectory() as directory:
   pathlib.Path(directory,'TEST-fixture.xml').write_text('<testsuite>'+cases+'</testsuite>')
   return M.verify(directory,['fixture#required'])
 def test_passing_required_method(self):
  self.assertEqual(1,self.check('<testcase classname="fixture" name="required"/>')['passed_tests'])
 def test_missing_method_despite_same_class_passing(self):
  with self.assertRaises(ValueError):self.check('<testcase classname="fixture" name="other"/>')
 def test_skipped_required_method(self):
  with self.assertRaises(ValueError):self.check('<testcase classname="fixture" name="required"><skipped/></testcase>')
 def test_duplicate_pass_cannot_hide_required_skip(self):
  with self.assertRaises(ValueError):self.check('<testcase classname="fixture" name="required"/><testcase classname="fixture" name="required"><skipped/></testcase>')
if __name__=='__main__':unittest.main()

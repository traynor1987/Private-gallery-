import importlib.util,json,tempfile,unittest
from pathlib import Path
SPEC=importlib.util.spec_from_file_location('sdk17',Path(__file__).resolve().parents[1]/'verify_android17_sdk.py')
class Sdk17Test(unittest.TestCase):
 def run_check(self,text):
  m=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(m)
  with tempfile.TemporaryDirectory() as folder:
   p=Path(folder)/'platforms/android-37.1';p.mkdir(parents=True);(p/'source.properties').write_text(text)
   return m.verify(Path(folder))
 def test_actual_release37_metadata(self):self.assertEqual(37,self.run_check('AndroidVersion.ApiLevel=37\nAndroidVersion.CodeName=REL\n')['api_level'])
 def test_wrong_api_rejected(self):
  with self.assertRaises(ValueError):self.run_check('AndroidVersion.ApiLevel=36\n')
 def test_preview_rejected(self):
  with self.assertRaises(ValueError):self.run_check('AndroidVersion.ApiLevel=37\nAndroidVersion.CodeName=Preview\n')
 def test_missing_api_rejected(self):
  with self.assertRaises(ValueError):self.run_check('Pkg.Revision=1\n')
if __name__=='__main__':unittest.main()

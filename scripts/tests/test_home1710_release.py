import tempfile,unittest,zipfile
from pathlib import Path
from scripts.verify_home1710_runtime import verify,EXPECTED

class RuntimeGateTest(unittest.TestCase):
 def archive(self,classes,failed=False):
  root=tempfile.TemporaryDirectory();self.addCleanup(root.cleanup);path=Path(root.name)/'runtime.zip'
  with zipfile.ZipFile(path,'w') as z:
   for c in classes:
    z.writestr('outputs/androidTest-results/connected/debug/TEST-'+c+'.xml',f'<testsuite tests="1" failures="{1 if failed else 0}" errors="0" skipped="0"><testcase classname="{c}" name="realCase">'+('<failure/>'if failed else '')+'</testcase></testsuite>')
  return path
 def test_complete_android_reports_are_required(self):self.assertGreater(verify(self.archive(EXPECTED)),0)
 def test_failure_denies_signer_gate(self):
  with self.assertRaises(ValueError):verify(self.archive(EXPECTED,True))
 def test_missing_class_denies_signer_gate(self):
  with self.assertRaises(ValueError):verify(self.archive(list(EXPECTED)[:-1]))
 def test_empty_artifact_denies_signer_gate(self):
  with self.assertRaises(ValueError):verify(self.archive([]))

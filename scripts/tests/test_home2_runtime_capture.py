import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]

class HomeRuntimeCapture(unittest.TestCase):
 def test_original_gradle_exit_survives_successful_or_failed_diagnostic_pull(self):
  for result,pull in ((0,0),(7,0),(7,9)):
   with self.subTest(result=result,pull=pull),tempfile.TemporaryDirectory() as temporary:
    p=Path(temporary);(p/'scripts').mkdir();(p/'bin').mkdir()
    (p/'scripts/verify_home2_runtime.py').write_text('print("Synthetic.SelectedCase")\n')
    gradle=p/'gradlew';gradle.write_text('#!/bin/bash\nprintf "%s\\n" "$*" > gradle-args.txt\nexit '+str(result)+'\n');gradle.chmod(0o755)
    adb=p/'bin/adb';adb.write_text('#!/bin/bash\nprintf "%s\\n" "$*" > adb-args.txt\nexit '+str(pull)+'\n');adb.chmod(0o755)
    env=dict(os.environ,PATH=str(p/'bin')+':'+os.environ['PATH'])
    ran=subprocess.run(['bash',str(ROOT/'scripts/run_home2_runtime.sh')],cwd=p,env=env,capture_output=True,text=True)
    self.assertEqual(result,ran.returncode,ran.stderr)
    self.assertIn('connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=Synthetic.SelectedCase',(p/'gradle-args.txt').read_text())
    self.assertIn('pull /sdcard/Android/data/uk.co.traynor.privategallery.hiddenprototype/files/nova-status60',(p/'adb-args.txt').read_text())

if __name__=='__main__':unittest.main()

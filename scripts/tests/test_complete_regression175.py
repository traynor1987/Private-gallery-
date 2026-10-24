import os,pathlib,subprocess,tempfile,unittest
SCRIPT=pathlib.Path(__file__).resolve().parents[1]/'complete_android_regression.sh'
class CompleteRegression175Test(unittest.TestCase):
 def check_release(self,branch):
  with tempfile.TemporaryDirectory() as directory:
   root=pathlib.Path(directory);(root/'scripts').mkdir();(root/'bin').mkdir();(root/'app/build/acceptance-diagnostics').mkdir(parents=True)
   (root/'scripts/verify_professional_ui_results.py').write_text("import pathlib;pathlib.Path('checked').write_text('strict verifier invoked');print('{}')")
   (root/'scripts/verify_synthetic_upgrade.sh').write_text("#!/bin/bash\nset -e\nprintf upgrade > upgraded\n")
   git=root/'bin/git';git.write_text('#!/bin/bash\nprintf frozen-source\\n\n');git.chmod(0o755)
   env=dict(os.environ,GITHUB_REF='refs/heads/'+branch,PATH=str(root/'bin')+':'+os.environ['PATH'])
   subprocess.run(['bash',str(SCRIPT)],cwd=root,env=env,check=True)
   self.assertTrue((root/'checked').exists(),'release175 must invoke the unchanged strict XML verifier')
   self.assertTrue((root/'upgraded').exists(),'release175 must run synthetic upgrade, never skip it')
   self.assertTrue((root/'app/build/acceptance-diagnostics/full-regression-source-sha.txt').exists())
 def test_release175(self):self.check_release('release/1.7.5-media-experience-privacy')
 def test_release1751(self):self.check_release('release/1.7.5.1-privacy-viewer-repair')
if __name__=='__main__':unittest.main()

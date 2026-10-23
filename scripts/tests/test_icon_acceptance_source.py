import importlib.util
from pathlib import Path
import unittest
import io
import zipfile
s=importlib.util.spec_from_file_location('g',Path(__file__).resolve().parents[1]/'verify_icon_acceptance_source.py');g=importlib.util.module_from_spec(s);s.loader.exec_module(g)
class GateTest(unittest.TestCase):
 def test_rejects_any_application_build_or_upgrade_change(self):
  for path in ('app/build.gradle.kts','app/src/main/AndroidManifest.xml','app/src/main/res/drawable/ic_private_gallery_foreground.xml','app/src/androidTest/java/uk/co/traynor/privategallery/ui/LauncherBrandingTest.kt','scripts/verify_synthetic_upgrade.sh','scripts/verify_permanent_signer.sh'):
   with self.subTest(path=path),self.assertRaises(ValueError):g.check_paths([path])
 def test_allows_only_post_test_workflow_fix(self):g.check_paths(['.github/workflows/android.yml','scripts/complete_android_regression.sh'])
 def test_artifact_pin_cannot_accept_another_source_or_digest(self):
  run={'id':g.RUN,'head_sha':g.SOURCE,'status':'completed','conclusion':'failure','event':'push','head_branch':'release/1.7.1-professional-ui','path':'.github/workflows/android.yml','repository':{'full_name':g.REPO}}
  meta={'id':g.ARTIFACT,'digest':'sha256:'+g.DIGEST,'expired':False,'workflow_run':{'id':g.RUN,'head_sha':g.SOURCE}}
  g.verify_run(run,meta)
  for key,value in [('id',0),('digest','sha256:'+'0'*64),('expired',True)]:
   with self.subTest(key=key),self.assertRaises(ValueError):g.verify_run(run,{**meta,key:value})
  with self.assertRaises(ValueError):g.verify_run({**run,'head_sha':'other'},meta)
 def test_no_failed_skipped_missing_or_wrong_count_case_is_accepted(self):
  for xml in ('<testsuite/>','<testsuite><testcase><failure/></testcase></testsuite>','<testsuite><testcase><skipped/></testcase></testsuite>','<testsuite><testcase/><testcase/></testsuite>'):
   with self.subTest(xml=xml),self.assertRaises(ValueError):self.suite(xml)
  self.assertEqual(1,self.suite('<testsuite><testcase/></testsuite>'))
 def suite(self,xml):
  data=io.BytesIO()
  with zipfile.ZipFile(data,'w') as z:z.writestr('results/TEST-example.xml',xml)
  with zipfile.ZipFile(data) as z:return g.check_suite(z,'results/',1)
if __name__=='__main__':unittest.main()

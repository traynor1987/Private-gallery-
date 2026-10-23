#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest
import io
import zipfile
spec=importlib.util.spec_from_file_location('gate',Path(__file__).resolve().parents[1]/'verify_icon_acceptance_source.py')
g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)

class GateTest(unittest.TestCase):
    def test_rejects_security_build_and_existing_test_changes(self):
        for path in ('app/build.gradle.kts','app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt','scripts/verify_synthetic_upgrade.sh','app/src/androidTest/java/uk/co/traynor/privategallery/ui/PrimaryCopyDialogTest.kt','app/src/main/res/values/strings.xml'):
            with self.subTest(path=path),self.assertRaises(ValueError): g.check_paths([path])
    def test_current_committed_delta_is_branding_only(self):
        g.check_paths(g.git("diff","--name-only",g.SOURCE,"HEAD").splitlines())
    def test_allows_only_branding_delta(self):
        g.check_paths(['app/src/main/res/mipmap-xxxhdpi/ic_launcher.png','docs/branding/source.json'])
    def test_requires_exact_successful_source(self):
        run={'id':g.RUN,'head_sha':g.SOURCE,'conclusion':'success','status':'completed','event':'push','head_branch':'release/1.7.1-professional-ui','path':'.github/workflows/android.yml','repository':{'full_name':g.REPO}}
        meta={'workflow_run':{'id':g.RUN,'head_sha':g.SOURCE},'name':'private-gallery-debug-and-instrumentation-diagnostics','expired':False,'digest':'sha256:'+'a'*64}
        g.verify_run(run,meta)
        for key,value in [('head_sha','wrong'),('conclusion','failure'),('status','in_progress'),('repository',{'full_name':'other/repo'})]:
            with self.subTest(key=key),self.assertRaises(ValueError): g.verify_run({**run,key:value},meta)
        with self.assertRaises(ValueError):g.verify_run(run,{**meta,'expired':True})
    def test_rejects_missing_failed_skipped_and_wrong_count_reports(self):
        for xml in ('<testsuite/>','<testsuite><testcase><failure/></testcase></testsuite>','<testsuite><testcase><skipped/></testcase></testsuite>','<testsuite><testcase/><testcase/></testsuite>'):
            with self.subTest(xml=xml),self.assertRaises(ValueError): self.suite(xml)
        self.assertEqual(1,self.suite('<testsuite><testcase/></testsuite>'))
    def suite(self,xml):
        data=io.BytesIO()
        with zipfile.ZipFile(data,'w') as z:z.writestr('results/TEST-example.xml',xml)
        with zipfile.ZipFile(data) as z:return g.check_suite(z,'results/',1)

if __name__=='__main__':unittest.main()

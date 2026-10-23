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
    def test_requires_exact_archived_failed_source(self):
        run={'id':g.RUN,'head_sha':g.SOURCE,'conclusion':'failure','status':'completed','event':'push','head_branch':'release/1.7.1-professional-ui','path':'.github/workflows/android.yml','repository':{'full_name':g.REPO}}
        meta={'id':g.ARTIFACT,'workflow_run':{'id':g.RUN,'head_sha':g.SOURCE},'name':'private-gallery-debug-and-instrumentation-diagnostics','expired':False,'digest':'sha256:'+g.DIGEST}
        g.verify_run(run,meta)
        for key,value in [('head_sha','wrong'),('conclusion','success'),('status','in_progress'),('repository',{'full_name':'other/repo'})]:
            with self.subTest(key=key),self.assertRaises(ValueError): g.verify_run({**run,key:value},meta)
        with self.assertRaises(ValueError):g.verify_run(run,{**meta,'expired':True})
    def test_rejects_missing_failed_skipped_and_wrong_count_reports(self):
        for xml in ('<testsuite/>','<testsuite><testcase><failure/></testcase></testsuite>','<testsuite><testcase><skipped/></testcase></testsuite>','<testsuite><testcase/><testcase/></testsuite>'):
            with self.subTest(xml=xml),self.assertRaises(ValueError): self.suite(xml)
        self.assertEqual(1,self.suite('<testsuite><testcase/></testsuite>'))
    def test_full_failure_is_exactly_known_tor_and_nothing_else(self):
        cases=[g.ET.fromstring('<testcase classname="other" name="passing"/>') for _ in range(315)]
        failure=g.ET.Element('testcase',classname=g.TOR_CLASS,name=g.TOR_METHOD)
        g.ET.SubElement(failure,'failure').text='onion-probe must be an actual upstream response, not synthetic success expected null, but was:<transport>'
        cases.append(failure);g.check_full_cases(cases)
        for attr,value in [('classname','other'),('name','other')]:
            old=failure.get(attr);failure.set(attr,value)
            with self.assertRaises(ValueError):g.check_full_cases(cases)
            failure.set(attr,old)
        g.ET.SubElement(cases[0],'skipped')
        with self.assertRaises(ValueError):g.check_full_cases(cases)
    def test_fresh_tor_must_pass_with_launcher(self):
        cases=[g.ET.Element('testcase',classname=g.TOR_CLASS,name=g.TOR_METHOD),g.ET.Element('testcase',classname='uk.co.traynor.privategallery.ui.LauncherBrandingTest',name='packagedAdaptiveRoundAndThemedIconsRender')]
        self.assertEqual(2,g.check_fresh_cases(cases))
        g.ET.SubElement(cases[0],'failure')
        with self.assertRaises(ValueError):g.check_fresh_cases(cases)
    def suite(self,xml):
        data=io.BytesIO()
        with zipfile.ZipFile(data,'w') as z:z.writestr('results/TEST-example.xml',xml)
        with zipfile.ZipFile(data) as z:return g.check_suite(z,'results/',1)

if __name__=='__main__':unittest.main()

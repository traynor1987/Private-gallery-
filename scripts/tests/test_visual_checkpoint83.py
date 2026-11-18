"""The existing visual runner keeps whole-class coverage and verifies focused identities."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]

class VisualCheckpoint83Test(unittest.TestCase):
 def run_fixture(self,method='',mode='all'):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory);(root/'scripts').mkdir()
   shutil.copy2(ROOT/'scripts/run_visual_checkpoint.sh',root/'scripts/run_visual_checkpoint.sh')
   (root/'scripts/run_android17_checked_command.sh').write_text('#!/bin/bash\nset -eu\nshift\ntest "$1" = --\nshift\nexec "$@"\n')
   source=root/'app/src/androidTest/java/synthetic/fixture/Fixture83Test.kt';source.parent.mkdir(parents=True)
   source.write_text('class Fixture83Test { @Test fun one() {}\n @Test fun two() {} }\n')
   gradle=root/'gradlew'
   gradle.write_text('''#!/usr/bin/env python3
from pathlib import Path
import os,sys,xml.etree.ElementTree as E
Path('trace').write_text('\\n'.join(sys.argv[1:]))
mode=os.environ['FIXTURE_MODE'];names=['one','two'] if mode=='all' else ['two'] if mode=='wrong' else ['one','one'] if mode=='duplicate' else ['one']
suite=E.Element('testsuite')
for name in names:
 case=E.SubElement(suite,'testcase',classname='synthetic.fixture.Fixture83Test',name=name)
 if mode=='skipped':E.SubElement(case,'skipped')
report=Path('app/build/outputs/androidTest-results/connected/debug/TEST-fixture.xml');report.parent.mkdir(parents=True,exist_ok=True);report.write_bytes(E.tostring(suite))
''');gradle.chmod(0o755)
   result=subprocess.run(['bash','scripts/run_visual_checkpoint.sh','Fixture83Test','focused','synthetic.fixture',method],cwd=root,
    env=dict(os.environ,FIXTURE_MODE=mode),capture_output=True,text=True)
   trace=(root/'trace').read_text() if (root/'trace').exists() else ''
   return result,trace
 def test_default_still_requires_every_original_case(self):
  result,trace=self.run_fixture();self.assertEqual(0,result.returncode,result.stderr)
  self.assertIn('class=synthetic.fixture.Fixture83Test\n',trace)
  self.assertIn('2/2 PASS',result.stdout)
  self.assertNotEqual(0,self.run_fixture(mode='selected')[0].returncode)
 def test_focused_method_is_executed_and_its_exact_identity_is_required(self):
  result,trace=self.run_fixture('one','selected');self.assertEqual(0,result.returncode,result.stderr)
  self.assertIn('class=synthetic.fixture.Fixture83Test#one\n',trace)
  self.assertIn('1/1 PASS',result.stdout)
  self.assertNotEqual(0,self.run_fixture('one','wrong')[0].returncode)
 def test_skipped_or_duplicate_focused_results_cannot_pass(self):
  for mode in ('skipped','duplicate'):
   with self.subTest(mode=mode):self.assertNotEqual(0,self.run_fixture('one',mode)[0].returncode)
 def test_unknown_or_invalid_method_cannot_be_accepted(self):
  self.assertNotEqual(0,self.run_fixture('missing','selected')[0].returncode)
  result,trace=self.run_fixture('one;invalid','selected');self.assertNotEqual(0,result.returncode);self.assertEqual('',trace)

if __name__=='__main__':unittest.main()
